package dev.keystone.auto.core

import kotlin.math.abs

/** Tunable parameters. `aspect` and the two biases are what the app learns from the user's feedback. */
data class Params(
    val aspect: Double = 1.70,       // target picture width / height in the square-on view
    val yawDeg: Double = 0.0,        // assumed camera heading relative to the wall normal
    val rollBiasDeg: Double = 0.0,   // correction added to the phone's roll reading
) {
    val yawRad get() = Math.toRadians(yawDeg)
    val rollBiasRad get() = Math.toRadians(rollBiasDeg)
}

/** Everything measured from one photo of the test pattern. */
data class CaptureMeasurement(
    val markers: List<Pt>,        // the four marker centres in JPEG pixels (any order)
    val intrinsics: Intrinsics,
    val upCam: Vec3,              // world-up in the camera frame, from the gravity sensor
    val keystone: Offsets,        // the projector's corner offsets while the photo was taken
)

/** The four markers in the levelled (square-on) view, ordered TL, TR, BR, BL. */
fun levelledMarkers(m: CaptureMeasurement, p: Params): List<Pt> {
    val hr = rectifyHomography(m.intrinsics, m.upCam, p.yawRad, p.rollBiasRad)
    return orderTlTrBrBl(warp(hr, m.markers))
}

fun solveCapture(m: CaptureMeasurement, p: Params): SolveResult? = solveKeystone(m.keystone, levelledMarkers(m, p), p.aspect)

/** The picture as the levelled camera sees it while `offsets` are active, from this capture's marker homography. */
fun pictureQuad(m: CaptureMeasurement, p: Params, offsets: Offsets): List<Pt> {
    val markersPanel = warp(homography(Panel.frame, m.keystone.corners()), Panel.markers)
    val hPw = homography(markersPanel, levelledMarkers(m, p))
    return warp(hPw, offsets.corners())
}

/** Shape of the picture currently on the wall, as seen in the levelled view. */
fun measureCurrentShape(m: CaptureMeasurement, p: Params): RectQuality = rectQuality(pictureQuad(m, p, m.keystone))

data class Combined(
    val offsets: Offsets,           // rounded, clamped
    val used: List<Int>,            // indices of the captures that were averaged
    val dropped: List<Int>,         // indices rejected as outliers
    val spread: Double,             // largest per-value standard deviation among the used captures (pixels)
)

/**
 * Average per-capture solutions after rejecting gross outliers (needs >= 3 captures to reject anything).
 * Hand-held shots scatter by roughly +-10 px per value, which is normal and gets averaged out; only a capture whose
 * summed deviation from the median solution is far beyond that (a bad detection, a badly tilted phone) is dropped.
 * Calibrated on real captures: the four shots of the accepted setting deviate by 12..71 px in total.
 */
fun combine(results: List<Offsets>): Combined {
    require(results.isNotEmpty())
    val vals = results.map { it.toList() }
    val n = vals.size
    val median = (0 until 8).map { k -> vals.map { it[k] }.sorted().let { s -> if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 } }
    val dist = vals.map { v -> (0 until 8).sumOf { abs(v[it] - median[it]) } }
    val medDist = dist.sorted().let { s -> if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 }
    val keep = if (n >= 3) dist.indices.filter { dist[it] <= maxOf(100.0, 4.0 * medDist) } else dist.indices.toList()
    val used = if (keep.isEmpty()) dist.indices.toList() else keep
    val mean = (0 until 8).map { k -> used.sumOf { vals[it][k] } / used.size }
    val spread = (0 until 8).maxOf { k ->
        val mu = mean[k]
        Math.sqrt(used.sumOf { (vals[it][k] - mu) * (vals[it][k] - mu) } / used.size)
    }
    return Combined(Offsets.fromList(mean).rounded().clamped(), used, dist.indices.filter { it !in used }, spread)
}
