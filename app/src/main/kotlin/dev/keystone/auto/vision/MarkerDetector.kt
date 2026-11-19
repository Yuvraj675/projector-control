package dev.keystone.auto.vision

import dev.keystone.auto.core.Pt
import dev.keystone.auto.core.isConvex
import dev.keystone.auto.core.polygonArea
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed class DetectResult {
    /** The four marker centres in input-image pixels, in no particular order. */
    class Found(val points: List<Pt>, val candidates: Int, val threshold: Int) : DetectResult()

    class Failed(val reason: String, val candidates: Int) : DetectResult()
}

/**
 * Finds the four white filled-circle markers of the test pattern in a photo.
 *
 * Real photos of a projection are often over-exposed, so the grid, the border and the firmware's mute icon are bright
 * as well. An opening wider than those thin features removes them and keeps only the filled discs; the four discs that
 * are most alike in size and form a convex quadrilateral are then chosen.
 */
object MarkerDetector {
    fun detect(input: GrayImage): DetectResult {
        val w = input.w
        val h = input.h
        val blurred = input.boxBlur(max(1, (w / 1500.0).roundToInt()))
        val thr = max(max(blurred.otsu(), (0.8 * blurred.percentile(0.997)).toInt()), 100)
        val mask = Mask.threshold(blurred, thr)
        val k = max(3, (0.008 * w).roundToInt()) or 1               // wider than grid / border lines
        val opened = Mask.open(mask, w, h, k)
        val minArea = max(12, (0.0003 * w * h).toInt())
        val maxArea = (0.03 * w * h).toInt()
        val cands = connectedComponents(opened, w, h, minArea).filter {
            it.area <= maxArea && it.width.coerceAtLeast(it.height) <= 0.3 * w &&
                it.fill in 0.6..0.97 &&
                it.width.toDouble() / it.height in 0.5..2.0
        }
        if (cands.size < 4) return DetectResult.Failed("found ${cands.size} marker candidates, need 4", cands.size)

        var best: List<Blob>? = null
        var bestScore = Double.MAX_VALUE
        val n = cands.size
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) for (d in c + 1 until n) {
            val combo = listOf(cands[a], cands[b], cands[c], cands[d])
            val areas = combo.map { it.area.toDouble() }
            val mean = areas.average()
            val std = sqrt(areas.sumOf { (it - mean) * (it - mean) } / 4)
            val pts = combo.map { Pt(it.cx, it.cy) }
            val ordered = byAngle(pts)
            if (!isConvex(ordered)) continue
            val score = std / mean - 0.001 * sqrt(polygonArea(ordered))
            if (score < bestScore) {
                bestScore = score
                best = combo
            }
        }
        val chosen = best ?: return DetectResult.Failed("no convex set of four markers among $n candidates", n)
        return DetectResult.Found(chosen.map { Pt(it.cx, it.cy) }, n, thr)
    }

    private fun byAngle(p: List<Pt>): List<Pt> {
        val cx = p.sumOf { it.x } / p.size
        val cy = p.sumOf { it.y } / p.size
        return p.sortedBy { atan2(it.y - cy, it.x - cx) }
    }
}
