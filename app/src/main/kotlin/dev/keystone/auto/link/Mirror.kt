package dev.keystone.auto.link

import java.io.ByteArrayOutputStream

/**
 * Cuts the raw H.264 byte stream that `screenrecord` prints into access units a decoder can take one at a time.
 * Parameter sets (SPS/PPS) and other non-picture NAL units are held back and sent together with the next picture, so the
 * decoder always sees "SPS PPS IDR" as one piece. Pure Kotlin, unit-tested.
 */
class NalSplitter(private val emit: (unit: ByteArray, keyFrame: Boolean) -> Unit) {
    private var buf = ByteArray(1 shl 16)
    private var len = 0
    private val header = ByteArrayOutputStream()

    @Synchronized fun push(data: ByteArray, n: Int = data.size) {
        if (len + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        System.arraycopy(data, 0, buf, len, n)
        len += n
        drain()
    }

    /**
     * A picture is only known to be complete when the next start code arrives, but `screenrecord` sends nothing while the
     * screen is static, so the last picture would sit here. Call this after the stream has been quiet for a moment.
     */
    @Synchronized fun flush() {
        val s0 = nextStart(0)
        if (s0 < 0) return
        val nal = buf.copyOfRange(s0, len)
        len = 0
        handle(nal)
    }

    private fun startCodeAt(i: Int): Int {          // length of the start code at i (3 or 4), or 0
        if (i + 3 <= len && buf[i].toInt() == 0 && buf[i + 1].toInt() == 0) {
            if (buf[i + 2].toInt() == 1) return 3
            if (i + 4 <= len && buf[i + 2].toInt() == 0 && buf[i + 3].toInt() == 1) return 4
        }
        return 0
    }

    private fun nextStart(from: Int): Int {
        var i = from
        while (i + 3 <= len) { if (startCodeAt(i) != 0) return i; i++ }
        return -1
    }

    private fun drain() {
        var pos = 0
        while (true) {
            val s0 = nextStart(pos)
            if (s0 < 0) break
            val s1 = nextStart(s0 + startCodeAt(s0))
            if (s1 < 0) { pos = s0; break }            // the last NAL may still be growing
            handle(buf.copyOfRange(s0, s1))
            pos = s1
        }
        if (pos > 0) { System.arraycopy(buf, pos, buf, 0, len - pos); len -= pos }
        else if (len > MAX_PENDING) len = 0            // garbage with no start code: start over
    }

    private fun handle(nal: ByteArray) {
        val sc = if (nal.size > 3 && nal[2].toInt() == 1) 3 else 4
        if (nal.size <= sc) return
        val type = nal[sc].toInt() and 0x1F
        if (type == 1 || type == 5) {
            val out = header.toByteArray() + nal
            header.reset()
            emit(out, type == 5)
        } else header.write(nal)
    }

    private companion object { const val MAX_PENDING = 8 shl 20 }
}

/** How much of the projector's encoder the live screen may use. Smooth is the default: the encoder cost grows with the picture size. */
enum class MirrorQuality(val width: Int, val height: Int, val bitRate: Int) {
    Smooth(640, 360, 1_500_000),
    Sharp(960, 540, 3_000_000),
}

/** Live view of the projector's screen: runs `screenrecord` over ADB and feeds H.264 access units to [onUnit]. */
class ScreenMirror(
    private val sh: Shell,
    quality: MirrorQuality = MirrorQuality.Smooth,
    private val onUnit: (ByteArray, Boolean) -> Unit,
    private val onProblem: (String) -> Unit,
) : AutoCloseable {
    @Volatile private var closed = false
    @Volatile private var current: AutoCloseable? = null
    private var thread: Thread? = null

    private val width = quality.width
    private val height = quality.height
    private val bitRate = quality.bitRate

    val command get() = "screenrecord --size ${width}x$height --bit-rate $bitRate --time-limit $SEGMENT_SECONDS --output-format=h264 -"

    fun start() {
        thread = Thread {
            var quick = 0
            while (!closed) {
                val ended = java.util.concurrent.CountDownLatch(1)
                val splitter = NalSplitter { u, k -> if (!closed) onUnit(u, k) }
                val lastData = java.util.concurrent.atomic.AtomicLong(0)
                val flusher = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mirror-flush").apply { isDaemon = true } }
                flusher.scheduleWithFixedDelay({
                    val t = lastData.get()
                    if (t != 0L && System.nanoTime() - t > QUIET_NS && lastData.compareAndSet(t, 0L)) splitter.flush()
                }, 20, 20, java.util.concurrent.TimeUnit.MILLISECONDS)
                val began = System.currentTimeMillis()
                var gotData = false
                try {
                    current = sh.openOutput(command, { b -> gotData = true; splitter.push(b); lastData.set(System.nanoTime()) }, { ended.countDown() })
                } catch (e: Exception) { onProblem("Couldn't start the projector's screen stream."); return@Thread }
                ended.await()
                flusher.shutdownNow()
                if (closed) break
                // screenrecord stops by itself after its time limit; a run that dies at once means it cannot work here.
                if (System.currentTimeMillis() - began < 2500 && !gotData) quick++ else quick = 0
                if (quick >= 3) { onProblem("The projector didn't start its screen stream."); break }
                try { Thread.sleep(150) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "screen-mirror"; start() }
    }

    override fun close() {
        closed = true
        try { current?.close() } catch (_: Exception) { }
        thread?.interrupt()
    }

    companion object {
        const val SEGMENT_SECONDS = 170 
        const val QUIET_NS = 60_000_000L
    }
}
