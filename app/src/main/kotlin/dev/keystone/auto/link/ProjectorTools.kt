package dev.keystone.auto.link

import java.io.InputStream
import java.util.zip.ZipInputStream

/** An app on the projector that can be started. */
data class ProjectorApp(val packageName: String, val component: String, val label: String, val thirdParty: Boolean, val tvApp: Boolean)

data class ProjectorStatus(
    val ramTotalMb: Int, val ramFreeMb: Int,
    val storageTotalMb: Int, val storageFreeMb: Int,
    val model: String, val androidRelease: String, val abi: String,
)

/** What to install: one APK, or a bundle of split APKs (.apks/.apkm/.xapk are zip files of them). */
interface InstallSource {
    val name: String
    fun open(): InputStream
}

class InstallResult(val ok: Boolean, val message: String)

/** Day-to-day control of the projector over ADB: apps, links, power, status. Pure shell commands, easy to fake in tests. */
class ProjectorTools(private val sh: Shell) {

    // ---- apps ----------------------------------------------------------------------------------------------

    fun listApps(): List<ProjectorApp> {
        val tv = launchables("android.intent.category.LEANBACK_LAUNCHER")
        val all = launchables("android.intent.category.LAUNCHER")
        val third = sh.run("pm list packages -3").out.lineSequence().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }.toSet()
        val byPackage = LinkedHashMap<String, Pair<String, Boolean>>()          // package -> (component, tvApp)
        tv.forEach { byPackage[it.substringBefore('/')] = it to true }
        all.forEach { byPackage.putIfAbsent(it.substringBefore('/'), it to false) }
        return byPackage.map { (pkg, v) -> ProjectorApp(pkg, v.first, AppNames.labelFor(pkg), pkg in third, v.second) }
            .filter { it.packageName !in HIDDEN }
            .sortedWith(compareByDescending<ProjectorApp> { it.thirdParty }.thenBy { it.label.lowercase() })
    }

    private fun launchables(category: String): List<String> =
        parseComponents(sh.run("cmd package query-activities --brief -a android.intent.action.MAIN -c $category").out)

    fun launch(app: ProjectorApp): Boolean {
        val r = sh.run("am start -n ${app.component.shellQuote()} 2>&1")
        return !r.out.contains("Error", true) && !r.out.contains("Exception", true)
    }

    fun forceStop(pkg: String) { sh.run("am force-stop ${pkg.shellQuote()}") }

    fun uninstall(pkg: String): Boolean = sh.run("pm uninstall ${pkg.shellQuote()} 2>&1").out.contains("Success")

    // ---- links, text, power --------------------------------------------------------------------------------

    /** Opens a web link on the projector in whatever app handles it (YouTube links go to the YouTube app). */
    fun openUrl(url: String): Boolean {
        val u = normaliseUrl(url) ?: return false
        val r = sh.run("am start -a android.intent.action.VIEW -d ${u.shellQuote()} 2>&1")
        return !r.out.contains("Error", true) && !r.out.contains("unable to resolve", true)
    }

    fun searchYoutube(query: String): Boolean =
        openUrl("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))

    fun sleep() { sh.run("input keyevent KEYCODE_SLEEP") }
    fun wake() { sh.run("input keyevent KEYCODE_WAKEUP") }
    fun reboot() { sh.run("reboot") }

    /**
     * Turns the screen off after [minutes]. The countdown runs ON the projector, so it still fires when the phone is
     * away or the app is closed. One timer at a time: a new one replaces the old one.
     */
    fun scheduleSleep(minutes: Int) {
        require(minutes in 1..600) { "Sleep timer must be 1-600 minutes" }
        cancelSleep()
        val script = "E=\$((\$(date +%s)+${minutes * 60})); echo \$\$ \$E > $SLEEP_FILE; sleep ${minutes * 60}; rm -f $SLEEP_FILE; input keyevent KEYCODE_SLEEP"
        sh.run("nohup sh -c '$script' >/dev/null 2>&1 </dev/null &")
    }

    fun cancelSleep() { sh.run("[ -f $SLEEP_FILE ] && kill \$(cut -d' ' -f1 $SLEEP_FILE) 2>/dev/null; rm -f $SLEEP_FILE") }

    /** Seconds until the projector's screen turns off, or null when no timer is running. */
    fun sleepRemainingSeconds(): Int? = parseSleepRemaining(sh.run("cat $SLEEP_FILE 2>/dev/null; echo; date +%s").out)

    /** Frees memory on this 1 GB device by stopping everything that is not in front. */
    fun freeMemory() { sh.run("am kill-all") }

    fun openSettings(action: String) { sh.run("am start -a ${action.shellQuote()} 2>&1") }

    // ---- status --------------------------------------------------------------------------------------------

    fun status(): ProjectorStatus {
        val mem = sh.run("grep -E 'MemTotal|MemAvailable' /proc/meminfo").out
        val df = sh.run("df /data | tail -1").out
        fun prop(n: String) = sh.run("getprop $n").out.trim()
        return parseStatus(mem, df, prop("ro.product.model"), prop("ro.build.version.release"), prop("ro.product.cpu.abilist"))
    }

    // ---- installing ----------------------------------------------------------------------------------------

    /**
     * Installs an APK or a bundle. Each file is copied to a temporary folder on the projector, installed with `pm`, and
     * the folder is removed again whatever happens. [onStep] reports what is going on for the progress line.
     */
    fun install(source: InstallSource, onStep: (String) -> Unit = {}): InstallResult {
        val dir = "/data/local/tmp/pc-install-${System.currentTimeMillis()}"
        try {
            sh.run("mkdir -p $dir")
            val files = ArrayList<String>()
            val isBundle = !source.name.endsWith(".apk", ignoreCase = true)
            if (isBundle) {
                onStep("Unpacking ${source.name}…")
                ZipInputStream(source.open()).use { zip ->
                    var n = 0
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory || !e.name.endsWith(".apk", ignoreCase = true)) continue
                        n++
                        val remote = "$dir/part$n.apk"
                        onStep("Sending part $n…")
                        sh.push(NonClosing(zip), remote)
                        files += remote
                    }
                }
                if (files.isEmpty()) return InstallResult(false, "That file doesn't contain any APKs.")
            } else {
                onStep("Sending ${source.name}…")
                val remote = "$dir/app.apk"
                sh.push(source.open(), remote)
                files += remote
            }
            onStep("Installing…")
            val cmd = if (files.size == 1) "pm install -r -t ${files[0]} 2>&1" else "pm install-multiple -r -t ${files.joinToString(" ")} 2>&1"
            val r = sh.run(cmd)
            return if (r.out.contains("Success")) InstallResult(true, "Installed.") else InstallResult(false, explainInstallFailure(r.text))
        } finally {
            try { sh.run("rm -rf $dir") } catch (_: Exception) { }
        }
    }

    companion object {
        /** Below this much free memory (MB) a heavy app is likely to stall this 1 GB projector. */
        const val LOW_MEMORY_MB = 250

        fun isLowOnMemory(st: ProjectorStatus) = st.ramTotalMb > 0 && st.ramFreeMb < LOW_MEMORY_MB

        const val SLEEP_FILE = "/data/local/tmp/pc-sleep"

        /** Input is "<pid> <end epoch>" (maybe missing) then a blank line, then the current epoch. */
        fun parseSleepRemaining(out: String): Int? {
            val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val now = lines.lastOrNull()?.toLongOrNull() ?: return null
            val end = lines.dropLast(1).lastOrNull()?.split(' ')?.getOrNull(1)?.toLongOrNull() ?: return null
            return (end - now).coerceAtLeast(0).toInt().takeIf { it > 0 }
        }

        /** Our own helper and the projector's launchers are not things to start from here. */
        val HIDDEN = setOf("dev.keystone.pattern", "com.gathin.tvhome", "com.newlink.overseaslauncher", "com.zhiying.bluetoothmodelservice")

        /** `pkg/activity` lines from `cmd package query-activities --brief`. */
        fun parseComponents(out: String): List<String> =
            out.lineSequence().map { it.trim() }.filter { Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$").matches(it) }.distinct().toList()

        fun parseStatus(mem: String, df: String, model: String, release: String, abi: String): ProjectorStatus {
            fun kb(key: String) = Regex("$key:\\s+(\\d+)").find(mem)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val cols = df.trim().split(Regex("\\s+"))
            val total = cols.getOrNull(1)?.toLongOrNull() ?: 0L      // 1K blocks
            val free = cols.getOrNull(3)?.toLongOrNull() ?: 0L
            return ProjectorStatus((kb("MemTotal") / 1024).toInt(), (kb("MemAvailable") / 1024).toInt(), (total / 1024).toInt(), (free / 1024).toInt(), model, release, abi.substringBefore(','))
        }

        /** Makes `pm`'s error codes readable. */
        fun explainInstallFailure(text: String): String = when {
            text.contains("NO_MATCHING_ABIS") -> "This app isn't built for the projector's 32-bit processor. Look for an armeabi-v7a (arm32) version."
            text.contains("INSUFFICIENT_STORAGE") -> "The projector is out of storage. Remove an app and try again."
            text.contains("VERSION_DOWNGRADE") -> "A newer version is already installed. Uninstall it first to go back."
            text.contains("UPDATE_INCOMPATIBLE") || text.contains("SIGNATURES") -> "This copy is signed differently from the installed one. Uninstall the installed app first."
            text.contains("OLDER_SDK") -> "This app needs a newer Android than the projector has."
            text.contains("INVALID_APK") || text.contains("PARSE_FAILED") -> "That file isn't a valid app."
            text.contains("MISSING_SPLIT") -> "This app needs its other parts too. Pick the .apkm/.xapk bundle instead of one file."
            else -> text.lineSequence().firstOrNull { it.contains("Failure") || it.contains("Error") }?.trim() ?: "The projector refused to install it: ${text.take(160)}"
        }

        /** Accepts "youtu.be/x", "https://…" and rejects anything that is not a web link. */
        fun normaliseUrl(raw: String): String? {
            val t = raw.trim().lines().firstOrNull { it.contains('.') }?.trim() ?: return null
            if (t.any { it.isWhitespace() }) return null
            val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
            return withScheme.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }
}

/** Single-quotes a value for the projector's shell. */
fun String.shellQuote() = "'" + replace("'", "'\\''") + "'"

/** Lets a ZipInputStream be handed to code that closes its input, without ending the zip. */
private class NonClosing(private val s: InputStream) : InputStream() {
    override fun read() = s.read()
    override fun read(b: ByteArray, off: Int, len: Int) = s.read(b, off, len)
    override fun close() {}
}

object AppNames {
    private val known = mapOf(
        "com.google.android.youtube.tv" to "YouTube", "com.google.android.youtube" to "YouTube", "com.netflix.mediaclient" to "Netflix", "com.netflix.ninja" to "Netflix",
        "in.startv.hotstar" to "JioHotstar", "com.stremio.one" to "Stremio", "org.videolan.vlc" to "VLC", "org.localsend.localsend_app" to "LocalSend",
        "com.android.vending" to "Play Store", "com.playfy.tv" to "Playfy", "com.shamsheer.projectorsettingslauncher" to "Project Settings",
        "com.zhiying.appstore" to "App Store", "com.newlink.filemanager" to "Files", "com.newlink.cast" to "Cast", "com.hisilicon.miracast" to "Miracast",
        "com.mobisystems.editor.office_registered" to "Office", "com.android.chrome" to "Chrome", "com.amazon.amazonvideo.livingroom" to "Prime Video",
    )

    fun labelFor(pkg: String): String = known[pkg] ?: pkg.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
}
