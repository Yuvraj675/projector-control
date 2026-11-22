package dev.keystone.auto

import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Panel
import dev.keystone.auto.core.Pt
import dev.keystone.auto.link.KeystoneTuner
import dev.keystone.auto.link.ShellStream
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TuneTest {
    private val start = Offsets(146.0, 153.0, 163.0, 1.0, 82.0, 2.0, 73.0, 48.0)

    /** Records what the tuner writes and applies it like the helper does (relative steps). */
    private class FakeStream : ShellStream {
        val lines = CopyOnWriteArrayList<String>()
        @Volatile var open = true
        @Volatile var failNext = false
        override val isOpen get() = open
        override fun write(text: String) {
            if (failNext) throw dev.keystone.auto.link.ProjectorException("The connection to the projector dropped.")
            lines += text.trim()
        }
        override fun close() { open = false }
        fun total(base: Offsets): Offsets {
            val m = base.toMap().toMutableMap()
            for (l in lines) for (part in l.split(" ")) { val (k, v) = part.split("="); m[k] = m.getValue(k) + v.toInt() }
            return Offsets.fromMap(m)
        }
    }

    // ---- geometry -------------------------------------------------------------------------------------------

    @Test fun cornersRoundTripThroughWithCorner() {
        for (i in 0..3) assertEquals(start.toList(), start.withCorner(i, start.corners()[i]).toList())
    }

    @Test fun moveEachCornerChangesOnlyItsOwnPair() {
        // right/down on the picture: TL x1,y1 grow; TR x2 shrinks, y2 grows; BR x4 shrinks, y4 shrinks; BL x3 grows, y3 shrinks
        val o = Offsets(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0)
        assertEquals(listOf(105.0, 103.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0), o.nudged(0, 5.0, 3.0).toList())
        assertEquals(listOf(100.0, 100.0, 95.0, 103.0, 100.0, 100.0, 100.0, 100.0), o.nudged(1, 5.0, 3.0).toList())
        assertEquals(listOf(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 95.0, 97.0), o.nudged(2, 5.0, 3.0).toList())
        assertEquals(listOf(100.0, 100.0, 100.0, 100.0, 105.0, 97.0, 100.0, 100.0), o.nudged(3, 5.0, 3.0).toList())
    }

    @Test fun cornerStaysInItsOwnBox() {
        val far = Offsets.NONE
        for (i in 0..3) {
            val box = Panel.cornerBox(i)
            for (p in listOf(Pt(-500.0, -500.0), Pt(5000.0, 5000.0), Pt(-500.0, 5000.0), Pt(5000.0, -500.0))) {
                val o = far.withCorner(i, p)
                assertTrue("limits ${o.toMap()}", o.withinLimits())
                val c = o.corners()[i]
                assertTrue(c.x in box.left..box.right && c.y in box.top..box.bottom)
            }
        }
    }

    @Test fun cornerBoxesMatchTheSafeLimits() {
        // the whole reach of a corner is exactly 0..MAX inward
        assertEquals(Panel.MAX_X, Panel.cornerBox(0).right, 0.0)
        assertEquals(Panel.W - Panel.MAX_X, Panel.cornerBox(1).left, 0.0)
        assertEquals(Panel.H - Panel.MAX_Y, Panel.cornerBox(2).top, 0.0)
        assertEquals(Panel.MAX_Y, Panel.cornerBox(3).bottom - Panel.cornerBox(3).top, 0.0)
    }

    // ---- tuner ----------------------------------------------------------------------------------------------

    @Test fun sendsTheExactDifference() {
        val s = FakeStream()
        KeystoneTuner(s, start, minGapMs = 0).use { t ->
            t.moveTo(start.nudged(0, 5.0, -3.0))
            assertTrue(t.settle())
            assertEquals(listOf("X1=5 Y1=-3"), s.lines.toList())
            assertEquals(start.nudged(0, 5.0, -3.0).toList(), t.position.toList())
        }
    }

    @Test fun manyFastMovesEndAtTheLastOneAndNeverDrift() {
        val s = FakeStream()
        val t = KeystoneTuner(s, start, minGapMs = 15)
        var o = start
        repeat(60) { k -> o = o.nudged(2, if (k % 3 == 0) 1.0 else -1.0, 1.0); t.moveTo(o) }
        assertTrue(t.settle())
        assertEquals(o.toList(), s.total(start).toList())
        assertTrue("merged: ${s.lines.size} lines for 60 moves", s.lines.size < 60)
        t.close()
    }

    @Test fun neverSendsAValueOutsideTheLimits() {
        val s = FakeStream()
        KeystoneTuner(s, start, minGapMs = 0).use { t ->
            t.moveTo(Offsets(9999.0, -50.0, 9999.0, -50.0, 9999.0, -50.0, 9999.0, -50.0))
            assertTrue(t.settle())
            val end = s.total(start)
            assertTrue(end.toMap().toString(), end.withinLimits())
        }
    }

    @Test fun closingSendsTheLastStepFirst() {
        val s = FakeStream()
        val t = KeystoneTuner(s, start, minGapMs = 20)
        t.moveTo(start.nudged(1, -4.0, 2.0))
        t.close()
        assertEquals(start.nudged(1, -4.0, 2.0).toList(), s.total(start).toList())
        assertFalse(s.isOpen)
        assertFalse(t.isOpen)
    }

    @Test fun aDroppedLinkIsReportedOnceAndStopsTheTuner() {
        val s = FakeStream().also { it.failNext = true }
        val errors = CopyOnWriteArrayList<String>()
        val t = KeystoneTuner(s, start, minGapMs = 0, onError = { errors += it })
        t.moveTo(start.nudged(0, 3.0, 0.0))
        Thread.sleep(300)
        assertEquals(1, errors.size)
        assertFalse(t.isOpen)
        t.moveTo(start.nudged(0, 9.0, 0.0))
        Thread.sleep(100)
        assertEquals(1, errors.size)
        t.close()
    }

    @Test fun noChangeSendsNothing() {
        val s = FakeStream()
        KeystoneTuner(s, start, minGapMs = 0).use { t -> t.moveTo(start); assertTrue(t.settle()) }
        assertTrue(s.lines.isEmpty())
    }
}
