package dev.keystone.auto

import dev.keystone.auto.core.*
import dev.keystone.auto.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class LearningTest {
    private val k = intrinsicsForJpeg(2801.094, 2801.094, 2048.0, 1536.0, intArrayOf(0, 0, 4096, 3072), 4096, 2304)

    private fun randomKeystone(rnd: Random) = Offsets(
        rnd.nextInt(60, 130).toDouble(), rnd.nextInt(0, 60).toDouble(), rnd.nextInt(60, 130).toDouble(), rnd.nextInt(0, 40).toDouble(),
        rnd.nextInt(0, 20).toDouble(), rnd.nextInt(30, 80).toDouble(), rnd.nextInt(0, 30).toDouble(), rnd.nextInt(40, 100).toDouble())

    /** Hand-held shots from the same spot: pose jitters a little, the wall-facing yaw and sensor bias are systematic. */
    private fun shots(scene: SimScene, keystone: Offsets, rnd: Random, n: Int = 4, yaw: Double, rollBias: Double) =
        List(n) { scene.capture(SimCamera(k, rnd.nextDouble(-1.5, 1.5), rnd.nextDouble(-1.0, 1.0), yaw + rnd.nextDouble(-0.6, 0.6)), keystone, rnd, rollBias) }

    @Test
    fun learningFromAnAcceptedPictureImprovesFutureResults() {
        val rnd = Random(21)
        val trueYaw = 6.0; val bias = 0.5
        val scene = SimScene(k, rnd)
        // session 1: the user accepts a picture that really is a clean rectangle on the wall
        val cur1 = randomKeystone(rnd)
        val accepted = scene.idealOffsets(cur1, 1.70)
        val captures = shots(scene, cur1, rnd, 4, trueYaw, bias)
        val learned = Learning.learnFromAccepted(Profile(), captures, accepted)
        println("learned params: ${learned.params}")
        assertEquals(1.70, learned.params.aspect, 0.02)
        assertEquals("yaw magnitude", trueYaw, abs(learned.params.yawDeg), 1.2)

        // session 2: fresh photos at a different keystone; compare default vs learned parameters on the wall
        var wins = 0
        repeat(6) {
            val cur2 = randomKeystone(rnd)
            val fresh = shots(scene, cur2, rnd, 4, trueYaw, bias)
            val def = Learning.solveAll(fresh, Params())!!.offsets
            val lrn = Learning.solveAll(fresh, learned.params)!!.offsets
            val qd = scene.wallQuality(def); val ql = scene.wallQuality(lrn)
            val errD = abs(qd.rightOverLeft - 1) + qd.tiltDeg / 10; val errL = abs(ql.rightOverLeft - 1) + ql.tiltDeg / 10
            println("default err %.4f (edge ratio %.3f, tilt %.2f)  learned err %.4f (edge ratio %.3f, tilt %.2f)".format(errD, qd.rightOverLeft, qd.tiltDeg, errL, ql.rightOverLeft, ql.tiltDeg))
            if (errL < errD) wins++
            assertTrue("learned edge ratio ${ql.rightOverLeft}", abs(ql.rightOverLeft - 1) < 0.02)
        }
        assertEquals("learning should help every time", 6, wins)
    }

    @Test
    fun edgeFeedbackConvergesOnTheWall() {
        val rnd = Random(33)
        val scene = SimScene(k, rnd)
        val cur = randomKeystone(rnd)
        val captures = shots(scene, cur, rnd, 4, yaw = 7.0, rollBias = 0.0)
        var profile = Profile()
        var ratio = scene.wallQuality(Learning.solveAll(captures, profile.params)!!.offsets).rightOverLeft
        println("start edge ratio %.3f".format(ratio))
        val start = abs(ratio - 1)
        assertTrue("scenario should start visibly off", start > 0.03)
        var last: Feedback? = null; var scale = 1.0; var taps = 0
        repeat(8) {
            if (abs(ratio - 1) < 0.01) return@repeat
            val fb = if (ratio < 1) Feedback.RIGHT_EDGE_SHORT else Feedback.LEFT_EDGE_SHORT
            if (last != null && last != fb) scale = maxOf(0.25, scale / 2)        // what the UI does on a reversal
            last = fb; taps++
            val shown = Learning.solveAll(captures, profile.params)!!.offsets
            profile = Learning.adjust(profile, captures, shown, fb, scale)
            ratio = scene.wallQuality(Learning.solveAll(captures, profile.params)!!.offsets).rightOverLeft
            println("after $fb: yaw %.1f edge ratio %.3f".format(profile.params.yawDeg, ratio))
        }
        assertTrue("took $taps taps", taps <= 5)
        assertTrue("edge ratio ended at $ratio", abs(ratio - 1) < 0.015)
        assertEquals(7.0, abs(profile.params.yawDeg), 1.5)
    }

    @Test
    fun tiltFeedbackConvergesOnTheWall() {
        val rnd = Random(44)
        val scene = SimScene(k, rnd)
        val cur = randomKeystone(rnd)
        val captures = shots(scene, cur, rnd, 4, yaw = 0.0, rollBias = 1.2)         // a sensor that reads 1.2 deg off
        var profile = Profile()
        fun wallSlope() = scene.wallQuality(Learning.solveAll(captures, profile.params)!!.offsets).let { (it.topDeg + it.bottomDeg) / 2 }
        var slope = wallSlope()
        println("start slope %.2f".format(slope))
        assertTrue("scenario should start visibly tilted", abs(slope) > 0.7)
        var last: Feedback? = null; var scale = 1.0
        repeat(8) {
            if (abs(slope) < 0.2) return@repeat
            val fb = if (slope < 0) Feedback.RIGHT_SIDE_HIGH else Feedback.LEFT_SIDE_HIGH       // negative angle = right side higher
            if (last != null && last != fb) scale = maxOf(0.25, scale / 2)
            last = fb
            val shown = Learning.solveAll(captures, profile.params)!!.offsets
            profile = Learning.adjust(profile, captures, shown, fb, scale)
            slope = wallSlope()
            println("after $fb: roll bias %.1f slope %.2f".format(profile.params.rollBiasDeg, slope))
        }
        assertTrue("slope ended at $slope", abs(slope) < 0.3)
    }

    @Test
    fun aspectFeedbackMovesTheAspect() {
        val rnd = Random(55)
        val scene = SimScene(k, rnd)
        val captures = shots(scene, randomKeystone(rnd), rnd, 3, 0.0, 0.0)
        val p = Profile()
        val shown = Learning.solveAll(captures, p.params)!!.offsets
        assertTrue(Learning.adjust(p, captures, shown, Feedback.TOO_WIDE).params.aspect < p.params.aspect)
        assertTrue(Learning.adjust(p, captures, shown, Feedback.TOO_TALL).params.aspect > p.params.aspect)
        assertEquals(1, Learning.adjust(p, captures, shown, Feedback.TOO_WIDE).rejected)
    }

    @Test
    fun profileAndHistoryRoundTripThroughJson() {
        val p = Profile(Params(1.68, -2.5, 0.3), accepted = 3, rejected = 1)
        assertEquals(p, Codec.profileFromJson(Codec.profileToJson(p)))
        assertEquals(Profile(), Codec.profileFromJson("not json"))
        val h = HistoryEntry(1234L, "proj-1", Offsets(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0), Offsets(8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0),
            Params(), 4, 5.5, "accepted", "ok")
        assertEquals(listOf(h), Codec.historyListFromJson(Codec.historyListToJson(listOf(h))))
        assertTrue(Codec.historyListFromJson("garbage").isEmpty())
    }
}
