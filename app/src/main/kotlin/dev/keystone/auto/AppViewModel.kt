package dev.keystone.auto

import android.app.Application
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadb.AdbKeyPair
import dev.keystone.auto.core.*
import dev.keystone.auto.data.Feedback
import dev.keystone.auto.data.HistoryEntry
import dev.keystone.auto.data.Learning
import dev.keystone.auto.data.Profile
import dev.keystone.auto.data.SavedKeystone
import dev.keystone.auto.data.SavedKeystones
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
    val toast = MutableStateFlow<String?>(null)      // a short message shown over any screen
    val remoteState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val apps = MutableStateFlow<AppsState>(AppsState.Loading)
    val install = MutableStateFlow<InstallState>(InstallState.Idle)
    val device = MutableStateFlow(DeviceState(null, null))
    val saved = MutableStateFlow<List<SavedKeystone>>(emptyList())   // named keystones for the connected projector
    val sleepLeft = MutableStateFlow<Int?>(null)     // seconds until the projector's screen turns off
    val pinned = MutableStateFlow(store.pinnedApps)   // packages shown as one-tap shortcuts on Home
    val link = MutableStateFlow(LinkState.Online)    // is the projector answering? (the watchdog keeps this current)

    // ---- private session state ----------------------------------------------------------------------------
    private var conn: AdbConnection? = null
    private var projector: Projector? = null
    private var projectorId = ""
    private var sessionProfile: Profile = profile.value        // parameters adjusted by feedback within this session
    private var lastFeedback: Feedback? = null
    private var feedbackScale = 1.0
    private var nextCaptureId = 1
    private var scanJob: kotlinx.coroutines.Job? = null
    private var remote: RemoteInput? = null
    private var remoteIdleJob: kotlinx.coroutines.Job? = null
    private var pendingShare: String? = null
    private var watchdog: kotlinx.coroutines.Job? = null

    init {
        // One-tap path: if we remember a projector, try it straight away.
        if (store.lastHost != null) findProjector()
    }

    // ---- navigation ----------------------------------------------------------------------------------------
    fun go(s: Screen) {
        _screen.value = s
        if (s != Screen.Mirror && mirror.value != MirrorState.Idle) stopMirror()
        // the virtual mouse/keyboard stays on the projector while the remote screens are open, then goes away a little later
        remoteIdleJob?.cancel()
        if (s != Screen.Remote && s != Screen.Keyboard && remote != null) {
            remoteIdleJob = viewModelScope.launch { kotlinx.coroutines.delay(REMOTE_IDLE_MS); closeRemote() }
        }
    }

    private fun connected() = connect.value is ConnectState.Connected
    private fun homeOrConnect() = if (connected()) Screen.Home else Screen.Connect

    /** Handles the system back button; returns false if the app should let the system close it. */
    fun back(): Boolean {
        when (_screen.value) {
            Screen.Connect, Screen.Home -> return false
            Screen.Guide, Screen.Remote, Screen.Keyboard, Screen.Apps, Screen.Device, Screen.Mirror -> go(Screen.Home)
            Screen.Install -> if (install.value !is InstallState.Working) go(Screen.Apps)
            Screen.Capture -> if (resultMode.value) { resultMode.value = false; go(Screen.Verify) } else go(Screen.Guide)
            Screen.Working -> Unit
            Screen.Verify -> Unit                                   // the user must say whether it looks right
            Screen.Feedback -> go(Screen.Verify)
            Screen.ResultPhoto -> go(Screen.Verify)
            Screen.Done -> goHome()
            Screen.Settings, Screen.History, Screen.Log -> go(homeOrConnect())
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
        when {
            usable.size == 1 -> connectTo(usable[0].host)
            usable.isEmpty() && choices.size == 1 -> connectTo(choices[0].host)     // the only device: use it as a plain remote
            else -> connect.value = ConnectState.Choose(choices)
        }
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

    fun cancelScan() {
        scanJob?.cancel()
        connect.value = ConnectState.Idle
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
            // Not a Hisilicon projector: keep the remote, apps, links, files and power, but no keystone.
            val limited = !info.supported
            if (limited) AppLog.d("limited mode: ${info.unsupportedReason}")
            connect.value = ConnectState.Connecting(host, "Preparing the projector…")
            var ks = Offsets.fromList(List(8) { 0.0 })
            var autoOn = false
            if (!limited) {
                p.ensureHelper { b, path -> c.push(b, path) }
                if (p.ensurePatternApp { b, path -> c.push(b, path) }) AppLog.d("installed the test pattern app")
                // Read only: we never change the firmware's Auto keystone setting. While it is on the getters read 0,
                // so don't remember that as the "original keystone".
                ks = p.readKeystone()
                autoOn = p.autoWasOn
                if (autoOn) AppLog.d("firmware Auto keystone is on (left as it is)")
            }
            projectorId = p.stableId()
            if (!limited && !autoOn) store.saveOriginalIfMissing(projectorId, ks)
            hasOriginal.value = store.originalKeystone(projectorId) != null
            saved.value = store.savedKeystones(projectorId)
            store.lastHost = host
            conn = c
            projector = p
            connect.value = ConnectState.Connected(info, ks)
            link.value = LinkState.Online
            loadApps()                                          // so pinned apps are ready on Home
            runCatching { p.tools.wake() }                      // a sleeping projector would ignore everything else
            startWatchdog()
            if (_screen.value == Screen.Connect) go(Screen.Home)
            if (limited) toast.value = "Connected. This projector has no Hisilicon keystone, so you get the remote, apps and files."
            else if (autoOn) toast.value = "The projector's own Auto keystone is on, so keystone changes will be refused. Turn it off in the projector's settings."
            pendingShare?.let { pendingShare = null; openLink(it) }
        } catch (e: Exception) {
            AppLog.e("connect to $host failed", e)
            connect.value = ConnectState.Failed(friendly(e), "Check that the projector is on, on the same Wi-Fi, and that network debugging is enabled.")
        }
    }

    private fun closeLocked() {
        stopMirror()
        closeRemote()
        conn?.close()
        conn = null
        projector = null
    }

    fun disconnect() {
        watchdog?.cancel()
        viewModelScope.launch(io) { lock.withLock { closeLocked() } }
        connect.value = ConnectState.Idle
        go(Screen.Connect)
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

    // ---- keeping the link alive -----------------------------------------------------------------------------
    /** Pings the projector now and then; when it stops answering, reconnects (rescanning the Wi-Fi if its IP changed). */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = viewModelScope.launch(io) {
            var failures = 0
            while (true) {
                kotlinx.coroutines.delay(if (failures == 0) PING_MS else Backoff.delayMs(failures))
                if (!connected()) return@launch
                val alive = if (lock.tryLock()) {
                    try { runCatching { conn?.run("echo ok")?.out?.contains("ok") == true }.getOrDefault(false) } finally { lock.unlock() }
                } else true                                              // busy with a real call: it is clearly alive
                if (alive) {
                    if (failures > 0) { AppLog.d("watchdog: link is back"); link.value = LinkState.Online; resumeRemote() }
                    failures = 0
                    continue
                }
                failures++
                link.value = LinkState.Reconnecting
                AppLog.d("watchdog: projector not answering (failure $failures)")
                if (recoverLink(failures)) { failures = 0; link.value = LinkState.Online; resumeRemote() }
            }
        }
    }

    private fun resumeRemote() {
        if (_screen.value == Screen.Remote || _screen.value == Screen.Keyboard) startRemote()
    }

    /** One attempt to get the projector back: same address first, then (every few failures) a scan for a changed one. */
    private suspend fun recoverLink(failures: Int): Boolean = lock.withLock {
        val p = projector ?: return false
        var host: String? = if (AdbScanner.portOpen(p.host, AdbConnection.DEFAULT_PORT, 1200)) p.host else null
        if (host == null && Backoff.shouldRescan(failures)) {
            val net = Network.local(getApplication())
            if (net != null) {
                for (h in AdbScanner.scan(Subnet.hosts(net.ip, net.prefixLength))) {
                    val same = runCatching { AdbConnection.connect(h, keyPair = keyPair(), connectTimeoutMs = 2500, socketTimeoutMs = 8000).use { Projector(it, assets, h).stableId() == projectorId } }.getOrDefault(false)
                    if (same) { host = h; break }
                }
            }
        }
        if (host == null) return false
        try {
            closeRemote(keepState = true)
            conn?.close()
            val c = AdbConnection.connect(host, keyPair = keyPair())
            conn = c
            projector = Projector(c, assets, host)
            if (host != p.host) { store.lastHost = host; AppLog.d("projector moved from ${p.host} to $host"); toast.value = "The projector's address changed; reconnected." }
            true
        } catch (e: Exception) {
            AppLog.e("recover link", e)
            false
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
                    if (out.measurement == null) {
                        notice.value = (out.problem ?: "Couldn't read that photo.") + " Try again, or skip the result photo."
                        return@launch
                    }
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
        // The projector now holds the keystone we applied, so the new photos are measured against that one.
        val applied = verify.value?.applied
        session.value = session.value?.let { it.copy(keystoneAtStart = applied ?: it.keystoneAtStart, items = emptyList()) }
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
                go(Screen.Home)
            } catch (e: Exception) {
                notice.value = "Couldn't restore it: ${friendly(e)}"
            } finally {
                busy.value = null
            }
        }
    }

    /** Show the projector's normal screen for a moment so the user can judge the picture with real content. */
    fun peekHome() {
        viewModelScope.launch(io) { try { proj { it.showHome() } } catch (_: Exception) { } }
    }

    /** Forget the remembered projector (the next start scans again). */
    fun forgetProjector() {
        store.lastHost = null
        disconnect()
        notice.value = "Forgot the saved projector."
    }

    /** Back to the projector's normal screen and to the start of the app. */
    fun goHome() {
        viewModelScope.launch(io) {
            try { proj { it.showHome() } } catch (_: Exception) { }
            session.value = null
            verify.value = null
            done.value = null
            refreshConnected()
            go(Screen.Home)
        }
    }

    private suspend fun refreshConnected() {
        val c = connect.value
        if (c is ConnectState.Connected && c.info.supported) {
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

    /** Saves what is on the projector right now under a name ("Bedroom ceiling"). */
    fun saveCurrentKeystone(name: String) {
        viewModelScope.launch(io) {
            try {
                val now = proj { it.readKeystone() }
                val n = name.trim().ifBlank { SavedKeystones.defaultName(saved.value) }.take(30)
                val list = SavedKeystones.put(saved.value, SavedKeystone(n, now, System.currentTimeMillis()))
                store.writeSavedKeystones(projectorId, list)
                saved.value = list
                toast.value = "Saved “$n”"
            } catch (e: Exception) { fail("Couldn't save the keystone", e) }
        }
    }

    fun applySavedKeystone(item: SavedKeystone) {
        viewModelScope.launch(io) {
            busy.value = "Applying “${item.name}”…"
            try {
                val before = proj { it.readKeystone() }
                proj { it.applyKeystone(item.offsets) }
                store.addHistory(HistoryEntry(System.currentTimeMillis(), projectorId, before, item.offsets, profile.value.params, 0, 0.0, "saved:${item.name}"))
                toast.value = "Applied “${item.name}”"
                refreshConnected()
            } catch (e: Exception) { fail("Couldn't apply it", e) } finally { busy.value = null }
        }
    }

    fun deleteSavedKeystone(item: SavedKeystone) {
        val list = SavedKeystones.remove(saved.value, item.name)
        store.writeSavedKeystones(projectorId, list)
        saved.value = list
    }

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

    // ---- remote: the phone as mouse, keyboard and remote -----------------------------------------------------
    fun startRemote() {
        if (remote?.isOpen == true) { remoteState.value = RemoteState.Ready; return }
        if (remoteState.value == RemoteState.Starting) return
        remoteState.value = RemoteState.Starting
        viewModelScope.launch(io) {
            try {
                val c = conn ?: throw ProjectorException("Not connected to a projector.")
                closeRemote(keepState = true)
                val r = RemoteInput(c)
                r.start()
                remote = r
                remoteState.value = RemoteState.Ready
                AppLog.d("remote: virtual mouse and keyboard ready")
            } catch (e: Exception) {
                AppLog.e("remote start", e)
                remoteState.value = RemoteState.Failed(friendly(e))
            }
        }
    }

    private fun closeRemote(keepState: Boolean = false) {
        remote?.close()
        remote = null
        if (!keepState) remoteState.value = RemoteState.Idle
    }

    /** The live input, or null (and the screen shows the reconnect prompt) when the link has dropped. */
    private fun live(): RemoteInput? {
        val r = remote
        if (r != null && r.isOpen) return r
        if (remoteState.value == RemoteState.Ready) remoteState.value = RemoteState.Failed("The connection to the projector dropped.")
        return null
    }

    fun pointerMove(dx: Double, dy: Double) { live()?.move(dx, dy) }
    fun scrollBy(notches: Double) { live()?.scroll(notches) }
    fun click(button: Int = Evdev.BTN_LEFT) { live()?.click(button) }
    fun buttonDown() { live()?.buttonDown() }
    fun buttonUp() { live()?.buttonUp() }
    fun press(key: Int) { live()?.key(key) }

    /** Types on the projector; tells the user about characters a US keyboard can't produce. */
    fun typeText(text: String) {
        val skipped = live()?.type(text) ?: return
        if (skipped.isNotEmpty()) toast.value = "Couldn't type: ${skipped.distinct().joinToString(" ")} (only English letters, digits and symbols are supported)"
    }

    // ---- projector tools: apps, links, power, status -----------------------------------------------------------
    private fun fail(what: String, e: Exception) {
        AppLog.e(what, e)
        toast.value = "$what: ${friendly(e)}"
    }

    fun loadApps() {
        apps.value = AppsState.Loading
        viewModelScope.launch(io) {
            try { apps.value = AppsState.Loaded(proj { it.tools.listApps() }) }
            catch (e: Exception) { AppLog.e("list apps", e); apps.value = AppsState.Failed(friendly(e)) }
        }
    }

    fun togglePin(app: ProjectorApp) {
        val now = if (app.packageName in pinned.value) pinned.value - app.packageName else pinned.value + app.packageName
        pinned.value = now
        store.pinnedApps = now
        toast.value = if (app.packageName in now) "${app.label} pinned to Home" else "${app.label} unpinned"
    }

    fun launchApp(app: ProjectorApp) {
        viewModelScope.launch(io) {
            try {
                // memory guard: on a 1 GB projector, clear the background first when memory is low
                val freed = proj { p -> if (ProjectorTools.isLowOnMemory(p.tools.status())) { p.tools.freeMemory(); true } else false }
                toast.value = if (proj { it.tools.launch(app) }) (if (freed) "Freed memory, opening ${app.label}…" else "Opening ${app.label}…") else "${app.label} didn't open"
            }
            catch (e: Exception) { fail("Couldn't open ${app.label}", e) }
        }
    }

    fun stopApp(app: ProjectorApp) {
        viewModelScope.launch(io) {
            try { proj { it.tools.forceStop(app.packageName) }; toast.value = "${app.label} stopped" } catch (e: Exception) { fail("Couldn't stop ${app.label}", e) }
        }
    }

    fun uninstallApp(app: ProjectorApp) {
        viewModelScope.launch(io) {
            try {
                val ok = proj { it.tools.uninstall(app.packageName) }
                toast.value = if (ok) "${app.label} removed" else "${app.label} couldn't be removed"
                if (ok) loadApps()
            } catch (e: Exception) { fail("Couldn't remove ${app.label}", e) }
        }
    }

    /** Opens a link (or a shared YouTube address) on the projector. */
    fun openLink(text: String) {
        if (!connected()) { pendingShare = text; toast.value = "Connect to the projector and the link will open."; return }
        viewModelScope.launch(io) {
            try { toast.value = if (proj { it.tools.openUrl(text) }) "Opened on the projector" else "That doesn't look like a web link" }
            catch (e: Exception) { fail("Couldn't open the link", e) }
        }
    }

    fun searchYoutube(query: String) {
        viewModelScope.launch(io) {
            try { toast.value = if (proj { it.tools.searchYoutube(query) }) "Searching YouTube for “$query”" else "YouTube didn't open" }
            catch (e: Exception) { fail("Couldn't search", e) }
        }
    }

    /** A video, photo or song shared to the app: copy it to the projector and play it there. */
    fun sendFile(uri: android.net.Uri, mime: String?) {
        if (!connected()) { toast.value = "Connect to the projector first, then share the file again."; return }
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"
        viewModelScope.launch(io) {
            try {
                val source = object : InstallSource {
                    override val name = name
                    override fun open() = resolver.openInputStream(uri) ?: throw ProjectorException("Couldn't read that file.")
                }
                val type = mime ?: resolver.getType(uri)
                val r = proj { it.tools.sendAndOpen(source, type) { step -> busy.value = step } }
                toast.value = r.message
            } catch (e: Exception) { fail("Couldn't send the file", e) } finally { busy.value = null }
        }
    }

    /** The user chose a file in the system picker: send it to the projector and install it. */
    fun installFrom(uri: android.net.Uri) {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "app.apk"
        go(Screen.Install)
        if (!Regex(".*\\.(apk|apks|apkm|xapk)$", RegexOption.IGNORE_CASE).matches(name)) {
            install.value = InstallState.Done(name, false, "That doesn't look like an app file. Choose an .apk (or an .apkm / .apks / .xapk bundle).")
            return
        }
        install.value = InstallState.Working(name, "Preparing…")
        viewModelScope.launch(io) {
            try {
                val source = object : InstallSource {
                    override val name = name
                    override fun open() = resolver.openInputStream(uri) ?: throw ProjectorException("Couldn't read that file.")
                }
                val r = proj { it.tools.install(source) { step -> install.value = InstallState.Working(name, step) } }
                AppLog.d("install $name: ${r.ok} ${r.message}")
                install.value = InstallState.Done(name, r.ok, r.message)
                if (r.ok) loadApps()
            } catch (e: Exception) {
                AppLog.e("install", e)
                install.value = InstallState.Done(name, false, friendly(e))
            }
        }
    }

    fun refreshDevice() {
        device.value = device.value.copy(loading = true)
        viewModelScope.launch(io) {
            try {
                val st = proj { it.tools.status() }
                val auto = if ((connect.value as? ConnectState.Connected)?.info?.supported == false) null else proj { it.firmwareAutoOn() }
                device.value = DeviceState(st, auto, customLauncher = proj { it.tools.hasPackage(ProjectorTools.CUSTOM_LAUNCHER) })
            } catch (e: Exception) {
                AppLog.e("device status", e)
                device.value = device.value.copy(loading = false)
                toast.value = "Couldn't read the projector: ${friendly(e)}"
            }
        }
    }

    fun freeMemory() {
        viewModelScope.launch(io) {
            try { proj { it.tools.freeMemory() }; toast.value = "Background apps stopped"; refreshDevice() } catch (e: Exception) { fail("Couldn't free memory", e) }
        }
    }

    enum class Power { Sleep, Wake, Reboot }

    fun power(p: Power) {
        viewModelScope.launch(io) {
            try {
                when (p) {
                    Power.Sleep -> proj { it.tools.sleep() }
                    Power.Wake -> proj { it.tools.wake() }
                    Power.Reboot -> proj { it.tools.reboot() }
                }
                toast.value = when (p) { Power.Sleep -> "Projector screen off"; Power.Wake -> "Projector awake"; Power.Reboot -> "Restarting the projector…" }
                if (p == Power.Reboot) disconnect()
            } catch (e: Exception) { fail("Couldn't do that", e) }
        }
    }

    /** Gathers a shareable diagnostics report and puts it on the phone's clipboard. */
    fun copyDiagnostics() {
        viewModelScope.launch(io) {
            try {
                val c = connect.value as? ConnectState.Connected ?: throw ProjectorException("Not connected to a projector.")
                val t0 = System.nanoTime()
                proj { it.shell.run("echo ok") }
                val ping = (System.nanoTime() - t0) / 1_000_000
                val st = runCatching { proj { it.tools.status() } }.getOrNull()
                val temp = runCatching { proj { it.tools.temperatureC() } }.getOrNull()
                val auto = runCatching { proj { it.firmwareAutoOn() } }.getOrNull()
                val app = getApplication<Application>()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
                val text = Diagnostics.report(DiagnosticsInput(c.info, st, ping, temp, auto, link.value, version, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
                val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("ProjectorConnect report", text))
                toast.value = "Report copied. Paste it wherever you need it."
            } catch (e: Exception) { fail("Couldn't build the report", e) }
        }
    }

    // ---- live screen: the projector's screen on the phone, with touch ---------------------------------------------
    val mirror = MutableStateFlow<MirrorState>(MirrorState.Idle)
    private var screenMirror: ScreenMirror? = null
    private var touch: TouchInput? = null
    @Volatile private var mirrorSink: ((ByteArray, Boolean) -> Unit)? = null
    val mirrorSharp = MutableStateFlow(store.mirrorSharp)

    private fun newScreenMirror(c: Shell) = ScreenMirror(
        c, if (mirrorSharp.value) MirrorQuality.Sharp else MirrorQuality.Smooth,
        onUnit = { u, k -> mirrorSink?.invoke(u, k) }, onProblem = { m -> mirror.value = MirrorState.Failed(m) },
    ).also { it.start() }

    /** Switches between the light and the sharp stream; the stream restarts, the touch screen stays. */
    fun setMirrorSharp(sharp: Boolean) {
        if (mirrorSharp.value == sharp) return
        mirrorSharp.value = sharp
        store.mirrorSharp = sharp
        val c = conn ?: return
        if (screenMirror == null) return
        viewModelScope.launch(io) { screenMirror?.close(); screenMirror = newScreenMirror(c) }
    }

    /** Starts streaming the projector's screen into [sink] (the decoder) and opens the virtual touch screen. */
    fun startMirror(sink: (ByteArray, Boolean) -> Unit) {
        if (mirror.value is MirrorState.Starting || mirror.value is MirrorState.Live) { mirrorSink = sink; return }
        mirror.value = MirrorState.Starting
        mirrorSink = sink
        viewModelScope.launch(io) {
            try {
                val c = conn ?: throw ProjectorException("Not connected to a projector.")
                stopMirror(keepState = true)
                // The stream is the picture after the keystone warp, so remember where the corners are to straighten it on the phone.
                val ks = runCatching { proj { it.readKeystone() } }.getOrNull()
                val autoOn = projector?.autoWasOn == true
                val offsets = if (ks == null || autoOn || ks.toList().all { it == 0.0 }) Offsets.fromList(List(8) { 0.0 }) else ks
                val quad = offsets.corners().map { (it.x / Panel.W).toFloat() to (it.y / Panel.H).toFloat() }
                val t = TouchInput(c)
                t.start()
                touch = t
                screenMirror = newScreenMirror(c)
                mirror.value = MirrorState.Live(quad)
                AppLog.d("mirror: streaming, touch screen ready")
            } catch (e: Exception) {
                AppLog.e("mirror start", e)
                stopMirror(keepState = true)
                mirror.value = MirrorState.Failed(friendly(e))
            }
        }
    }

    fun stopMirror(keepState: Boolean = false) {
        mirrorSink = null
        try { touch?.releaseAll() } catch (_: Exception) { }
        touch?.close(); touch = null
        screenMirror?.close(); screenMirror = null
        if (!keepState) mirror.value = MirrorState.Idle
    }

    fun touchDown(slot: Int, x: Int, y: Int) { touch?.touchDown(slot, x, y) }
    fun touchMove(slot: Int, x: Int, y: Int) { touch?.touchMove(slot, x, y) }
    fun touchUp(slot: Int) { touch?.touchUp(slot) }

    /** Undo for the custom launcher: Home goes back to the stock launcher, and the Home-key helper service is removed. */
    fun restoreStockLauncher() {
        viewModelScope.launch(io) {
            busy.value = "Restoring the projector's original home screen…"
            try {
                val home = proj { it.tools.restoreStockHome() }
                val acc = proj { it.tools.restoreStockAccessibility() }
                toast.value = if (home && acc) "Original home screen restored" else "Partly restored (home: ${if (home) "ok" else "failed"}, services: ${if (acc) "ok" else "failed"})"
            } catch (e: Exception) { fail("Couldn't restore it", e) } finally { busy.value = null }
        }
    }

    fun refreshSleepTimer() {
        viewModelScope.launch(io) { try { sleepLeft.value = proj { it.tools.sleepRemainingSeconds() } } catch (_: Exception) { } }
    }

    /** Screen off in [minutes] (null cancels). The countdown runs on the projector. */
    fun setSleepTimer(minutes: Int?) {
        viewModelScope.launch(io) {
            try {
                if (minutes == null) { proj { it.tools.cancelSleep() }; toast.value = "Sleep timer cancelled" }
                else { proj { it.tools.scheduleSleep(minutes) }; toast.value = "Screen turns off in $minutes min" }
                sleepLeft.value = proj { it.tools.sleepRemainingSeconds() }
            } catch (e: Exception) { fail("Couldn't set the timer", e) }
        }
    }

    fun openProjectorSettings(action: String) {
        viewModelScope.launch(io) { try { proj { it.tools.openSettings(action) } } catch (e: Exception) { fail("Couldn't open settings", e) } }
    }

    override fun onCleared() {
        super.onCleared()
        stopMirror()
        closeRemote()
        try { conn?.close() } catch (_: Exception) { }
    }

    private companion object {
        const val REMOTE_IDLE_MS = 45_000L
        const val PING_MS = 8_000L
    }
}
