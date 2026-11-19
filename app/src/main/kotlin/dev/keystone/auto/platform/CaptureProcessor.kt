package dev.keystone.auto.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.keystone.auto.core.*
import dev.keystone.auto.vision.DetectResult
import dev.keystone.auto.vision.GrayImage
import dev.keystone.auto.vision.MarkerDetector
import java.io.File
import kotlin.math.abs

/** The result of analysing one photo of the test pattern. */
class CaptureOutcome(
    val measurement: CaptureMeasurement?,   // null if the markers could not be found
    val thumb: Bitmap?,                     // small preview with the detected markers drawn on it
    val elevationDeg: Double,               // phone tilt up/down when the photo was taken
    val rollDeg: Double,                    // phone roll when the photo was taken
    val problem: String?,                   // why the photo cannot be used
    val warning: String?,                   // usable, but the user may want to retake
) {
    val usable get() = measurement != null
}

object CaptureProcessor {
    private const val DETECT_WIDTH = 1500            // decode so the analysis image is at least this wide
    const val LEVEL_WARN_DEG = 3.0

    fun process(file: File, gravityDevice: Vec3, meta: CameraMeta, keystone: Offsets): CaptureOutcome {
        val up = upInSensorFrame(gravityDevice, meta.sensorOrientation)
        val elev = elevationDeg(up)
        val roll = rollDeg(up)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val jw = bounds.outWidth
        val jh = bounds.outHeight
        if (jw <= 0 || jh <= 0) return CaptureOutcome(null, null, elev, roll, "The photo could not be read.", null)
        var sample = 1
        while (jw / (sample * 2) >= DETECT_WIDTH) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return CaptureOutcome(null, null, elev, roll, "The photo could not be decoded.", null)

        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val result = MarkerDetector.detect(GrayImage.fromArgb(px, bmp.width, bmp.height))
        return when (result) {
            is DetectResult.Failed -> CaptureOutcome(
                null, thumb(bmp, emptyList()), elev, roll,
                "Couldn't find all four circles. Make sure the whole picture is in view, the room is dim and nothing bright is behind the wall.", null,
            )
            is DetectResult.Found -> {
                val scale = jw.toDouble() / bmp.width
                val markers = result.points.map { Pt(it.x * scale, it.y * scale) }
                val k = meta.intrinsicsFor(jw, jh)
                val m = CaptureMeasurement(markers, k, up, keystone)
                val edge = 0.015
                val cut = markers.any { it.x < jw * edge || it.x > jw * (1 - edge) || it.y < jh * edge || it.y > jh * (1 - edge) }
                val warn = when {
                    abs(elev) > LEVEL_WARN_DEG || abs(roll) > LEVEL_WARN_DEG -> "The phone was tilted (${"%.1f".format(elev)}° up/down, ${"%.1f".format(roll)}° roll). A level shot is more accurate."
                    cut -> "A circle is very close to the edge of the photo. Step back a little."
                    else -> null
                }
                CaptureOutcome(m, thumb(bmp, result.points), elev, roll, null, warn)
            }
        }
    }

    private fun thumb(src: Bitmap, points: List<Pt>): Bitmap {
        val w = minOf(640, src.width)
        val scale = w.toFloat() / src.width
        val out = Bitmap.createScaledBitmap(src, w, (src.height * scale).toInt(), true).copy(Bitmap.Config.ARGB_8888, true)
        if (points.size == 4) {
            val c = Canvas(out)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(53, 224, 200); style = Paint.Style.STROKE; strokeWidth = 3f }
            val ordered = orderTlTrBrBl(points)
            for (i in 0..3) {
                val a = ordered[i]; val b = ordered[(i + 1) % 4]
                c.drawLine((a.x * scale).toFloat(), (a.y * scale).toFloat(), (b.x * scale).toFloat(), (b.y * scale).toFloat(), line)
            }
            for (p in ordered) c.drawCircle((p.x * scale).toFloat(), (p.y * scale).toFloat(), 14f, line)
        }
        return out
    }
}
