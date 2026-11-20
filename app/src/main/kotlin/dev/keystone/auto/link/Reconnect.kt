package dev.keystone.auto.link

/** How long to wait between attempts to get a dropped projector back. */
object Backoff {
    /** 3 s, 6 s, 12 s ... capped at 30 s. `attempt` counts failures so far (1 = first failure). */
    fun delayMs(attempt: Int): Long = (3000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(30_000L)

    /** A full subnet scan is expensive, so only look for a changed IP address on every few failures. */
    fun shouldRescan(attempt: Int): Boolean = attempt >= 2 && attempt % 3 == 2
}

/** The state of the link to the projector while the app is open. */
enum class LinkState { Online, Reconnecting }
