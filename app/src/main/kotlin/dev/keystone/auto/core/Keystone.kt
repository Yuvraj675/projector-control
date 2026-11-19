package dev.keystone.auto.core

/**
 * Keystone model of the projector (verified against screenshots and wall photos of the real device).
 *
 * The framebuffer is 1280x720. The keystone is a homography that moves the four frame corners to the positions
 * given by the TV service's corner offsets (pixels measured inward):
 *   TL = (X1, Y1)   TR = (1280-X2, Y2)   BL = (X3, 720-Y3)   BR = (1280-X4, 720-Y4)
 */
object Panel {
    const val W = 1280.0
    const val H = 720.0

    /** Frame corners TL, TR, BR, BL. */
    val frame = listOf(Pt(0.0, 0.0), Pt(W, 0.0), Pt(W, H), Pt(0.0, H))

    /**
     * Test-pattern marker centres as fractions of the frame: TL, TR, BR, BL. BR sits left of the corner so the
     * firmware's mute indicator (bottom right) never covers it. Keep in sync with the pattern app.
     */
    val markerFractions = listOf(0.075 to 0.075, 0.925 to 0.075, 0.85 to 0.925, 0.075 to 0.925)
    val markers = markerFractions.map { Pt(it.first * W, it.second * H) }

    /** Sanity limits for any value ever written to the projector. */
    const val MAX_X = 400.0
    const val MAX_Y = 220.0
}

/** The eight corner offsets, in the order X1, Y1, X2, Y2, X3, Y3, X4, Y4. */
data class Offsets(
    val x1: Double, val y1: Double, val x2: Double, val y2: Double,
    val x3: Double, val y3: Double, val x4: Double, val y4: Double,
) {
    fun toList() = listOf(x1, y1, x2, y2, x3, y3, x4, y4)

    fun toMap(): Map<String, Int> = KEYS.zip(toList()).associate { it.first to Math.round(it.second).toInt() }

    fun rounded() = fromList(toList().map { Math.round(it).toDouble() })

    /** Clamp into the range the projector accepts. */
    fun clamped() = fromList(toList().mapIndexed { i, v -> v.coerceIn(0.0, if (i % 2 == 0) Panel.MAX_X else Panel.MAX_Y) })

    fun withinLimits(): Boolean = toList().withIndex().all { (i, v) -> v >= 0.0 && v <= (if (i % 2 == 0) Panel.MAX_X else Panel.MAX_Y) }

    /** Corner positions TL, TR, BR, BL in panel pixels. */
    fun corners() = listOf(Pt(x1, y1), Pt(Panel.W - x2, y2), Pt(Panel.W - x4, Panel.H - y4), Pt(x3, Panel.H - y3))

    /** Fraction of the panel area the picture covers. */
    fun panelUse() = polygonArea(corners()) / (Panel.W * Panel.H)

    companion object {
        val KEYS = listOf("X1", "Y1", "X2", "Y2", "X3", "Y3", "X4", "Y4")

        fun fromList(v: List<Double>) = Offsets(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])

        fun fromMap(m: Map<String, Int>) = fromList(KEYS.map { m.getValue(it).toDouble() })

        fun fromCorners(c: List<Pt>): Offsets {
            val (tl, tr, br, bl) = c
            return Offsets(tl.x, tl.y, Panel.W - tr.x, tr.y, bl.x, Panel.H - bl.y, Panel.W - br.x, Panel.H - br.y)
        }

        val NONE = Offsets(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
