package dev.keystone.auto.core

/**
 * Finds the corner offsets that make the projected picture the largest rectangle (of a given aspect ratio) in the
 * observed view.
 *
 * `observed` are the four marker centres as seen in a view where rectangles are rectangles (the phone photo after
 * levelling), ordered TL, TR, BR, BL. Four correspondences determine the panel -> view homography exactly; we then
 * look for the biggest axis-aligned rectangle whose pre-image still fits inside the panel (within sensible corner
 * limits) and return the offsets that produce it.
 */
data class SolveResult(
    val offsets: Offsets,          // unrounded
    val rectHeightPx: Double,      // height of the chosen rectangle in the observed view
    val rectCentre: Pt,
)

fun solveKeystone(
    current: Offsets,
    observed: List<Pt>,
    aspect: Double = Panel.W / Panel.H,
    margin: Double = 1.0,
    grid: Int = 21,
    maxOffX: Double = 320.0,
    maxOffY: Double = 180.0,
): SolveResult? {
    require(observed.size == 4)
    val hFbToPanel = homography(Panel.frame, current.corners())
    val markerPanel = warp(hFbToPanel, Panel.markers)
    val hPw = homography(markerPanel, observed)          // panel -> observed view
    val hWp = hPw.inverse()
    val quad = warp(hPw, Panel.frame)                    // the whole panel as seen in the view
    val ctr = Pt(quad.sumOf { it.x } / 4, quad.sumOf { it.y } / 4)
    val spanX = quad.maxOf { it.x } - quad.minOf { it.x }
    val spanY = quad.maxOf { it.y } - quad.minOf { it.y }

    fun rectPre(t: Pt, s: Double): List<Pt> {
        val w = s * aspect
        val h = s
        val r = listOf(
            Pt(t.x - w / 2, t.y - h / 2), Pt(t.x + w / 2, t.y - h / 2),
            Pt(t.x + w / 2, t.y + h / 2), Pt(t.x - w / 2, t.y + h / 2),
        )
        return warp(hWp, r)
    }

    fun inBounds(p: List<Pt>) = p.all { it.x >= margin && it.x <= Panel.W - margin && it.y >= margin && it.y <= Panel.H - margin }

    fun inLimits(p: List<Pt>): Boolean {
        val o = Offsets.fromCorners(p)
        return o.x1 <= maxOffX && o.x2 <= maxOffX && o.x3 <= maxOffX && o.x4 <= maxOffX &&
            o.y1 <= maxOffY && o.y2 <= maxOffY && o.y3 <= maxOffY && o.y4 <= maxOffY
    }

    fun fits(t: Pt, s: Double): Boolean {
        val p = rectPre(t, s)
        return inBounds(p) && inLimits(p)
    }

    /** Largest s fitting inside the panel (monotone: bisect), then scan down until the offset limits hold. */
    fun bestSize(t: Pt): Double {
        var lo = 1.0
        var hi = spanY * 1.2
        if (!inBounds(rectPre(t, lo))) return 0.0
        repeat(40) {
            val mid = (lo + hi) / 2
            if (inBounds(rectPre(t, mid))) lo = mid else hi = mid
        }
        val sB = lo
        var prev = Double.NaN
        for (i in 0 until 36) {
            val s = sB + (0.35 * sB - sB) * i / 35.0
            if (fits(t, s)) {
                if (prev.isNaN()) return s
                var a = s
                var b = prev                     // a feasible, b (larger) infeasible
                repeat(20) {
                    val m = (a + b) / 2
                    if (fits(t, m)) a = m else b = m
                }
                return a
            }
            prev = s
        }
        return 0.0
    }

    var bestS = 0.0
    var bestT: Pt? = null
    for (i in 0 until grid) {
        val dx = (-0.25 + 0.5 * i / (grid - 1)) * spanX
        for (j in 0 until grid) {
            val dy = (-0.25 + 0.5 * j / (grid - 1)) * spanY
            val t = Pt(ctr.x + dx, ctr.y + dy)
            val s = bestSize(t)
            if (s > bestS) {
                bestS = s
                bestT = t
            }
        }
    }
    val t = bestT ?: return null
    return SolveResult(Offsets.fromCorners(rectPre(t, bestS)), bestS, t)
}
