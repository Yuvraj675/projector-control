package dev.keystone.auto

import dev.keystone.auto.core.*
import dev.keystone.auto.vision.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/** Renders the test pattern, warps it like a photo of the wall and degrades it, then checks the detector. */
class MarkerDetectorTest {
    private fun pattern(): Array<FloatArray> {                     // 1280 x 720, values 0..255
        val img = Array(720) { FloatArray(1280) }
        val r = 0.055 * 720
        for (m in Panel.markers) for (y in 0 until 720) for (x in 0 until 1280) if (hypot(x + 0.5 - m.x, y + 0.5 - m.y) <= r) img[y][x] = 255f
        for (x in 0 until 1280) for (t in 0..3) { img[2 + t][x] = 255f; img[717 - t][x] = 255f }
        for (y in 0 until 720) for (t in 0..3) { img[y][2 + t] = 255f; img[y][1277 - t] = 255f }
        for (i in 1 until 10) {                                      // thick bright grid, as an over-exposed photo shows it
            val gx = 1280 * i / 10; val gy = 720 * i / 10
            for (y in 0 until 720) for (t in -2..2) img[y][gx + t] = maxOf(img[y][gx + t], 200f)
            for (x in 0 until 1280) for (t in -2..2) img[gy + t][x] = maxOf(img[gy + t][x], 200f)
        }
        // a "mute icon": thin ring near the bottom right
        for (y in 0 until 720) for (x in 0 until 1280) { val d = hypot(x - 1215.0, y - 600.0); if (d in 24.0..30.0) img[y][x] = 255f }
        return img
    }

    private fun sample(p: Array<FloatArray>, x: Double, y: Double): Float {
        if (x < 0 || y < 0 || x >= 1279 || y >= 719) return -1f
        val x0 = x.toInt(); val y0 = y.toInt(); val fx = (x - x0).toFloat(); val fy = (y - y0).toFloat()
        return p[y0][x0] * (1 - fx) * (1 - fy) + p[y0][x0 + 1] * fx * (1 - fy) + p[y0 + 1][x0] * (1 - fx) * fy + p[y0 + 1][x0 + 1] * fx * fy
    }

    private fun render(w: Int, h: Int, hw: Mat3, gain: Float, rnd: Random): GrayImage {
        val pat = pattern()
        val inv = hw.inverse()
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = inv.apply(Pt(x + 0.5, y + 0.5))
            val s = sample(pat, p.x, p.y)
            val v = if (s < 0) 50f + rnd.nextFloat() * 6 else 60f + s * gain * 0.75f + rnd.nextFloat() * 8
            out[y * w + x] = v.coerceIn(0f, 255f).toInt().toByte()
        }
        return GrayImage(w, h, out)
    }

    @Test
    fun findsTheFourMarkersInADegradedPhoto() {
        val rnd = Random(5)
        var worst = 0.0
        repeat(6) { trial ->
            val w = 2048; val h = 1152
            val base = listOf(Pt(560.0, 330.0), Pt(1500.0, 300.0), Pt(1540.0, 800.0), Pt(520.0, 830.0))
            val dst = base.map { Pt(it.x + rnd.nextDouble(-60.0, 60.0), it.y + rnd.nextDouble(-40.0, 40.0)) }
            val hw = homography(Panel.frame, dst)
            val img = render(w, h, hw, gain = 0.5f + 0.5f * trial / 5f, rnd = rnd).boxBlur(2)
            val res = MarkerDetector.detect(img)
            assertTrue("trial $trial: ${(res as? DetectResult.Failed)?.reason}", res is DetectResult.Found)
            val got = orderTlTrBrBl((res as DetectResult.Found).points)
            val want = warp(hw, Panel.markers)
            for (i in 0..3) worst = maxOf(worst, got[i].dist(want[i]))
        }
        assertTrue("worst marker error $worst px", worst < 2.0)
    }

    @Test
    fun failsCleanlyWhenThereIsNoPattern() {
        val img = GrayImage(640, 360, ByteArray(640 * 360) { 70 })
        assertTrue(MarkerDetector.detect(img) is DetectResult.Failed)
    }

    @Test
    fun ignoresAStraySmallBrightBlob() {
        val rnd = Random(9)
        val dst = listOf(Pt(500.0, 300.0), Pt(1500.0, 300.0), Pt(1500.0, 820.0), Pt(500.0, 820.0))
        val hw = homography(Panel.frame, dst)
        val img = render(2048, 1152, hw, 1f, rnd)
        for (y in 100 until 112) for (x in 150 until 162) img.data[y * 2048 + x] = 255.toByte()   // lamp reflection
        val res = MarkerDetector.detect(img)
        assertTrue(res is DetectResult.Found)
        val got = orderTlTrBrBl((res as DetectResult.Found).points)
        val want = warp(hw, Panel.markers)
        for (i in 0..3) assertTrue("marker $i off by ${got[i].dist(want[i])}", got[i].dist(want[i]) < 2.0)
    }
}
