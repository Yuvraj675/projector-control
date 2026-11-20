package dev.keystone.auto

import dev.keystone.auto.link.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    private val info = ProjectorInfo("10.0.0.5", "Hi3751", "Hisilicon", "12", 31, true, null)

    @Test fun reportListsTheFactsAndNeverTheAddress() {
        val st = ProjectorStatus(1000, 120, 8000, 3000, "m", "12", "armeabi-v7a")
        val r = Diagnostics.report(DiagnosticsInput(info, st, 40, 61.0, false, LinkState.Online, "0.3.0", "vivo V2319"))
        assertTrue(r.contains("120 MB free of 1000 MB (LOW)"))
        assertTrue(r.contains("40 ms") && r.contains("good"))
        assertTrue(r.contains("61 °C") && r.contains("off"))
        assertFalse(r.contains("10.0.0.5"))
    }

    @Test fun unknownThingsAreSaidSo() {
        val r = Diagnostics.report(DiagnosticsInput(info, null, null, null, null, LinkState.Reconnecting, "0.3.0", "p"))
        assertTrue(r.contains("no answer") && r.contains("not reported") && r.contains("Memory/storage: unknown"))
    }

    @Test fun temperatureUnits() {
        assertEquals(48.5, Diagnostics.parseTemperature("48500\n")!!, 0.01)
        assertEquals(52.0, Diagnostics.parseTemperature("52")!!, 0.01)
        assertNull(Diagnostics.parseTemperature("n/a"))
        assertNull(Diagnostics.parseTemperature("999999999"))
    }
}
