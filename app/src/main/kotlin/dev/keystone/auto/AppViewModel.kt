package dev.keystone.auto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadb.AdbKeyPair
import dev.keystone.auto.core.*
import dev.keystone.auto.data.Feedback
import dev.keystone.auto.data.HistoryEntry
import dev.keystone.auto.data.Learning
import dev.keystone.auto.data.Profile
import dev.keystone.auto.link.*
import dev.keystone.auto.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs the whole flow: find and connect to the projector, take guided photos, work out and apply the keystone,
 * ask the user whether it is right, and learn from the answer. The UI only observes the flows and calls the functions.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val assets = AndroidAssets(app)
    private val io = Dispatchers.IO
    private val lock = Mutex()                       // serialises everything that talks to the projector

    // ---- observable state ---------------------------------------------------------------------------------
    private val _screen = MutableStateFlow<Screen>(Screen.Connect)
    val screen: StateFlow<Screen> = _screen

    val connect = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val settings = MutableStateFlow(store.loadSettings())
    val profile = MutableStateFlow(store.loadProfile())
    val session = MutableStateFlow<Session?>(null)
    val processing = MutableStateFlow(false)         // a photo is being analysed
    val work = MutableStateFlow(Work("", 0f))
    val verify = MutableStateFlow<VerifyState?>(null)
    val done = MutableStateFlow<DoneState?>(null)
    val notice = MutableStateFlow<String?>(null)     // a message to show on the next screen (errors, tips)
    val busy = MutableStateFlow<String?>(null)       // blocking overlay text
    val hasOriginal = MutableStateFlow(false)
    val resultMode = MutableStateFlow(false)         // the camera is taking the optional result photo

    // ---- private session state ----------------------------------------------------------------------------
    private var conn: AdbConnection? = null
    private var projector: Projector? = null
    private var projectorId = ""
    private var sessionProfile: Profile = profile.value        // parameters adjusted by feedback within this session
    private var lastFeedback: Feedback? = null
    private var feedbackScale = 1.0
    private var nextCaptureId = 1
    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // One-tap path: if we remember a projector, try it straight away.
        if (store.lastHost != null) findProjector()
    }

    // ---- navigation ----------------------------------------------------------------------------------------
    fun go(s: Screen) { _screen.value = s }

    /** Handles the system back button; returns false if the app should let the system close it. */
    fun back(): Boolean {
        when (_screen.value) {
            Screen.Connect -> return false
            Screen.Guide -> go(Screen.Connect)
            Screen.Capture -> if (resultMode.value) { resultMode.value = false; go(Screen.Verify) } else go(Screen.Guide)
            Screen.Working -> Unit
            Screen.Verify -> Unit                                   // the user must say whether it looks right
            Screen.Feedback -> go(Screen.Verify)
            Screen.ResultPhoto -> go(Screen.Verify)
            Screen.Done -> goHome()
            Screen.Settings, Screen.History, Screen.Log -> go(Screen.Connect)
        }
        return true
    }

    // ---- connecting ----------------------------------------------------------------------------------------
    private fun keyPair(): AdbKeyPair? = runCatching { loadOrCreateKeyPair(store.keyDir()) }.getOrElse { AppLog.e("no ADB key pair, continuing without", it); null }

    fun findProjector() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(io) { doFind() }
    }

    private suspend fun doFind() {
        notice.value = null
        connect.value = ConnectState.Scanning(0, 1)
        val net = Network.local(getApplication())
        AppLog.d("scan: network=$net")
        if (net == null) {
            connect.value = ConnectState.Failed("Your phone isn't on Wi-Fi.", "Connect it to the same Wi-Fi network as the projector, then try again.")
            return
        }
        // the projector we used last time, if it is still there
        store.lastHost?.let { h ->
            if (AdbScanner.portOpen(h, AdbConnection.DEFAULT_PORT, 900)) {
                AppLog.d("scan: remembered projector $h answers")
                connectTo(h)
                if (connect.value is ConnectState.Connected) return
            }
        }
        val hosts = Subnet.hosts(net.ip, net.prefixLength)
        val open = AdbScanner.scan(hosts, onProgress = { d, t -> connect.value = ConnectState.Scanning(d, t) })
        AppLog.d("scan: ${hosts.size} hosts, ADB open on $open")
        if (open.isEmpty()) {
            connect.value = ConnectState.Failed(
                "No projector found on this Wi-Fi.",
                "Switch the projector on, make sure it is on the same Wi-Fi as this phone and that network debugging (ADB) is enabled, then scan again. You can also enter its IP address.",
            )
            return
        }
        val choices = open.map { identify(it) }
        val usable = choices.filter { it.supported }
        if (usable.size == 1) connectTo(usable[0].host) else connect.value = ConnectState.Choose(choices)
    }

    private fun identify(host: String): DeviceChoice = try {
        AdbConnection.connect(host, keyPair = keyPair(), connectTimeoutMs = 2500, socketTimeoutMs = 8000).use { c ->
            val info = Projector(c, assets, host).info()
            DeviceChoice(host, info.displayName.ifBlank { host }, info.supported, info.unsupportedReason)
        }
    } catch (e: Exception) {
        AppLog.e("identify $host", e)
        DeviceChoice(host, host, false, "Could not talk to this device. If it asks to allow debugging, accept on its screen and scan again.")
    }

    fun connectManually(host: String) {
        val h = host.trim()
        if (h.isEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch(io) { connectTo(h) }
    }

    fun connectToChoice(host: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(io) { connectTo(host) }
    }

    private suspend fun connectTo(host: String) = lock.withLock {
        connect.value = ConnectState.Connecting(host, "Connecting…")
        try {
            closeLocked()
            val c = AdbConnection.connect(host, keyPair = keyPair())
            val p = Projector(c, assets, host)
            connect.value = ConnectState.Connecting(host, "Checking the projector…")
            val info = p.info()
            AppLog.d("connected: $info")
            if (!info.supported) {
                c.close()
                connect.value = ConnectState.Failed(info.unsupportedReason ?: "This projector isn't supported.", "AutoKeystone works with projectors that use the Hisilicon TV API (Newlink/Zhiying, Hi3751).")
                return@withLock
            }
            connect.value = ConnectState.Connecting(host, "Preparing the projector…")
            p.ensureHelper { b, path -> c.push(b, path) }
            if (p.ensurePatternApp { b, path -> c.push(b, path) }) AppLog.d("installed the test pattern app")
            val ks = p.readKeystone()
            projectorId = p.stableId()
            store.saveOriginalIfMissing(projectorId, ks)
            hasOriginal.value = store.originalKeystone(projectorId) != null
            store.lastHost = host
            conn = c
            projector = p
            connect.value = ConnectState.Connected(info, ks)
        } catch (e: Exception) {
            AppLog.e("connect to $host failed", e)
            connect.value = ConnectState.Failed(friendly(e), "Check that the projector is on, on the same Wi-Fi, and that network debugging is enabled.")
        }
    }

    private fun closeLocked() {
        conn?.close()
        conn = null
        projector = null
    }

    fun disconnect() {
        viewModelScope.launch(io) { lock.withLock { closeLocked() } }
        connect.value = ConnectState.Idle
    }

    /** Runs a projector call, reconnecting once if the connection dropped (the projector may have slept). */
    private suspend fun <T> proj(block: (Projector) -> T): T = lock.withLock {
        val p = projector ?: throw ProjectorException("Not connected to a projector.")
        try {
            block(p)
        } catch (e: Exception) {
            AppLog.e("projector call failed, reconnecting once", e)
            val host = p.host
            conn?.close()
            val c = AdbConnection.connect(host, keyPair = keyPair())
            val np = Projector(c, assets, host)
            conn = c
            projector = np
            block(np)
        }
    }

    private fun friendly(e: Exception): String {
        val m = (e.message ?: e.javaClass.simpleName)
        return when {
            m.contains("refused", true) -> "The projector refused the connection. Network debugging (ADB) may be switched off."
            m.contains("timed out", true) || m.contains("timeout", true) -> "The projector didn't answer in time."
            m.contains("unreachable", true) || m.contains("No route", true) -> "The projector can't be reached on this network."
            else -> m
        }
    }

    // ---- capture session -----------------------------------------------------------------------------------
    fun startCapture() {
        viewModelScope.launch(io) {
            busy.value = "Showing the test pattern on the projector…"
            try {
                val ks = withContext(io) { proj { it.readKeystone() } }
                withContext(io) { proj { it.showPattern() } }
                session.value = Session(ks, settings.value.captureCount)
                sessionProfile = profile.value
                lastFeedback = null
                feedbackScale = 1.0
                verify.value = null
                resultMode.value = false
                notice.value = null
                go(Screen.Capture)
            } catch (e: Exception) {
                AppLog.e("start capture", e)
                notice.value = "Couldn't start: ${friendly(e)}"
                connect.value = ConnectState.Failed(friendly(e), "Reconnect to the projector and try again.")
                go(Screen.Connect)
            } finally {
                busy.value = null
            }
        }
    }

    fun showPatternAgain() {
        viewModelScope.launch(io) {
            try { proj { it.showPattern() } } catch (e: Exception) { notice.value = "Couldn't show the pattern: ${friendly(e)}" }
        }
    }

    /** A photo has been taken: analyse it, and when enough good ones exist, solve and apply. */
    fun onPhoto(file: File, gravity: Vec3, meta: CameraMeta) {
        viewModelScope.launch(io) {
            processing.value = true
            try {
                val s = session.value ?: return@launch
                val isResult = resultMode.value
                val keystone = if (isResult) verify.value?.applied ?: s.keystoneAtStart else s.keystoneAtStart
                val out = CaptureProcessor.process(file, gravity, meta, keystone)
                AppLog.d("photo: usable=${out.usable} elev=${"%.1f".format(out.elevationDeg)} roll=${"%.1f".format(out.rollDeg)} problem=${out.problem} warning=${out.warning}")
                if (isResult) {
                    resultMode.value = false
                    finishAccepted(out.measurement)
                    return@launch
                }
                val item = CaptureItem(nextCaptureId++, out.measurement, out.thumb, out.elevationDeg, out.rollDeg, out.problem, out.warning)
                val updated = s.copy(items = s.items + item)
                session.value = updated
                if (updated.usableCount >= updated.target) solveAndApply(isRetry = false)
            } catch (e: Exception) {
                AppLog.e("photo processing", e)
                notice.value = "Couldn't process that photo: ${e.message}"
            } finally {
                file.delete()
                processing.value = false
            }
        }
    }

    fun removeCapture(id: Int) {
        val s = session.value ?: return
        session.value = s.copy(items = s.items.filter { it.id != id })
    }

    fun clearCaptures() {
        session.value = session.value?.copy(items = emptyList())
    }

    // ---- solving and applying ------------------------------------------------------------------------------
    private fun failToCapture(message: String) {
        notice.value = message
        work.value = Work("", 0f)
        go(if (session.value != null) Screen.Capture else Screen.Connect)
    }

    private suspend fun solveAndApply(isRetry: Boolean) {
        go(Screen.Working)
        work.value = Work("Working out the correction…", 0.15f)
        val s = session.value ?: return failToCapture("The session was lost. Please start again.")
        val ms = s.items.mapNotNull { it.measurement }
        val params = sessionProfile.params
        AppLog.d("solve: ${ms.size} captures, params=$params")
        val solutions = ms.mapNotNull { solveCapture(it, params)?.offsets }
        if (solutions.isEmpty()) return failToCapture("Couldn't work out a correction from these photos. Retake them with the whole picture in view.")
        val combined = combine(solutions)
        val o = combined.offsets
        if (!o.withinLimits() || o.panelUse() < 0.45 || !isConvex(o.corners())) {
            AppLog.e("solve: implausible result ${o.toMap()} panelUse=${o.panelUse()}")
            return failToCapture("The result looked wrong (${(o.panelUse() * 100).toInt()}% of the panel). Retake the photos, standing square to the wall.")
        }
        try {
            work.value = Work("Checking the projector…", 0.45f)
            val now = proj { it.readKeystone() }
            if (!isRetry && now.toList() != s.keystoneAtStart.toList()) {
                return failToCapture("The projector's keystone changed while you were taking photos. Please start again.")
            }
            work.value = Work("Applying to the projector…", 0.7f)
            val before = verify.value?.before ?: now
            val applied = proj { it.applyKeystone(o) }
            work.value = Work("Done", 1f)
            proj { it.showPattern() }
            val round = (verify.value?.round ?: 0) + 1
            verify.value = VerifyState(before, applied, combined, params, round, lastFeedback?.name)
            AppLog.d("applied ${applied.toMap()} (round $round, spread ${"%.1f".format(combined.spread)} px, dropped ${combined.dropped})")
            go(Screen.Verify)
        } catch (e: Exception) {
            AppLog.e("apply", e)
            failToCapture("Couldn't apply the keystone: ${friendly(e)}")
        }
    }

    // ---- verdict -------------------------------------------------------------------------------------------
    fun looksCorrect() = go(Screen.ResultPhoto)

    fun notRight() = go(Screen.Feedback)

    fun skipResultPhoto() {
        viewModelScope.launch(io) { finishAccepted(null) }
    }

    fun startResultPhoto() {
        resultMode.value = true
        go(Screen.Capture)
    }

    private suspend fun finishAccepted(result: CaptureMeasurement?) {
        val v = verify.value ?: return
        val s = session.value ?: return
        val captures = s.items.mapNotNull { it.measurement } + listOfNotNull(result)
        val learned = Learning.learnFromAccepted(sessionProfile, captures, v.applied)
        AppLog.d("learned from accepted: ${learned.params} (${captures.size} captures, result photo=${result != null})")
        profile.value = learned
        store.saveProfile(learned)
        store.addHistory(HistoryEntry(System.currentTimeMillis(), projectorId, v.before, v.applied, v.params, captures.size, v.combined.spread, "accepted", if (result != null) "with result photo" else ""))
        done.value = DoneState(v.before, v.applied, learned.params, result != null, learned.accepted)
        go(Screen.Done)
    }

    /** "Not right yet": nudge the parameter behind the complaint, re-solve from the same photos and apply again. */
    fun reject(fb: Feedback) {
        viewModelScope.launch(io) {
            val v = verify.value ?: return@launch
            val s = session.value ?: return@launch
            val ms = s.items.mapNotNull { it.measurement }
            val previous = lastFeedback
            if (previous != null && opposite(previous) == fb) feedbackScale = maxOf(0.25, feedbackScale / 2)   // reversing: take a smaller step
            lastFeedback = fb
            sessionProfile = Learning.adjust(sessionProfile, ms, v.applied, fb, feedbackScale)
            AppLog.d("feedback $fb (scale $feedbackScale) -> ${sessionProfile.params}")
            store.addHistory(HistoryEntry(System.currentTimeMillis(), projectorId, v.before, v.applied, v.params, ms.size, v.combined.spread, "rejected:${fb.name}"))
            solveAndApply(isRetry = true)
        }
    }

    private fun opposite(f: Feedback) = when (f) {
        Feedback.RIGHT_EDGE_SHORT -> Feedback.LEFT_EDGE_SHORT
        Feedback.LEFT_EDGE_SHORT -> Feedback.RIGHT_EDGE_SHORT
        Feedback.RIGHT_SIDE_HIGH -> Feedback.LEFT_SIDE_HIGH
        Feedback.LEFT_SIDE_HIGH -> Feedback.RIGHT_SIDE_HIGH
        Feedback.TOO_WIDE -> Feedback.TOO_TALL
        Feedback.TOO_TALL -> Feedback.TOO_WIDE
    }

    fun retakePhotos() {
        verify.value = verify.value?.copy(round = verify.value?.round ?: 0)
        session.value = session.value?.copy(items = emptyList())
        lastFeedback = null
        feedbackScale = 1.0
        notice.value = "Let's take the photos again."
        go(Screen.Capture)
    }

    /** Put back the keystone the projector had before this session. */
    fun undo() {
        viewModelScope.launch(io) {
            val v = verify.value ?: return@launch
            busy.value = "Restoring the previous keystone…"
            try {
                proj { it.applyKeystone(v.before) }
                store.addHistory(HistoryEntry(System.currentTimeMillis(), projectorId, v.applied, v.before, v.params, session.value?.usableCount ?: 0, v.combined.spread, "undone"))
                notice.value = "Restored the previous keystone."
                verify.value = null
                session.value = null
                refreshConnected()
                go(Screen.Connect)
            } catch (e: Exception) {
                notice.value = "Couldn't restore it: ${friendly(e)}"
            } finally {
                busy.value = null
            }
        }
    }

    /** Back to the projector's normal screen and to the start of the app. */
    fun goHome() {
        viewModelScope.launch(io) {
            try { proj { it.showHome() } } catch (_: Exception) { }
            session.value = null
            verify.value = null
            done.value = null
            refreshConnected()
            go(Screen.Connect)
        }
    }

    private suspend fun refreshConnected() {
        val c = connect.value
        if (c is ConnectState.Connected) {
            try { connect.value = c.copy(keystone = proj { it.readKeystone() }) } catch (_: Exception) { }
        }
    }

    // ---- settings and data ---------------------------------------------------------------------------------
    fun updateSettings(s: Settings) {
        settings.value = s
        store.saveSettings(s)
    }

    fun resetLearning() {
        store.resetProfile()
        profile.value = Profile()
        sessionProfile = Profile()
        notice.value = "The learned preferences were reset."
    }

    fun history(): List<HistoryEntry> = store.loadHistory().reversed()

    fun clearHistory() = store.clearHistory()

    /** Restore the keystone the projector had the first time this app met it. */
    fun restoreOriginal() {
        viewModelScope.launch(io) {
            val orig = store.originalKeystone(projectorId)
            if (orig == null) { notice.value = "No original keystone is saved for this projector."; return@launch }
            busy.value = "Restoring the original keystone…"
            try {
                proj { it.applyKeystone(orig) }
                store.addHistory(HistoryEntry(System.currentTimeMillis(), projectorId, orig, orig, profile.value.params, 0, 0.0, "restored-original"))
                notice.value = "Restored the original keystone."
                refreshConnected()
            } catch (e: Exception) {
                notice.value = "Couldn't restore it: ${friendly(e)}"
            } finally {
                busy.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { conn?.close() } catch (_: Exception) { }
    }
}
