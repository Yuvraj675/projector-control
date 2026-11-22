package dev.keystone.auto

import dev.keystone.auto.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TuneViewTest {
    private val base = Offsets(146.0, 153.0, 163.0, 1.0, 82.0, 2.0, 73.0, 48.0)

    @Test fun theBaseLooksLikeTheFullRectangle() {
        val v = TuneView(base).corners(base)
        for (i in 0..3) { assertEquals(Panel.frame[i].x, v[i].x, 1e-6); assertEquals(Panel.frame[i].y, v[i].y, 1e-6) }
    }

    @Test fun movingACornerToWhereItIsChangesNothing() {
        val t = TuneView(base)
        for (i in 0..3) assertEquals(base.toList(), t.moveCorner(base, i, Panel.frame[i]).toList())
    }

    @Test fun theCornerFollowsTheTargetAndTheOthersStay() {
        val rnd = Random(7)
        repeat(200) {
            val b = Offsets.fromList(List(8) { i -> rnd.nextDouble(20.0, if (i % 2 == 0) 200.0 else 190.0) }).rounded()
            val t = TuneView(b)
            val i = rnd.nextInt(4)
            val before = t.corners(b)
            val target = Pt(before[i].x + rnd.nextDouble(-25.0, 25.0), before[i].y + rnd.nextDouble(-25.0, 25.0))
            val after = t.moveCorner(b, i, target)
            val seen = t.corners(after)
            val want = t.panelOf(target); val box = Panel.cornerBox(i)
            val reachable = want.x in box.left + 1..box.right - 1 && want.y in box.top + 1..box.bottom - 1
            if (reachable) assertTrue("corner $i at ${seen[i]} wanted $target", seen[i].dist(target) < 2.0)
            else assertTrue(after.withinLimits())      // out of range: it stops at the limit instead
            for (k in 0..3) if (k != i) { assertEquals(b.corners()[k], after.corners()[k]) }   // the other three are untouched
        }
    }

    @Test fun dragsStayInsideTheProjectorsRange() {
        val t = TuneView(base)
        for (i in 0..3) for (p in listOf(Pt(-9000.0, -9000.0), Pt(9000.0, 9000.0), Pt(-9000.0, 9000.0), Pt(9000.0, -9000.0)))
            assertTrue(t.moveCorner(base, i, p).withinLimits())
    }

    @Test fun everyStepMovesTheProjectorAtLeastOnePixelAndKeepsGoing() {
        val t = TuneView(base)
        for (i in 0..3) for (dir in listOf(Pt(1.0, 0.0), Pt(-1.0, 0.0), Pt(0.0, 1.0), Pt(0.0, -1.0))) {
            var o = base
            var moved = 0
            repeat(6) {
                val n = t.step(o, i, dir, 1.0)
                if (n.toList() != o.toList()) moved++
                // only this corner's pair of values may change
                for (k in 0..3) if (k != i) assertEquals(o.corners()[k], n.corners()[k])
                o = n
            }
            val box = Panel.cornerBox(i)
            val c = base.corners()[i]
            val atEdge = (c.x <= box.left + 8 || c.x >= box.right - 8 || c.y <= box.top + 8 || c.y >= box.bottom - 8)
            assertTrue("corner $i dir $dir moved only $moved of 6 times", moved == 6 || atEdge)
        }
    }

    @Test fun upMovesTheCornerUpOnTheViewForEveryCorner() {
        val t = TuneView(base)
        for (i in 0..3) {
            val n = t.step(base, i, Pt(0.0, -1.0), 3.0)
            val before = t.corners(base)[i]; val after = t.corners(n)[i]
            if (n.toList() != base.toList()) assertTrue("corner $i: $before -> $after", after.y < before.y)
        }
    }

    @Test fun boundsCoverTheFrameAndStayReasonable() {
        val b = TuneView(base).bounds
        assertTrue(b.left <= 0.0 && b.top <= 0.0 && b.right >= Panel.W && b.bottom >= Panel.H)
        assertTrue(b.right - b.left <= 2.2 * Panel.W && b.bottom - b.top <= 2.2 * Panel.H)
    }

    @Test fun aDegenerateBaseDoesNotCrash() {
        val t = TuneView(Offsets.NONE)
        assertEquals(Panel.frame[0], t.corners(Offsets.NONE)[0])
        assertTrue(t.moveCorner(Offsets.NONE, 0, Pt(30.0, 30.0)).withinLimits())
    }
}
