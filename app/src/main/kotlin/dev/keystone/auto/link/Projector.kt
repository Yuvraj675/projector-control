package dev.keystone.auto.link

import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Panel
import java.security.MessageDigest

/** The two files the app pushes to the projector. */
interface ProjectorAssets {
    fun helperJar(): ByteArray
    fun patternApk(): ByteArray
}

object ProjectorBundle {
    const val HELPER_PATH = "/data/local/tmp/ksall.jar"
    const val PATTERN_PACKAGE = "dev.keystone.pattern"
    const val PATTERN_COMPONENT = "dev.keystone.pattern/.PatternActivity"
    const val PATTERN_VERSION = 1                       // must equal versionCode in pattern/build.gradle
    const val HELPER_CLASS = "KsAll"
    val REQUIRED_LIBS = listOf("/system/framework/HitvShare.jar", "/system/framework/HitvManager.jar")
    val CLASSPATH = "CLASSPATH=$HELPER_PATH:${REQUIRED_LIBS.joinToString(":")}"
}

data class ProjectorInfo(
    val host: String,
    val model: String,
    val manufacturer: String,
    val androidRelease: String,
    val sdk: Int,
    val supported: Boolean,
    val unsupportedReason: String?,
) {
    val displayName get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { host }
}

/** Everything the app needs from the projector, on top of any [Shell]. */
class Projector(private val sh: Shell, private val assets: ProjectorAssets, val host: String) {

    fun info(): ProjectorInfo {
        fun prop(name: String) = sh.run("getprop $name").out.trim()
        val model = prop("ro.product.model")
        val manufacturer = prop("ro.product.manufacturer")
        val release = prop("ro.build.version.release")
        val sdk = prop("ro.build.version.sdk").toIntOrNull() ?: 0
        val libs = sh.run("ls ${ProjectorBundle.REQUIRED_LIBS.joinToString(" ")} 2>&1").text
        val missing = ProjectorBundle.REQUIRED_LIBS.filter { !libs.contains(it) || libs.contains("$it: No such file") || libs.contains("cannot access '$it'") }
        val svc = sh.run("service check hitvservice 2>&1").text
        val reason = when {
            missing.isNotEmpty() -> "This projector has no Hisilicon TV API (${missing.joinToString { it.substringAfterLast('/') }} missing)."
            !svc.contains("found") || svc.contains("not found") -> "The Hisilicon TV service is not running on this projector."
            else -> null
        }
        return ProjectorInfo(host, model, manufacturer, release, sdk, reason == null, reason)
    }

    /** A stable id for remembering per-projector data (original keystone, history). */
    fun stableId(): String {
        val model = sh.run("getprop ro.product.model").out.trim().ifBlank { "projector" }
        val mac = sh.run("cat /sys/class/net/wlan0/address 2>/dev/null").out.trim()
        return (model + (if (mac.isNotBlank()) "-$mac" else "-$host")).replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    // ---- helper ---------------------------------------------------------------------------------------------

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    /** Pushes the keystone helper if the projector's copy is missing or different. */
    fun ensureHelper(push: (ByteArray, String) -> Unit) {
        val jar = assets.helperJar()
        val remote = sh.run("md5sum ${ProjectorBundle.HELPER_PATH} 2>/dev/null").out.trim().substringBefore(' ')
        if (remote != md5(jar)) {
            push(jar, ProjectorBundle.HELPER_PATH)
            val check = sh.run("md5sum ${ProjectorBundle.HELPER_PATH}").out.trim().substringBefore(' ')
            if (check != md5(jar)) throw ProjectorException("Could not copy the helper to the projector.")
        }
    }

    private fun runHelper(args: String): Offsets {
        val r = sh.run("${ProjectorBundle.CLASSPATH} app_process /system/bin ${ProjectorBundle.HELPER_CLASS} $args 2>&1")
        val values = Regex("(X[1-4]|Y[1-4])=(-?\\d+)").findAll(r.out).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        if (!Offsets.KEYS.all { it in values }) throw ProjectorException("Unexpected reply from the keystone helper: ${r.text.take(200)}")
        return Offsets.fromMap(values)
    }

    fun readKeystone(): Offsets = runHelper("get")

    /**
     * Moves the corners to `target`. The projector's setters are relative steps, so we read the current values,
     * send the differences and verify by reading back (one corrective retry).
     */
    fun applyKeystone(target: Offsets): Offsets {
        val t = target.rounded()
        if (!t.withinLimits()) throw ProjectorException("Refusing to apply values outside the safe range: ${t.toMap()}")
        var cur = readKeystone()
        repeat(2) {
            val delta = t.toList().zip(cur.toList()).map { (a, b) -> Math.round(a - b).toInt() }
            if (delta.all { it == 0 }) return cur
            val args = "delta " + Offsets.KEYS.zip(delta).filter { it.second != 0 }.joinToString(" ") { "${it.first}=${it.second}" }
            cur = runHelper(args)
            if (cur.toList().zip(t.toList()).all { (a, b) -> Math.abs(a - b) <= 1.0 }) return cur
        }
        throw ProjectorException("The projector did not accept the new keystone (wanted ${t.toMap()}, got ${cur.toMap()}).")
    }

    // ---- pattern app ---------------------------------------------------------------------------------------

    fun patternInstalledVersion(): Int? {
        val r = sh.run("dumpsys package ${ProjectorBundle.PATTERN_PACKAGE} | grep versionCode").out
        return Regex("versionCode=(\\d+)").find(r)?.groupValues?.get(1)?.toInt()
    }

    /** Installs the pattern app if it is missing or older than the bundled one. Returns true if it installed it. */
    fun ensurePatternApp(push: (ByteArray, String) -> Unit): Boolean {
        val have = patternInstalledVersion()
        if (have != null && have >= ProjectorBundle.PATTERN_VERSION) return false
        val remote = "/data/local/tmp/ks-pattern.apk"
        push(assets.patternApk(), remote)
        val r = sh.run("pm install -r -t $remote 2>&1; rm -f $remote")
        if (!r.out.contains("Success")) throw ProjectorException("Could not install the pattern app: ${r.text.take(200)}")
        return true
    }

    fun showPattern() {
        sh.run("input keyevent KEYCODE_WAKEUP; am force-stop ${ProjectorBundle.PATTERN_PACKAGE}")
        val r = sh.run("am start -W -n ${ProjectorBundle.PATTERN_COMPONENT} 2>&1")
        if (!r.out.contains("Status: ok")) throw ProjectorException("Could not open the test pattern: ${r.text.take(200)}")
    }

    fun showHome() {
        sh.run("input keyevent KEYCODE_HOME")
    }

    /** Is the pattern the app in front right now? */
    fun patternIsShowing(): Boolean = sh.run("dumpsys window | grep mCurrentFocus").out.contains(ProjectorBundle.PATTERN_PACKAGE)

    companion object {
        /** Panel size the model assumes; the projector reports it too, so we can refuse a mismatch. */
        const val PANEL = "${Panel.W}x${Panel.H}"
    }
}
