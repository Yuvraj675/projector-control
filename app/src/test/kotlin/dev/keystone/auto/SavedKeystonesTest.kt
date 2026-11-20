package dev.keystone.auto

import dev.keystone.auto.core.Offsets
import dev.keystone.auto.data.SavedKeystone
import dev.keystone.auto.data.SavedKeystones
import org.junit.Assert.*
import org.junit.Test

class SavedKeystonesTest {
    private fun o(v: Int) = Offsets.fromList(List(8) { v.toDouble() })
    private fun k(name: String, v: Int = 1) = SavedKeystone(name, o(v), 100L)

    @Test fun roundTripsThroughJson() {
        val list = listOf(k("Bedroom", 10), k("Living room", 20))
        assertEquals(list, SavedKeystones.fromJson(SavedKeystones.toJson(list)))
    }

    @Test fun sameNameReplaces() {
        val list = SavedKeystones.put(listOf(k("Bedroom", 1)), k("bedroom", 2))
        assertEquals(1, list.size)
        assertEquals(o(2), list[0].offsets)
    }

    @Test fun keepsTheNewestTwelve() {
        var list = emptyList<SavedKeystone>()
        (1..15).forEach { list = SavedKeystones.put(list, k("S$it")) }
        assertEquals(12, list.size)
        assertEquals("S15", list.last().name)
        assertEquals("S4", list.first().name)
    }

    @Test fun defaultNameAvoidsClashes() {
        assertEquals("Setup 3", SavedKeystones.defaultName(listOf(k("Setup 2"), k("x"))))
    }

    @Test fun badJsonIsEmpty() = assertTrue(SavedKeystones.fromJson("nope").isEmpty())
}
