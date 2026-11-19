package dev.keystone.auto

import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Panel
import dev.keystone.auto.link.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Assets read straight from the repo (the same bytes the app bundles). */
class RepoAssets : ProjectorAssets {
    private val dir = File("src/main/assets/projector")
    override fun helperJar() = File(dir, "ksall.jar").readBytes()
    override fun patternApk() = File(dir, "pattern.apk").readBytes()
}

/** A projector simulated at the shell level, including the real device's "values never go below zero" behaviour. */
class FakeProjector(var offsets: Offsets, private val assets: ProjectorAssets, var hasTvApi: Boolean = true) : Shell {
    var helperOnDevice: ByteArray? = null
    var patternVersion: Int? = null
    var refuseDeltas = false
    val log = ArrayList<String>()

    fun pushHelper(b: ByteArray) { helperOnDevice = b }

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    override fun run(cmd: String): ShellResult {
        log += cmd
        fun ok(s: String) = ShellResult(s, "", 0)
        return when {
            cmd.startsWith("getprop ro.product.model") -> ok("HiDPTAndroid Hi3751V350\n")
            cmd.startsWith("getprop ro.product.manufacturer") -> ok("Hisilicon\n")
            cmd.startsWith("getprop ro.build.version.release") -> ok("12\n")
            cmd.startsWith("getprop ro.build.version.sdk") -> ok("31\n")
            cmd.startsWith("ls /system/framework/HitvShare.jar") ->
                if (hasTvApi) ok("/system/framework/HitvManager.jar\n/system/framework/HitvShare.jar\n")
                else ok("ls: /system/framework/HitvShare.jar: No such file or directory\nls: /system/framework/HitvManager.jar: No such file or directory\n")
            cmd.startsWith("service check hitvservice") -> ok(if (hasTvApi) "Service hitvservice: found\n" else "Service hitvservice: not found\n")
            cmd.startsWith("md5sum ") -> helperOnDevice?.let { ok("${md5(it)}  /data/local/tmp/ksall.jar\n") } ?: ok("")
            cmd.contains("app_process") && cmd.contains(" get") -> ok(fmt())
            cmd.contains("app_process") && cmd.contains(" delta") -> {
                if (!refuseDeltas) {
                    val v = offsets.toList().toMutableList()
                    Regex("(X[1-4]|Y[1-4])=(-?\\d+)").findAll(cmd.substringAfter("delta")).forEach {
                        val i = Offsets.KEYS.indexOf(it.groupValues[1]); v[i] = maxOf(0.0, v[i] + it.groupValues[2].toInt())
                    }
                    offsets = Offsets.fromList(v)
                }
                ok(fmt())
            }
            cmd.startsWith("dumpsys package dev.keystone.pattern") -> ok(patternVersion?.let { "    versionCode=$it minSdk=24\n" } ?: "")
            cmd.startsWith("pm install") -> { patternVersion = ProjectorBundle.PATTERN_VERSION; ok("Success\n") }
            cmd.startsWith("am start -W -n dev.keystone.pattern") -> ok("Status: ok\n")
            else -> ok("")
        }
    }

    private fun fmt() = Offsets.KEYS.zip(offsets.toList()).joinToString(" ") { "${it.first}=${it.second.toInt()}" } + "\n"
}

class LinkTest {
    private val assets = RepoAssets()
    private val d = Offsets(103.0, 36.0, 107.0, 7.0, 1.0, 56.0, 13.0, 74.0)

    @Test
    fun subnetHostsExcludeNetworkBroadcastAndSelf() {
        val h = Subnet.hosts("192.0.2.172", 24)
        assertEquals(253, h.size)
        assertFalse(h.contains("192.0.2.0")); assertFalse(h.contains("192.0.2.255")); assertFalse(h.contains("192.0.2.172"))
        assertTrue(h.contains("192.0.2.13"))
    }

    @Test
    fun bigSubnetsAreWindowedAroundTheDevice() {
        val h = Subnet.hosts("10.0.5.20", 16, maxHosts = 512)
        assertTrue(h.size in 509..512); assertTrue(h.contains("10.0.5.21")); assertTrue(h.contains("10.0.5.21"))
    }

    @Test
    fun readAndApplyKeystone() {
        val fake = FakeProjector(Offsets(104.0, 30.0, 106.0, 4.0, 2.0, 58.0, 12.0, 81.0), assets)
        val p = Projector(fake, assets, "10.0.0.2")
        assertEquals(30.0, p.readKeystone().y1, 0.0)
        val after = p.applyKeystone(d)
        assertEquals(d.toList(), after.toList()); assertEquals(d.toList(), fake.offsets.toList())
        // applying the same values again sends no delta at all
        fake.log.clear(); p.applyKeystone(d)
        assertTrue(fake.log.none { it.contains(" delta") })
    }

    @Test
    fun valuesBelowZeroAreHandledAndOutOfRangeIsRefused() {
        val fake = FakeProjector(d, assets)
        val p = Projector(fake, assets, "h")
        val zero = d.copy(x3 = 0.0)
        assertEquals(0.0, p.applyKeystone(zero).x3, 0.0)
        try { p.applyKeystone(d.copy(x1 = 900.0)); fail("expected refusal") } catch (e: ProjectorException) { assertTrue(e.message!!.contains("safe range")) }
        try { p.applyKeystone(d.copy(y2 = -5.0)); fail("expected refusal") } catch (e: ProjectorException) { }
    }

    @Test
    fun failsLoudlyIfTheProjectorIgnoresTheChange() {
        val fake = FakeProjector(d, assets).also { it.refuseDeltas = true }
        try { Projector(fake, assets, "h").applyKeystone(d.copy(x1 = 120.0)); fail("expected failure") }
        catch (e: ProjectorException) { assertTrue(e.message!!.contains("did not accept")) }
    }

    @Test
    fun helperIsPushedOnlyWhenMissingOrDifferent() {
        val fake = FakeProjector(d, assets)
        val p = Projector(fake, assets, "h")
        var pushes = 0
        val push = { b: ByteArray, _: String -> pushes++; fake.pushHelper(b) }
        p.ensureHelper(push); p.ensureHelper(push)
        assertEquals(1, pushes)
        fake.helperOnDevice = byteArrayOf(1, 2, 3)               // stale copy
        p.ensureHelper(push)
        assertEquals(2, pushes)
    }

    @Test
    fun patternAppInstalledOnceAndSupportDetected() {
        val fake = FakeProjector(d, assets)
        val p = Projector(fake, assets, "h")
        assertNull(p.patternInstalledVersion())
        assertTrue(p.ensurePatternApp { _, _ -> }); assertFalse(p.ensurePatternApp { _, _ -> })
        p.showPattern()
        val info = p.info()
        assertTrue(info.supported); assertEquals("Hisilicon HiDPTAndroid Hi3751V350", info.displayName)
        fake.hasTvApi = false
        val bad = p.info()
        assertFalse(bad.supported); assertNotNull(bad.unsupportedReason)
    }

    /** The pattern app and the phone app must agree on where the markers are and which version is bundled. */
    @Test
    fun patternAppSourceMatchesTheModel() {
        val src = File("../pattern/src/main/java/dev/keystone/pattern/PatternActivity.java").readText()
        val nums = Regex("MARKERS = \\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL).find(src)!!.groupValues[1]
        val fr = Regex("([0-9.]+)f").findAll(nums).map { it.groupValues[1].toDouble() }.toList()
        assertEquals(8, fr.size)
        Panel.markerFractions.forEachIndexed { i, (x, y) -> assertEquals(x, fr[2 * i], 1e-9); assertEquals(y, fr[2 * i + 1], 1e-9) }
        val gradle = File("../pattern/build.gradle").readText()
        assertEquals(ProjectorBundle.PATTERN_VERSION, Regex("versionCode (\\d+)").find(gradle)!!.groupValues[1].toInt())
    }
}

/** Runs against a real projector when KS_PROJECTOR=<ip> is set (read-only apart from copying the tiny helper). */
class LiveProjectorTest {
    @Test
    fun readsTheRealProjector() {
        val host = System.getenv("KS_PROJECTOR")
        org.junit.Assume.assumeTrue("KS_PROJECTOR not set", host != null)
        val assets = RepoAssets()
        AdbConnection.connect(host!!).use { conn ->
            val p = Projector(conn, assets, host)
            val info = p.info()
            println("LIVE info: $info")
            assertTrue(info.unsupportedReason, info.supported)
            p.ensureHelper { b, path -> conn.push(b, path) }
            val k = p.readKeystone()
            println("LIVE keystone: ${k.toMap()}  panel use ${"%.1f".format(100 * k.panelUse())}%")
            assertTrue(k.withinLimits())
            val same = p.applyKeystone(k)                                   // no-op apply: must send nothing and return the same
            assertEquals(k.toList(), same.toList())
            println("LIVE pattern app version on projector: ${p.patternInstalledVersion()}  id=${p.stableId()}")
        }
    }

    /** KS_SCAN="<own ip>/<prefix>" scans that subnet for ADB (port 5555); the projector must show up. */
    @Test
    fun scansTheSubnetAndFindsTheProjector() {
        val spec = System.getenv("KS_SCAN")
        org.junit.Assume.assumeTrue("KS_SCAN not set", spec != null)
        val (ip, prefix) = spec!!.split("/")
        val hosts = Subnet.hosts(ip, prefix.toInt())
        val t0 = System.currentTimeMillis()
        val found = AdbScanner.scan(hosts)
        println("LIVE scan of ${hosts.size} hosts took ${System.currentTimeMillis() - t0} ms, ADB open on: $found")
        System.getenv("KS_PROJECTOR")?.let { assertTrue("projector $it not found in $found", it in found) }
        assertTrue(found.isNotEmpty())
    }
}
