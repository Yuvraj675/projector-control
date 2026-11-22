package dev.keystone.auto.core

/**
 * The corrected view that manual keystone is edited in.
 *
 * The keystone that is on the projector when the screen opens ([base]) is taken to make the picture look right: a plain
 * rectangle on the wall. A homography is fixed by four points, so mapping the base picture's four corners (in panel pixels)
 * onto the corners of the full rectangle gives the panel-to-view mapping, and view coordinates are then "what the viewer
 * sees". Dragging a corner to a new place in that view means putting the panel corner at the same place mapped back, so
 * the corner follows the finger one to one, the other three stay where they are on the wall, and the whole picture warps
 * like a perspective tool in a photo editor. View units are the panel's own (1280x720 is the whole, undistorted frame).
 */
class TuneView(val base: Offsets) {
    private val toView: Mat3 = runCatching { homography(base.corners(), Panel.frame) }.getOrElse { Mat3.identity() }
    private val toPanel: Mat3 = runCatching { toView.inverse() }.getOrElse { Mat3.identity() }

    fun viewOf(panel: Pt): Pt = toView.apply(panel)
    fun panelOf(view: Pt): Pt = toPanel.apply(view)

    /** The picture's corners (TL, TR, BR, BL) in the view for the given offsets. */
    fun corners(o: Offsets): List<Pt> = o.corners().map { viewOf(it) }

    /** [cur] with corner [i] moved to [view] (clamped to what the projector accepts). Unusable points leave it unchanged. */
    fun moveCorner(cur: Offsets, i: Int, view: Pt): Offsets {
        val p = panelOf(view)
        return if (p.x.isFinite() && p.y.isFinite()) cur.withCorner(i, p) else cur
    }

    /**
     * Corner [i] moved one step along [dir] (a unit vector in the view). The step is sized so the projector's corner moves
     * about [panelPx] of its own pixels, because the projector only takes whole pixels and a step must never round away.
     */
    fun step(cur: Offsets, i: Int, dir: Pt, panelPx: Double): Offsets {
        val at = viewOf(cur.corners()[i])
        val len = panelOf(at).dist(panelOf(at + dir))
        if (!len.isFinite() || len < 1e-9) return cur
        return moveCorner(cur, i, at + dir * (panelPx / len))
    }

    /** Where each corner can go, as polygons in the view. */
    val reach: List<List<Pt>> = (0..3).map { i ->
        val b = Panel.cornerBox(i)
        listOf(Pt(b.left, b.top), Pt(b.right, b.top), Pt(b.right, b.bottom), Pt(b.left, b.bottom)).map { viewOf(it) }
    }

    /** The area the editor draws: the whole frame and everything a corner can reach, kept to a sensible size. */
    val bounds: Box = run {
        val pts = Panel.frame + reach.flatten()
        val minX = pts.minOf { it.x }.coerceIn(-0.6 * Panel.W, 0.0)
        val maxX = pts.maxOf { it.x }.coerceIn(Panel.W, 1.6 * Panel.W)
        val minY = pts.minOf { it.y }.coerceIn(-0.6 * Panel.H, 0.0)
        val maxY = pts.maxOf { it.y }.coerceIn(Panel.H, 1.6 * Panel.H)
        Box(minX, minY, maxX, maxY)
    }
}
