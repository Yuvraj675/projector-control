package dev.keystone.auto

import dev.keystone.auto.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A simulated camera looking at a vertical wall: world x right, y down, z forward (towards the wall). */
private class SimCamera(val k: Intrinsics, pitchDeg: Double, rollDeg: Double, yawDeg: Double = 0.0) {
    private val right: Vec3
    private val down: Vec3
    private val fwd: Vec3

    init {
        val p = Math.toRadians(pitchDeg); val y = Math.toRadians(yawDeg); val r = Math.toRadians(rollDeg)
        // heading (yaw) about the vertical, then elevation
        val f0 = Vec3(sin(y) * cos(p), -sin(p), cos(y) * cos(p))
        val x0 = Vec3(cos(y), 0.0, -sin(y))
        val d0 = f0.cross(x0)
        fwd = f0
        right = x0 * cos(r) + d0 * sin(r)
        down = x0 * (-sin(r)) + d0 * cos(r)
    }

    fun toCamera(v: Vec3) = Vec3(v.dot(right), v.dot(down), v.dot(fwd))
    fun project(world: Vec3): Pt { val c = toCamera(world); return Pt(k.fx * c.x / c.z + k.cx, k.fy * c.y / c.z + k.cy) }
    fun upCam(): Vec3 = toCamera(Vec3(0.0, -1.0, 0.0)).unit()
}

class PoseTest {
    private val k = intrinsicsForJpeg(2801.094, 2801.094, 2048.0, 1536.0, intArrayOf(0, 0, 4096, 3072), 4096, 2304)

    @Test
    fun jpegCropMovesThePrincipalPoint() {
        assertEquals(2048.0, k.cx, 1e-9); assertEquals(1152.0, k.cy, 1e-9)
    }

    @Test
    fun realCaptureGravityGivesTheExpectedUpVector() {
        // measured on the real phone (vivo V2319, landscape, sensor orientation 90)
        val up = upInSensorFrame(Vec3(9.796918869018555, -0.13361075520515442, 0.20583276450634003), 90)
        assertEquals(0.0136, up.x, 5e-4); assertEquals(-0.9997, up.y, 5e-4); assertEquals(-0.021, up.z, 5e-4)
        assertEquals(-1.20, elevationDeg(up), 0.01)       // the app displayed -1.2 deg up/down
        assertEquals(0.78, rollDeg(up), 0.01)             // and 0.78 deg roll
    }

    @Test
    fun levellingMakesAWallRectangleARectangleForAnyPitchAndRoll() {
        val wallW = 2000.0; val wallH = 1125.0; val z0 = 3000.0
        val corners = listOf(Vec3(-wallW / 2 + 300, -wallH / 2 - 150, z0), Vec3(wallW / 2 + 300, -wallH / 2 - 150, z0),
            Vec3(wallW / 2 + 300, wallH / 2 - 150, z0), Vec3(-wallW / 2 + 300, wallH / 2 - 150, z0))
        for (pitch in listOf(-6.0, -1.2, 0.0, 3.0, 8.0)) for (roll in listOf(-5.0, -0.7, 0.0, 0.8, 4.0)) {
            val cam = SimCamera(k, pitch, roll)
            val photo = corners.map { cam.project(it) }
            val hr = rectifyHomography(k, cam.upCam())
            val q = rectQuality(warp(hr, photo))
            assertEquals("tilt at pitch=$pitch roll=$roll", 0.0, q.tiltDeg, 1e-6)
            assertEquals("aspect at pitch=$pitch roll=$roll", wallW / wallH, q.aspect, 1e-6)
        }
    }

    /** Full chain on a synthetic wall: the solved keystone must give a 16:9 rectangle in wall coordinates. */
    @Test
    fun endToEndOnASyntheticWall() {
        val rnd = Random(11)
        var worstTilt = 0.0; var worstAspect = 0.0
        repeat(25) {
            val cam = SimCamera(k, rnd.nextDouble(-4.0, 4.0), rnd.nextDouble(-3.0, 3.0))
            // projector panel -> wall (metric mm at z = 3000), a mildly keystoned throw
            fun jit(m: Double) = Pt(rnd.nextDouble(-m, m), rnd.nextDouble(-m, m))
            val wallQuad = listOf(Pt(-900.0, -450.0), Pt(1000.0, -400.0), Pt(1050.0, 700.0), Pt(-950.0, 650.0)).map { it + jit(60.0) }
            val hWall = homography(Panel.frame, wallQuad)
            val cur = Offsets(rnd.nextInt(0, 120).toDouble(), rnd.nextInt(0, 50).toDouble(), rnd.nextInt(0, 120).toDouble(),
                rnd.nextInt(0, 50).toDouble(), rnd.nextInt(0, 120).toDouble(), rnd.nextInt(0, 50).toDouble(),
                rnd.nextInt(0, 120).toDouble(), rnd.nextInt(0, 50).toDouble())
            val hk = homography(Panel.frame, cur.corners())
            fun wallToPhoto(p: Pt) = cam.project(Vec3(p.x, p.y, 3000.0))
            val markers = Panel.markers.map { wallToPhoto(hWall.apply(hk.apply(it))) }.shuffled(rnd)
            val m = CaptureMeasurement(markers, k, cam.upCam(), cur)
            val res = solveCapture(m, Params(aspect = 16.0 / 9.0))!!
            val target = res.offsets.rounded().clamped()
            val picOnWall = target.corners().map { hWall.apply(it) }
            val q = rectQuality(picOnWall)
            worstTilt = maxOf(worstTilt, q.tiltDeg); worstAspect = maxOf(worstAspect, Math.abs(q.aspect / (16.0 / 9.0) - 1))
        }
        assertTrue("worst tilt on the wall $worstTilt deg", worstTilt < 0.3)
        assertTrue("worst aspect error on the wall $worstAspect", worstAspect < 0.01)
    }

    @Test
    fun combineRejectsAnOutlier() {
        val good = Offsets(100.0, 30.0, 100.0, 5.0, 2.0, 55.0, 12.0, 80.0)
        val a = good; val b = Offsets(102.0, 33.0, 98.0, 7.0, 3.0, 52.0, 10.0, 84.0)
        val c = Offsets(98.0, 28.0, 101.0, 4.0, 1.0, 58.0, 13.0, 77.0)
        val bad = Offsets(160.0, 90.0, 40.0, 60.0, 50.0, 10.0, 60.0, 20.0)
        val r = combine(listOf(a, b, c, bad))
        assertEquals(listOf(3), r.dropped)
        assertEquals(100.0, r.offsets.x1, 1.5)
    }
}
