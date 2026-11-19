package dev.keystone.auto

import dev.keystone.auto.core.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A simulated camera looking at a vertical wall: world x right, y down, z forward (towards the wall). */
class SimCamera(val k: Intrinsics, pitchDeg: Double, rollDeg: Double, yawDeg: Double = 0.0) {
    private val right: Vec3
    private val down: Vec3
    private val fwd: Vec3

    init {
        val p = Math.toRadians(pitchDeg); val y = Math.toRadians(yawDeg); val r = Math.toRadians(rollDeg)
        val f0 = Vec3(sin(y) * cos(p), -sin(p), cos(y) * cos(p))     // heading (yaw) about the vertical, then elevation
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

/** A projector throwing onto a wall (metric mm at z = 3000) and the helpers to "photograph" and judge it. */
class SimScene(val k: Intrinsics, rnd: Random, val wallZ: Double = 3000.0) {
    /** panel -> wall, a mildly keystoned throw */
    val hWall: Mat3 = homography(
        Panel.frame,
        listOf(Pt(-900.0, -450.0), Pt(1000.0, -400.0), Pt(1050.0, 700.0), Pt(-950.0, 650.0)).map { Pt(it.x + rnd.nextDouble(-50.0, 50.0), it.y + rnd.nextDouble(-50.0, 50.0)) },
    )

    private fun wallOf(keystone: Offsets, panelPt: Pt) = hWall.apply(homography(Panel.frame, keystone.corners()).apply(panelPt))

    /** A photo of the pattern: gravity sensor reading biased by `rollBiasDeg` like a real, slightly mis-calibrated sensor. */
    fun capture(cam: SimCamera, keystone: Offsets, rnd: Random, rollBiasDeg: Double = 0.0, noisePx: Double = 0.6): CaptureMeasurement {
        val markers = Panel.markers.map { m ->
            val w = wallOf(keystone, m)
            val p = cam.project(Vec3(w.x, w.y, wallZ))
            Pt(p.x + rnd.nextDouble(-noisePx, noisePx), p.y + rnd.nextDouble(-noisePx, noisePx))
        }.shuffled(rnd)
        val up = cam.upCam()
        val c = cos(Math.toRadians(rollBiasDeg)); val s = sin(Math.toRadians(rollBiasDeg))
        val biased = Vec3(up.x * c - up.y * s, up.x * s + up.y * c, up.z)
        return CaptureMeasurement(markers, k, biased, keystone)
    }

    /** The picture on the wall for the given keystone, judged in true wall metric. */
    fun wallQuality(offsets: Offsets): RectQuality = rectQuality(offsets.corners().map { hWall.apply(it) })

    /** The keystone that gives a perfect rectangle of `aspect` on the wall (what a perfectionist would accept). */
    fun idealOffsets(cur: Offsets, aspect: Double): Offsets {
        val hk = homography(Panel.frame, cur.corners())
        val obsWall = warp(hWall, warp(hk, Panel.markers))
        return solveKeystone(cur, obsWall, aspect)!!.offsets.rounded().clamped()
    }
}
