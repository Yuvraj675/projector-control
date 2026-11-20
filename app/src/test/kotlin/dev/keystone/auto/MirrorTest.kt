package dev.keystone.auto

import dev.keystone.auto.link.InputProtocol
import dev.keystone.auto.link.MirrorQuality
import dev.keystone.auto.link.NalSplitter
import dev.keystone.auto.link.ScreenMirror
import org.junit.Assert.*
import org.junit.Test

class MirrorTest {
    private fun nal(type: Int, vararg body: Int, long: Boolean = true): ByteArray =
        (if (long) byteArrayOf(0, 0, 0, 1) else byteArrayOf(0, 0, 1)) + byteArrayOf(type.toByte()) + body.map { it.toByte() }.toByteArray()

    private fun collect(chunk: Int, stream: ByteArray): List<Pair<ByteArray, Boolean>> {
        val out = ArrayList<Pair<ByteArray, Boolean>>()
        val s = NalSplitter { u, k -> out += u to k }
        var i = 0
        while (i < stream.size) { val n = minOf(chunk, stream.size - i); s.push(stream.copyOfRange(i, i + n)); i += n }
        return out
    }

    @Test
    fun groupsParameterSetsWithTheNextPicture() {
        val sps = nal(0x67, 1, 2, 3); val pps = nal(0x68, 4); val idr = nal(0x65, 9, 9, 9, 9); val p1 = nal(0x41, 7, 7); val p2 = nal(0x41, 8, 8, 8, long = false)
        val stream = sps + pps + idr + p1 + p2 + nal(0x41, 5)       // the last NAL only completes when the next start code shows up
        val units = collect(1000, stream)
        assertEquals(3, units.size)
        assertArrayEquals(sps + pps + idr, units[0].first); assertTrue(units[0].second)
        assertArrayEquals(p1, units[1].first); assertFalse(units[1].second)
        assertArrayEquals(p2, units[2].first)
    }

    @Test
    fun resultDoesNotDependOnHowTheBytesArriveInChunks() {
        val stream = nal(0x67, 1) + nal(0x68, 2) + nal(0x65, 1, 2, 3, 0, 5) + nal(0x41, 0, 0, 2) + nal(0x41, 6) + nal(0x41, 7)
        val whole = collect(stream.size, stream)
        for (chunk in listOf(1, 2, 3, 5, 7)) {
            val c = collect(chunk, stream)
            assertEquals("chunk $chunk", whole.size, c.size)
            c.zip(whole).forEach { (a, b) -> assertArrayEquals(b.first, a.first); assertEquals(b.second, a.second) }
        }
    }

    @Test
    fun touchScreenRegistrationDeclaresMultiTouchAndDirectProperty() {
        val r = InputProtocol.registerTouch()
        assertTrue(r.contains("\"name\":\"ProjectorConnect Touch\""))
        assertTrue(r.contains("{\"type\":110,\"data\":[1]}"))                    // INPUT_PROP_DIRECT: a touch screen, not a pad
        assertTrue(r.contains("\"code\":53,\"info\":{\"value\":0,\"minimum\":0,\"maximum\":1279"))
        assertTrue(r.contains("\"code\":54,\"info\":{\"value\":0,\"minimum\":0,\"maximum\":719"))
    }

    @Test
    fun touchEventsFollowProtocolB() {
        assertEquals("""{"id":3,"command":"inject","events":[3,47,0,3,57,5,3,53,640,3,54,360,1,330,1,0,0,0]}""", InputProtocol.touchDown(0, 5, 640, 360, true))
        assertEquals("""{"id":3,"command":"inject","events":[3,47,1,3,57,6,3,53,10,3,54,20,0,0,0]}""", InputProtocol.touchDown(1, 6, 10, 20, false))
        assertEquals("""{"id":3,"command":"inject","events":[3,47,0,3,53,1,3,54,2,3,47,1,3,53,3,3,54,4,0,0,0]}""",
            InputProtocol.touchMove(listOf(Triple(0, 1, 2), Triple(1, 3, 4))))
        assertEquals("""{"id":3,"command":"inject","events":[3,47,0,3,57,-1,1,330,0,0,0,0]}""", InputProtocol.touchUp(0, true))
    }

    @Test
    fun flushReleasesTheLastPictureOfAStaticScreen() {
        val out = ArrayList<Pair<ByteArray, Boolean>>()
        val s = NalSplitter { u, k -> out += u to k }
        val sps = nal(0x67, 1); val pps = nal(0x68, 2); val idr = nal(0x65, 3, 4, 5)
        s.push(sps + pps + idr)
        assertTrue(out.isEmpty())
        s.flush()
        assertEquals(1, out.size); assertArrayEquals(sps + pps + idr, out[0].first); assertTrue(out[0].second)
        s.flush(); assertEquals(1, out.size)
    }

    @Test
    fun streamIsTheLightOneByDefaultAndTheSharpOneOnRequest() {
        val shell = object : dev.keystone.auto.link.Shell { override fun run(cmd: String) = dev.keystone.auto.link.ShellResult("", "", 0) }
        assertTrue(ScreenMirror(shell, onUnit = { _, _ -> }, onProblem = {}).command.contains("--size 640x360 --bit-rate 1500000"))
        assertTrue(ScreenMirror(shell, MirrorQuality.Sharp, onUnit = { _, _ -> }, onProblem = {}).command.contains("--size 960x540 --bit-rate 3000000"))
    }

    /** Real crash from the phone: closing a running mirror interrupted its thread while it waited, and nothing caught that. */
    @Test
    fun closingARunningMirrorNeverThrowsInItsThread() {
        for (endsOnClose in listOf(true, false)) {
            val crashes = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
            try {
                val shell = object : dev.keystone.auto.link.Shell {
                    override fun run(cmd: String) = dev.keystone.auto.link.ShellResult("", "", 0)
                    override fun openOutput(cmd: String, onData: (ByteArray) -> Unit, onEnd: () -> Unit): AutoCloseable =
                        AutoCloseable { if (endsOnClose) onEnd() }              // false: the stream never reports its end
                }
                repeat(5) {
                    val m = ScreenMirror(shell, onUnit = { _, _ -> }, onProblem = {})
                    m.start(); Thread.sleep(60); m.close()
                }
                Thread.sleep(500)
                assertTrue("uncaught: $crashes", crashes.isEmpty())
            } finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
        }
    }
}
