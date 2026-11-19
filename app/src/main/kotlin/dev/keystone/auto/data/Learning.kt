package dev.keystone.auto.data

import dev.keystone.auto.core.*
import kotlin.math.abs
import kotlin.math.ln

/** What the user can say is wrong when the keystone is not right yet. */
enum class Feedback(val label: String) {
    RIGHT_EDGE_SHORT("Right edge looks too short"),
    LEFT_EDGE_SHORT("Left edge looks too short"),
    RIGHT_SIDE_HIGH("Picture slopes up to the right"),
    LEFT_SIDE_HIGH("Picture slopes up to the left"),
    TOO_WIDE("Picture looks too wide / not tall enough"),
    TOO_TALL("Picture looks too tall / stretched");
}

/** The parameters the app has learned, plus how much evidence is behind them. */
data class Profile(
    val params: Params = Params(),
    val accepted: Int = 0,
    val rejected: Int = 0,
) {
    companion object {
        const val ASPECT_MIN = 1.55
        const val ASPECT_MAX = 1.80
        const val YAW_LIMIT = 20.0
        const val ROLL_LIMIT = 3.0
    }
}

object Learning {
    private fun shape(m: CaptureMeasurement, p: Params, offsets: Offsets) = rectQuality(pictureQuad(m, p, offsets))

    private fun argmin(lo: Double, hi: Double, step: Double, f: (Double) -> Double): Double {
        var best = lo
        var bestV = Double.MAX_VALUE
        var x = lo
        while (x <= hi + 1e-9) {
            val v = f(x)
            if (v < bestV) { bestV = v; best = x }
            x += step
        }
        return best
    }

    private fun mean(v: List<Double>) = v.sum() / v.size

    /**
     * The user said "this looks correct" for `accepted`. Treat that picture as the ground-truth shape: find the
     * parameters under which it is a clean rectangle in the levelled view of every capture, and blend them in.
     * `captures` may be the photos taken before applying (the accepted picture is predicted in each) and/or a result
     * photo taken afterwards (measured directly): both work because the predicted quad uses each capture's own state.
     */
    fun learnFromAccepted(profile: Profile, captures: List<CaptureMeasurement>, accepted: Offsets): Profile {
        if (captures.isEmpty()) return profile.copy(accepted = profile.accepted + 1)
        var p = profile.params
        repeat(3) {
            val yaw = argmin(-Profile.YAW_LIMIT, Profile.YAW_LIMIT, 0.25) { y ->
                mean(captures.map { abs(ln(shape(it, p.copy(yawDeg = y), accepted).rightOverLeft)) })
            }
            p = p.copy(yawDeg = yaw)
            val roll = argmin(-Profile.ROLL_LIMIT, Profile.ROLL_LIMIT, 0.1) { r ->
                mean(captures.map { val q = shape(it, p.copy(rollBiasDeg = r), accepted); abs((q.topDeg + q.bottomDeg) / 2) })
            }
            p = p.copy(rollBiasDeg = roll)
        }
        val aspect = mean(captures.map { shape(it, p, accepted).aspect }).coerceIn(Profile.ASPECT_MIN, Profile.ASPECT_MAX)
        val target = p.copy(aspect = aspect)
        val a = if (profile.accepted == 0) 1.0 else 0.5            // the first acceptance sets the baseline, later ones refine it
        val old = profile.params
        val blended = Params(
            aspect = old.aspect + a * (target.aspect - old.aspect),
            yawDeg = old.yawDeg + a * (target.yawDeg - old.yawDeg),
            rollBiasDeg = old.rollBiasDeg + a * (target.rollBiasDeg - old.rollBiasDeg),
        )
        return profile.copy(params = blended, accepted = profile.accepted + 1)
    }

    /** Solves every capture under `p` and combines them (the same path the app takes). */
    fun solveAll(captures: List<CaptureMeasurement>, p: Params): Combined? {
        val sols = captures.mapNotNull { solveCapture(it, p)?.offsets }
        return if (sols.isEmpty()) null else combine(sols)
    }

    /**
     * "Not right yet": nudge the parameter behind the complaint. The direction is chosen by trying both signs and
     * keeping the one that moves the predicted picture (seen in the *current* levelled view) the way the user asked.
     * `scale` shrinks the step: the UI halves it when the user reverses direction, so it homes in instead of oscillating.
     */
    fun adjust(profile: Profile, captures: List<CaptureMeasurement>, current: Offsets, feedback: Feedback, scale: Double = 1.0): Profile {
        val p = profile.params
        fun measure(q: Params, f: (RectQuality) -> Double): Double? {
            val c = solveAll(captures, q) ?: return null
            return mean(captures.map { f(shape(it, p, c.offsets)) })      // evaluated in the view the user was looking at
        }
        val next: Params = when (feedback) {
            Feedback.TOO_WIDE -> p.copy(aspect = (p.aspect - 0.03 * scale).coerceIn(Profile.ASPECT_MIN, Profile.ASPECT_MAX))
            Feedback.TOO_TALL -> p.copy(aspect = (p.aspect + 0.03 * scale).coerceIn(Profile.ASPECT_MIN, Profile.ASPECT_MAX))
            Feedback.RIGHT_EDGE_SHORT, Feedback.LEFT_EDGE_SHORT -> {
                val wantLonger = if (feedback == Feedback.RIGHT_EDGE_SHORT) 1.0 else -1.0      // + = raise right/left
                pickDirection(p, { q, d -> q.copy(yawDeg = (q.yawDeg + d).coerceIn(-Profile.YAW_LIMIT, Profile.YAW_LIMIT)) }, 2.0 * scale) { q ->
                    measure(q) { wantLonger * ln(it.rightOverLeft) }
                }
            }
            Feedback.RIGHT_SIDE_HIGH, Feedback.LEFT_SIDE_HIGH -> {
                // right side high means the top/bottom edges have a negative angle (image y grows downwards)
                val wantAngle = if (feedback == Feedback.RIGHT_SIDE_HIGH) 1.0 else -1.0         // fix by increasing / decreasing the angle
                pickDirection(p, { q, d -> q.copy(rollBiasDeg = (q.rollBiasDeg + d).coerceIn(-Profile.ROLL_LIMIT, Profile.ROLL_LIMIT)) }, 0.6 * scale) { q ->
                    measure(q) { wantAngle * (it.topDeg + it.bottomDeg) / 2 }
                }
            }
        }
        return profile.copy(params = next, rejected = profile.rejected + 1)
    }

    /** Tries p moved by +step and -step; returns whichever raises `score` (the one that does not changes nothing). */
    private fun pickDirection(p: Params, move: (Params, Double) -> Params, step: Double, score: (Params) -> Double?): Params {
        val base = score(p)
        val up = score(move(p, +step))
        val down = score(move(p, -step))
        if (base == null || up == null || down == null) return p
        return when {
            up > base && up >= down -> move(p, +step)
            down > base -> move(p, -step)
            else -> p
        }
    }
}
