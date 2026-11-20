package dev.keystone.auto.platform

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Decodes the projector's H.264 screen stream straight onto a [Surface]. Frames are dropped, never queued up, when behind. */
class MirrorDecoder(private val width: Int, private val height: Int, private val onFirstFrame: () -> Unit = {}) {
    private class Nal(val data: ByteArray, val key: Boolean)

    private val queue = LinkedBlockingQueue<Nal>()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var needKey = true

    fun feed(data: ByteArray, key: Boolean) {
        if (!running) return
        if (queue.size > MAX_BACKLOG) { queue.clear(); needKey = true }
        if (needKey && !key) return
        if (key) needKey = false
        queue.offer(Nal(data, key))
    }

    fun start(surface: Surface) {
        stop()
        running = true
        needKey = true
        thread = Thread { run(surface) }.apply { isDaemon = true; name = "mirror-decoder"; start() }
    }

    fun stop() {
        running = false
        val t = thread
        thread = null
        t?.interrupt()
        // The codec must be released before its Surface is used again, so wait for it.
        try { t?.join(3000) } catch (_: InterruptedException) { }
        queue.clear()
    }

    private fun run(surface: Surface) {
        val codec = try {
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            if (android.os.Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            f.setInteger(MediaFormat.KEY_MAX_WIDTH, 1280)        // the stream size changes with the quality: allow a new size without reconfiguring
            f.setInteger(MediaFormat.KEY_MAX_HEIGHT, 720)
            c.configure(f, surface, null, 0)
            c.start()
            c
        } catch (e: Throwable) { AppLog.e("mirror decoder", e); return }
        val info = MediaCodec.BufferInfo()
        var pts = 0L
        var first = true
        try {
            while (running) {
                val u = queue.poll(20, TimeUnit.MILLISECONDS)
                if (u != null) {
                    val i = codec.dequeueInputBuffer(20_000)
                    if (i >= 0) {
                        val b = codec.getInputBuffer(i)!!
                        b.clear(); b.put(u.data)
                        codec.queueInputBuffer(i, 0, u.data.size, pts, 0)
                        pts += 33_333
                    }
                }
                while (true) {
                    val o = codec.dequeueOutputBuffer(info, 0)
                    if (o >= 0) {
                        codec.releaseOutputBuffer(o, true)
                        if (first) { first = false; onFirstFrame() }
                    } else break
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Throwable) { AppLog.e("mirror decoder loop", e) }
        finally { try { codec.stop() } catch (_: Throwable) { }; try { codec.release() } catch (_: Throwable) { } }
    }

    private companion object { const val MAX_BACKLOG = 40 }
}
