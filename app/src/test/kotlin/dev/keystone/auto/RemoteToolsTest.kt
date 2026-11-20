package dev.keystone.auto

import dev.keystone.auto.link.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A shell that records everything: commands, files pushed, and the lines written to a streamed command. */
class RecordingShell(private val replies: (String) -> String = { "" }) : Shell {
    val commands = ArrayList<String>()
    val pushed = LinkedHashMap<String, Int>()
    val streamed = ArrayList<String>()
    var streamCmd: String? = null

    override fun run(cmd: String): ShellResult { commands += cmd; return ShellResult(replies(cmd), "", 0) }
    override fun push(input: InputStream, remotePath: String) { pushed[remotePath] = input.readBytes().size }
    override fun openStream(cmd: String): ShellStream {
        streamCmd = cmd
        return object : ShellStream {
            override val isOpen = true
            override fun write(text: String) { streamed += text.trimEnd('\n').split('\n') }
            override fun close() {}
        }
    }
}

class RemoteToolsTest {
    @Test
    fun mouseAndKeyboardAreRegisteredWithEvdevConfiguration() {
        val m = InputProtocol.registerMouse()
        assertTrue(m.contains("\"command\":\"register\"") && m.contains("\"configuration\"") && m.contains("[272,273,274]"))
        val k = InputProtocol.registerKeyboard()
        assertTrue(k.contains("\"data\":[1]") && k.contains(",580"))
        assertNotEquals(InputProtocol.MOUSE_ID, InputProtocol.KEYBOARD_ID)
    }

    @Test
    fun pointerMovesAreRelativeEventsEndedByASync() {
        assertEquals("""{"id":1,"command":"inject","events":[2,0,5,2,1,-3,0,0,0]}""", InputProtocol.move(5, -3))
        assertEquals("""{"id":1,"command":"inject","events":[2,0,5,0,0,0]}""", InputProtocol.move(5, 0))
        assertEquals("""{"id":1,"command":"inject","events":[2,8,-2,0,0,0]}""", InputProtocol.wheel(-2))
        assertEquals("""{"id":1,"command":"inject","events":[1,272,1,0,0,0]}""", InputProtocol.button(Evdev.BTN_LEFT, true))
    }

    @Test
    fun textMapsToUsKeysWithShift() {
        assertEquals(30 to false, InputProtocol.keyFor('a'))
        assertEquals(30 to true, InputProtocol.keyFor('A'))
        assertEquals(2 to true, InputProtocol.keyFor('!'))
        assertEquals(53 to true, InputProtocol.keyFor('?'))
        assertEquals(57 to false, InputProtocol.keyFor(' '))
        assertNull(InputProtocol.keyFor('é'))
        val (lines, skipped) = InputProtocol.typeLines("Hi é")
        assertEquals(listOf('é'), skipped)
        assertEquals(3, lines.size)                                   // H, i, space
        assertTrue(lines[0].contains("[1,42,1,0,0,0,1,35,1,0,0,0,1,35,0,0,0,0,1,42,0,0,0,0]"))   // shift down, H, H up, shift up
    }

    @Test
    fun remoteInputRegistersThenStreamsOrderedEvents() {
        val sh = RecordingShell { if (it.contains("grep -c")) "1" else "" }
        RemoteInput(sh, registerTimeoutMs = 500).use { r ->
            r.start()
            assertEquals("uinput -", sh.streamCmd)
            assertEquals(2, sh.streamed.size)                         // mouse + keyboard registration
            r.move(10.4, 0.0); r.move(0.7, 3.0)
            r.click()
            r.key(RemoteKey.HOME)
            r.type("ok")
            Thread.sleep(150)
            val moves = sh.streamed.filter { it.contains("\"id\":1") && it.contains("[2,0,") }
            assertEquals("pointer deltas are merged and the fraction carried", """{"id":1,"command":"inject","events":[2,0,11,2,1,3,0,0,0]}""", moves.first())
            val order = sh.streamed.drop(2)
            assertTrue("motion is sent before the click that follows it", order.indexOf(moves.first()) < order.indexOfFirst { it.contains("[1,272,1") })
            assertTrue(order.any { it.contains("[1,172,1") })          // HOME
            assertEquals(2, order.count { it.contains("\"id\":2") && it.contains("[1,24,1") || it.contains("[1,37,1") })   // o, k
        }
    }

    @Test
    fun parsesActivitiesStatusAndLinks() {
        val out = "11 activities found:\n  Activity #0:\n    priority=0 preferredOrder=0 match=0x108000\n    com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity\n  Activity #1:\n    in.startv.hotstar/com.hotstar.MainActivity\n    com.hisilicon.miracast/.activity.WelcomeActivity\n"
        assertEquals(listOf("com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity", "in.startv.hotstar/com.hotstar.MainActivity", "com.hisilicon.miracast/.activity.WelcomeActivity"), ProjectorTools.parseComponents(out))
        val st = ProjectorTools.parseStatus("MemTotal:         995900 kB\nMemAvailable:     224920 kB\n", "/dev/block/mmcblk0p26   3546848 1027672   2429392  30% /data/user/0", "HiDPT", "12", "armeabi-v7a,armeabi")
        assertEquals(972, st.ramTotalMb); assertEquals(219, st.ramFreeMb); assertEquals(2372, st.storageFreeMb); assertEquals("armeabi-v7a", st.abi)
        assertEquals("https://youtu.be/abc", ProjectorTools.normaliseUrl("youtu.be/abc"))
        assertEquals("https://www.youtube.com/watch?v=1", ProjectorTools.normaliseUrl("Watch this\nhttps://www.youtube.com/watch?v=1"))
        assertNull(ProjectorTools.normaliseUrl("hello world")); assertNull(ProjectorTools.normaliseUrl("javascript://x.y"))
        assertEquals("'it'\\''s'", "it's".shellQuote())
    }

    @Test
    fun listsAppsThirdPartyFirstAndHidesOurOwn() {
        val sh = RecordingShell {
            when {
                it.contains("LEANBACK") -> "  x\n    com.google.android.youtube.tv/.ShellActivity\n    dev.keystone.pattern/.PatternActivity\n    org.videolan.vlc/.Main\n"
                it.contains("category.LAUNCHER") -> "    com.android.vending/.TvMain\n    org.videolan.vlc/.Main\n"
                it.startsWith("pm list packages -3") -> "package:org.videolan.vlc\npackage:com.google.android.youtube.tv\n"
                else -> ""
            }
        }
        val apps = ProjectorTools(sh).listApps()
        assertEquals(listOf("VLC", "YouTube", "Play Store"), apps.map { it.label })
        assertTrue(apps.none { it.packageName == "dev.keystone.pattern" })
    }

    private fun src(name: String, bytes: ByteArray) = object : InstallSource { override val name = name; override fun open() = ByteArrayInputStream(bytes) }

    private fun zip(vararg entries: Pair<String, Int>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> entries.forEach { (n, size) -> z.putNextEntry(ZipEntry(n)); z.write(ByteArray(size) { 1 }); z.closeEntry() } }
        return b.toByteArray()
    }

    @Test
    fun installsASingleApkAndCleansUp() {
        val sh = RecordingShell { if (it.startsWith("pm install")) "Success\n" else "" }
        val r = ProjectorTools(sh).install(src("app.apk", ByteArray(1000)))
        assertTrue(r.ok)
        assertEquals(1000, sh.pushed.values.single())
        assertTrue(sh.commands.any { it.startsWith("pm install -r -t /data/local/tmp/pc-install-") })
        assertTrue(sh.commands.last().startsWith("rm -rf /data/local/tmp/pc-install-"))
    }

    @Test
    fun installsABundleWithInstallMultipleAndSkipsNonApkFiles() {
        val sh = RecordingShell { if (it.startsWith("pm install-multiple")) "Success\n" else "" }
        val steps = ArrayList<String>()
        val r = ProjectorTools(sh).install(src("show.apkm", zip("base.apk" to 300, "split_config.armeabi_v7a.apk" to 200, "info.json" to 10, "icon.png" to 10)), steps::add)
        assertTrue(r.ok)
        assertEquals(listOf(300, 200), sh.pushed.values.toList())
        assertTrue(sh.commands.any { it.startsWith("pm install-multiple -r -t ") && it.contains("part1.apk") && it.contains("part2.apk") })
        assertTrue(steps.any { it.startsWith("Unpacking") })
    }

    @Test
    fun explainsFailuresInPlainWordsAndStillCleansUp() {
        val sh = RecordingShell { if (it.startsWith("pm install")) "Failure [INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries]\n" else "" }
        val r = ProjectorTools(sh).install(src("big.apk", ByteArray(10)))
        assertFalse(r.ok); assertTrue(r.message.contains("32-bit"))
        assertTrue(sh.commands.last().startsWith("rm -rf"))
        assertFalse(ProjectorTools(RecordingShell()).install(src("x.apkm", zip("readme.txt" to 5))).ok)
    }

    @Test
    fun sleepTimerRunsOnTheProjectorAndReplacesTheOldOne() {
        val sh = RecordingShell()
        ProjectorTools(sh).scheduleSleep(30)
        assertTrue(sh.commands[0].contains("kill"))                       // old timer cancelled first
        val start = sh.commands[1]
        assertTrue(start.startsWith("nohup sh -c '") && start.endsWith("&"))
        assertTrue(start.contains("sleep 1800") && start.contains("KEYCODE_SLEEP") && start.contains("</dev/null"))
        assertThrows(IllegalArgumentException::class.java) { ProjectorTools(sh).scheduleSleep(0) }
    }

    @Test
    fun sleepRemainingIsParsedFromTheProjectorsClock() {
        assertEquals(600, ProjectorTools.parseSleepRemaining("1234 1600\n\n1000\n"))
        assertNull(ProjectorTools.parseSleepRemaining("\n1000\n"))             // no timer file
        assertNull(ProjectorTools.parseSleepRemaining("1234 900\n\n1000\n"))  // already past
    }

    @Test
    fun lowMemoryIsDetected() {
        fun st(free: Int) = ProjectorStatus(1000, free, 8000, 4000, "m", "12", "armeabi-v7a")
        assertTrue(ProjectorTools.isLowOnMemory(st(120)))
        assertFalse(ProjectorTools.isLowOnMemory(st(400)))
        assertFalse(ProjectorTools.isLowOnMemory(ProjectorStatus(0, 0, 0, 0, "", "", "")))   // unreadable status: don't act
    }

    @Test
    fun pngDetection() {
        assertTrue(ProjectorTools.isPng(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0)))
        assertFalse(ProjectorTools.isPng("not an image at all".toByteArray()))
    }

    @Test
    fun sendFileNamesAreSafeAndTypesKnown() {
        assertEquals("evil___x_.mp4", ProjectorTools.safeFileName("evil;\$(x).mp4"))
        assertEquals("passwd", ProjectorTools.safeFileName("../../etc/passwd"))
        assertEquals("file", ProjectorTools.safeFileName("..."))
        assertEquals("video/x-matroska", ProjectorTools.mimeFor("Movie.MKV"))
        assertEquals("*/*", ProjectorTools.mimeFor("noextension"))
    }

    @Test
    fun sendingAFileCopiesItThenOpensItByType() {
        val sh = RecordingShell()
        val src = object : InstallSource { override val name = "My Film.mp4"; override fun open() = ByteArrayInputStream(ByteArray(10)) }
        val r = ProjectorTools(sh).sendAndOpen(src, "video/*")
        assertTrue(r.ok)
        assertEquals(setOf("/sdcard/Download/ProjectorConnect/My Film.mp4"), sh.pushed.keys)
        val open = sh.commands.last()
        assertTrue(open.contains("-t 'video/mp4'") && open.contains("file:///sdcard/Download/ProjectorConnect/My Film.mp4"))
    }

    @Test
    fun magnetAndStremioLinksPassThrough() {
        assertEquals("magnet:?xt=urn:btih:abc", ProjectorTools.normaliseUrl("magnet:?xt=urn:btih:abc"))
        assertEquals("stremio:///detail/movie/tt1", ProjectorTools.normaliseUrl(" stremio:///detail/movie/tt1 "))
        assertNull(ProjectorTools.normaliseUrl("magnet:?xt=a b"))
        assertEquals("https://youtu.be/x", ProjectorTools.normaliseUrl("youtu.be/x"))
        assertNull(ProjectorTools.normaliseUrl("javascript:alert(1)"))
    }

    @Test
    fun recoveryCommandsMatchTheSavedBackups() {
        val sh = RecordingShell { if (it.startsWith("settings put")) "com.newlink.accessibility/com.newlink.accessibility.NewLinkAccessibilityService:com.hisilicon.rmservice/com.hisilicon.rmservice.HiRMService" else "" }
        val t = ProjectorTools(sh)
        assertTrue(t.restoreStockHome())
        assertEquals("cmd package set-home-activity com.newlink.nlprovision/.MainActivity 2>&1", sh.commands[0])
        assertTrue(t.restoreStockAccessibility())
        assertTrue(sh.commands[1].contains("NewLinkAccessibilityService:com.hisilicon.rmservice/com.hisilicon.rmservice.HiRMService"))
    }
}
