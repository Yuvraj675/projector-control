package dev.keystone.auto

import dev.keystone.auto.link.Backoff
import org.junit.Assert.*
import org.junit.Test

class ReconnectTest {
    @Test fun backoffDoublesAndCaps() {
        assertEquals(listOf(3000L, 6000L, 12000L, 24000L, 30000L, 30000L), (1..6).map { Backoff.delayMs(it) })
    }

    @Test fun rescansOnlyEveryFewFailures() {
        assertEquals(listOf(2, 5, 8), (1..9).filter { Backoff.shouldRescan(it) })
    }
}
