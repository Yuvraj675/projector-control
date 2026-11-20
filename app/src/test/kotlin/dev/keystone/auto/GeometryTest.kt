package dev.keystone.auto

import dev.keystone.auto.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GeometryTest {
    @Test
    fun homographyMapsSourceCornersExactly() {
        val src = Panel.frame
        val dst = listOf(Pt(100.0, 50.0), Pt(1200.0, 20.0), Pt(1250.0, 700.0), Pt(30.0, 690.0))
        val h = homography(src, dst)
        for (i in 0..3) {
            val p = h.apply(src[i])
            assertEquals(dst[i].x, p.x, 1e-6); assertEquals(dst[i].y, p.y, 1e-6)
        }
        val back = h.inverse().apply(dst[2])
        assertEquals(src[2].x, back.x, 1e-6); assertEquals(src[2].y, back.y, 1e-6)
    }

    @Test
    fun offsetsAndCornersRoundTrip() {
        val o = Offsets(115.0, 31.0, 105.0, 5.0, 11.0, 13.0, 0.0, 8.0)
        val back = Offsets.fromCorners(o.corners())
        assertEquals(o.toList(), back.toList())
        // verified on the real projector: TL=(115,31) TR=(1175,5) BL=(11,707) BR=(1280,712)
        val c = o.corners()
        assertEquals(1175.0, c[1].x, 1e-9); assertEquals(707.0, c[3].y, 1e-9); assertEquals(712.0, c[2].y, 1e-9)
    }

    @Test
    fun orderingAndQuality() {
        val shuffled = listOf(Pt(900.0, 700.0), Pt(100.0, 90.0), Pt(120.0, 710.0), Pt(910.0, 80.0))
        val ordered = orderTlTrBrBl(shuffled)
        assertEquals(Pt(100.0, 90.0), ordered[0]); assertEquals(Pt(910.0, 80.0), ordered[1])
        assertEquals(Pt(900.0, 700.0), ordered[2]); assertEquals(Pt(120.0, 710.0), ordered[3])
        val q = rectQuality(listOf(Pt(0.0, 0.0), Pt(1600.0, 0.0), Pt(1600.0, 900.0), Pt(0.0, 900.0)))
        assertEquals(0.0, q.tiltDeg, 1e-9); assertEquals(16.0 / 9.0, q.aspect, 1e-9); assertEquals(1.0, q.rightOverLeft, 1e-9)
    }

    /** Synthetic wall/camera: the solved offsets must make the picture a 16:9 rectangle in the observed view. */
    @Test
    fun solverMakesARectangleInTheObservedView() {
        val rnd = Random(7)
        var worstTilt = 0.0; var worstAspect = 0.0; var infeasible = 0
        repeat(60) {
            fun r(m: Int) = rnd.nextInt(0, m).toDouble()
            val cur = Offsets(r(150), r(60), r(150), r(60), r(150), r(60), r(150), r(60))
            fun j(m: Double) = Pt(rnd.nextDouble(-m, m), rnd.nextDouble(-m, m))
            val base = listOf(Pt(300.0, 200.0), Pt(1600.0, 200.0), Pt(1600.0, 900.0), Pt(300.0, 900.0))
            val dst = base.map { it + j(80.0) }
            val hw = homography(Panel.frame, dst)                       // panel -> photo
            val hk = homography(Panel.frame, cur.corners())             // framebuffer -> panel
            val obs = warp(hw, warp(hk, Panel.markers))
            val res = solveKeystone(cur, obs)
            if (res == null) { infeasible++; return@repeat }
            val target = res.offsets.rounded().clamped()
            val q = rectQuality(warp(hw, target.corners()))
            worstTilt = maxOf(worstTilt, q.tiltDeg)
            worstAspect = maxOf(worstAspect, Math.abs(q.aspect / (16.0 / 9.0) - 1.0))
        }
        assertEquals("unsolvable cases", 0, infeasible)
        assertTrue("worst tilt $worstTilt deg", worstTilt < 0.3)
        assertTrue("worst aspect error $worstAspect", worstAspect < 0.005)
    }
}
