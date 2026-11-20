package dev.keystone.auto

import dev.keystone.auto.link.*
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

/**
 * Live checks of the remote-control features against a real projector (`KS_PROJECTOR=<ip>`). Read-only apart from a
 * 2-pixel pointer nudge; nothing here touches the keystone.
 */
class LiveToolsTest {
    private fun host(): String { val h = System.getenv("KS_PROJECTOR"); Assume.assumeTrue("KS_PROJECTOR not set", h != null); return h!! }

    @Test
    fun listsAppsAndReadsStatus() {
        AdbConnection.connect(host()).use { c ->
            val t = ProjectorTools(c)
            val apps = t.listApps()
            println("LIVE apps: " + apps.joinToString { "${it.label}${if (it.thirdParty) "*" else ""}" })
            assertTrue(apps.isNotEmpty() && apps.any { it.thirdParty })
            assertTrue(apps.none { it.packageName == "dev.keystone.pattern" })
            val st = t.status()
            println("LIVE status: $st")
            assertTrue(st.ramTotalMb > 500 && st.storageTotalMb > 1000)
            assertTrue(st.abi.isNotBlank())
        }
    }

    @Test
    fun virtualMouseAndKeyboardAppearAndDisappear() {
        AdbConnection.connect(host()).use { c ->
            fun devices() = c.run("dumpsys input | grep -E '^  Device [0-9]+: ProjectorConnect' | wc -l").out.trim().toIntOrNull() ?: -1
            assertEquals("a stale virtual device is already there", 0, devices())
            RemoteInput(c).use { r ->
                val t0 = System.currentTimeMillis()
                r.start()
                println("LIVE remote ready after ${System.currentTimeMillis() - t0} ms")
                assertTrue(r.isOpen)
                assertEquals(2, devices())
                r.move(2.0, 0.0)
                Thread.sleep(300)
            }
            var left = devices()
            repeat(20) { if (left != 0) { Thread.sleep(300); left = devices() } }
            assertEquals("the devices should vanish when the stream closes", 0, left)
        }
    }
}

/** Live check of the screen mirror and touch screen (`KS_PROJECTOR=<ip>`). Never sends a touch. */
class LiveMirrorTest {
    private fun host(): String { val h = System.getenv("KS_PROJECTOR"); Assume.assumeTrue("KS_PROJECTOR not set", h != null); return h!! }

    @Test
    fun streamsDecodableUnitsAndTheTouchScreenComesAndGoes() {
        AdbConnection.connect(host()).use { c ->
            val units = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Boolean>>()
            val m = ScreenMirror(c, onUnit = { u, k -> units += u.size to k }, onProblem = { println("LIVE mirror problem: $it") })
            m.start()
            val end = System.currentTimeMillis() + 7000
            while (System.currentTimeMillis() < end) Thread.sleep(200)
            m.close()
            println("LIVE mirror: ${units.size} units, first key=${units.firstOrNull()?.second}, bytes=${units.sumOf { it.first }}")
            assertTrue(units.isNotEmpty())
            assertTrue("the first unit must be a key frame with SPS/PPS", units.first().second)

            fun touchDevices() = c.run("dumpsys input | grep -E '^  Device [0-9]+: ProjectorConnect Touch' | wc -l").out.trim().toIntOrNull() ?: -1
            assertEquals(0, touchDevices())
            TouchInput(c).use { t -> t.start(); assertTrue(t.isOpen); assertEquals(1, touchDevices()) }
            Thread.sleep(1500)
            assertEquals(0, touchDevices())
        }
    }
}
