package dev.keystone.auto.link

import dev.keystone.auto.core.Offsets

/**
 * Moves the projector's keystone corners while a finger is still dragging.
 *
 * The projector's setters are RELATIVE steps, and a fresh `app_process` per step is far too slow to follow a finger, so
 * this talks to the helper's `serve` mode: one long-lived process that applies every line it is sent. The tuner keeps two
 * values, where the user wants the corners ([moveTo]) and where the projector is believed to be ([position]), and one
 * worker thread sends the difference. Moves that arrive faster than the projector can take them are merged into one step,
 * so nothing queues up and the picture simply catches up to the finger. Nothing is ever sent outside the safe limits.
 *
 * @param minGapMs the shortest pause between two steps (the projector re-renders its picture for each one).
 * @param onError called once, from the worker thread, if the link drops. The tuner is closed afterwards.
 */
class KeystoneTuner(
    private val stream: ShellStream,
    start: Offsets,
    private val minGapMs: Long = 30,
    private val onError: (String) -> Unit = {},
) : AutoCloseable {
    private val lock = Object()
    private var target = start.rounded().clamped()
    private var sent = start.rounded()
    @Volatile private var closed = false
    @Volatile private var failed = false

    /** Where the projector's corners are believed to be right now. */
    val position: Offsets get() = synchronized(lock) { sent }

    val isOpen: Boolean get() = !closed && !failed && stream.isOpen

    private val worker = Thread({ run() }, "keystone-tuner").apply { isDaemon = true; start() }

    /** Asks for the corners to go to [t] (clamped to the safe range). Returns at once. */
    fun moveTo(t: Offsets) {
        synchronized(lock) { target = t.rounded().clamped(); lock.notifyAll() }
    }

    /** Waits until the projector has caught up with the last [moveTo], or the time is up. Returns whether it did. */
    fun settle(timeoutMs: Long = 1500): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (target.toList() != sent.toList() && !failed && !closed) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
            return target.toList() == sent.toList()
        }
    }

    private fun run() {
        try {
            while (true) {
                val line: String
                val next: Offsets
                synchronized(lock) {
                    while (!closed && target.toList() == sent.toList()) lock.wait()
                    if (closed && target.toList() == sent.toList()) return
                    val delta = target.toList().zip(sent.toList()).map { (a, b) -> Math.round(a - b).toInt() }
                    line = Offsets.KEYS.zip(delta).filter { it.second != 0 }.joinToString(" ") { "${it.first}=${it.second}" }
                    next = target
                }
                stream.write(line + "\n")
                synchronized(lock) { sent = next; lock.notifyAll() }
                if (minGapMs > 0) Thread.sleep(minGapMs)
            }
        } catch (e: InterruptedException) {
            // closed while waiting
        } catch (e: Exception) {
            failed = true
            synchronized(lock) { lock.notifyAll() }
            onError(e.message ?: "The connection to the projector dropped.")
        }
    }

    /** Sends any step still owed (briefly), then ends the helper. */
    override fun close() {
        if (closed) return
        if (!failed) settle(1200)
        synchronized(lock) { closed = true; lock.notifyAll() }
        try { worker.join(300) } catch (_: InterruptedException) { }
        try { stream.close() } catch (_: Exception) { }
    }
}
