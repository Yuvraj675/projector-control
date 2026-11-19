package dev.keystone.auto

import dev.keystone.auto.core.*
import dev.keystone.auto.vision.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Cross-checks the Kotlin pipeline against the validated Python prototype on real photos.
 * The photos show the owner's room, so they are never committed: set KS_CAPTURES to a folder holding
 * keystone_*.jpg / .json / .keystone.json, otherwise this test is skipped.
 */
class RealCapturesTest {
    private val dir: File? = (System.getenv("KS_CAPTURES") ?: "../../autokeystone/captures").let { File(it) }.takeIf { it.isDirectory }

    // Per-capture solutions at aspect 1.70, yaw 0 from the Python prototype (solver.py)
    private val golden = mapOf(
        "keystone_20261120_025142" to listOf(105, 26, 103, 7, 1, 53, 9, 84),
        "keystone_20261120_025150" to listOf(102, 35, 110, 1, 3, 64, 14, 78),
        "keystone_20261120_030119" to listOf(100, 52, 110, 16, 1, 46, 14, 54),
        "keystone_20261120_030129" to listOf(105, 30, 105, 5, 1, 62, 14, 78),
    )

    /** The Android unit-test classpath lacks javax.imageio, but the JDK provides it at runtime: use reflection. */
    private fun decodeJpeg(f: File): Triple<IntArray, Int, Int> {
        val imageIo = Class.forName("javax.imageio.ImageIO")
        val img = imageIo.getMethod("read", File::class.java).invoke(null, f)!!
        val c = img.javaClass
        val w = c.getMethod("getWidth").invoke(img) as Int
        val h = c.getMethod("getHeight").invoke(img) as Int
        val px = IntArray(w * h)
        c.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(img, 0, 0, w, h, px, 0, w)
        return Triple(px, w, h)
    }

    private fun measure(base: String): CaptureMeasurement {
        val d = dir!!
        val meta = JSONObject(File(d, "$base.json").readText())
        val ks = JSONObject(File(d, "$base.keystone.json").readText()).getJSONObject("offsets")
        val (px, iw, ih) = decodeJpeg(File(d, "$base.jpg"))
        val gray = GrayImage.fromArgb(px, iw, ih).downscale(2)
        val res = MarkerDetector.detect(gray)
        assertTrue("$base: ${(res as? DetectResult.Failed)?.reason}", res is DetectResult.Found)
        val pts = (res as DetectResult.Found).points.map { Pt(it.x * 2, it.y * 2) }

        val cam = meta.getJSONObject("camera")
        val intr = cam.getJSONArray("lens_intrinsic_fx_fy_cx_cy_s")
        val aa = cam.getJSONArray("active_array").let { intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }
        val k = intrinsicsForJpeg(intr.getDouble(0), intr.getDouble(1), intr.getDouble(2), intr.getDouble(3), aa,
            meta.getJSONObject("image").getInt("width"), meta.getJSONObject("image").getInt("height"))
        val g = meta.getJSONArray("gravity_avg")
        val up = upInSensorFrame(Vec3(g.getDouble(0), g.getDouble(1), g.getDouble(2)), cam.getInt("sensor_orientation"))
        val keystone = Offsets.fromMap(Offsets.KEYS.associateWith { ks.getInt(it) })
        return CaptureMeasurement(pts, k, up, keystone)
    }

    @Test
    fun kotlinPipelineMatchesThePythonPrototypeOnRealPhotos() {
        assumeTrue("no local captures", dir != null && golden.keys.all { File(dir, "$it.jpg").exists() })
        val results = ArrayList<Offsets>()
        for ((base, want) in golden) {
            val m = measure(base)
            val res = solveCapture(m, Params(aspect = 1.70))!!
            val got = res.offsets.rounded().toList().map { it.toInt() }
            println("$base: kotlin=$got python=$want")
            for (i in 0 until 8) assertTrue("$base value $i: kotlin ${got[i]} vs python ${want[i]}", Math.abs(got[i] - want[i]) <= 3)
            results += res.offsets
        }
        val combined = combine(results)
        println("combined: ${combined.offsets.toMap()} spread %.1f".format(combined.spread))
        // the setting the user accepted as "perfect" was the rounded mean of these four solutions
        assertEquals(36.0, combined.offsets.y1, 3.0)
        assertEquals(74.0, combined.offsets.y4, 3.0)
    }
}
