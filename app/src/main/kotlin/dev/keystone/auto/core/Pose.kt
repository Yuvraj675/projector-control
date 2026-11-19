package dev.keystone.auto.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Phone pose handling. The camera is a pinhole with intrinsics K; the gravity sensor tells us which way is up in the
 * camera frame. With that we can synthesise the view of a camera that looks horizontally with no roll
 * (a pure rotation, so it is exact for any scene). If the wall is vertical and the camera faces it, that view is
 * square-on and rectangles on the wall are rectangles in it.
 *
 * Camera frame: OpenCV style, x right, y down, z forward (along the optical axis), in *sensor* orientation.
 */
data class Intrinsics(val fx: Double, val fy: Double, val cx: Double, val cy: Double) {
    fun matrix() = Mat3(doubleArrayOf(fx, 0.0, cx, 0.0, fy, cy, 0.0, 0.0, 1.0))
}

/**
 * Intrinsics for a JPEG that is a centred crop of the sensor's active array (Camera2 reports LENS_INTRINSIC_CALIBRATION
 * for the full active array; CameraX crops 4:3 to 16:9).
 */
fun intrinsicsForJpeg(
    fx: Double, fy: Double, cx: Double, cy: Double,
    activeArray: IntArray, // left, top, right, bottom
    jpegW: Int, jpegH: Int,
): Intrinsics {
    val offX = ((activeArray[2] - activeArray[0]) - jpegW) / 2.0
    val offY = ((activeArray[3] - activeArray[1]) - jpegH) / 2.0
    return Intrinsics(fx, fy, cx - offX, cy - offY)
}

/** Fallback when the phone reports no calibration: derive the focal length in pixels from the physical sensor size. */
fun intrinsicsFromFocalLength(
    focalMm: Double, sensorWmm: Double, sensorHmm: Double, pixelW: Int, pixelH: Int,
    activeArray: IntArray, jpegW: Int, jpegH: Int,
): Intrinsics {
    val fx = focalMm / sensorWmm * pixelW
    val fy = focalMm / sensorHmm * pixelH
    return intrinsicsForJpeg(fx, fy, pixelW / 2.0, pixelH / 2.0, activeArray, jpegW, jpegH)
}

/**
 * World-up in the camera's sensor frame. `gravity` is Android's TYPE_GRAVITY reading in device coordinates
 * (x right, y up the screen, z out of the screen; at rest it points *up*). The back camera looks along -z.
 * The sensor frame is the device frame rotated by SENSOR_ORIENTATION.
 */
fun upInSensorFrame(gravity: Vec3, sensorOrientation: Int): Vec3 {
    val c = when (((sensorOrientation % 360) + 360) % 360) {
        0 -> Vec3(gravity.x, -gravity.y, -gravity.z)
        90 -> Vec3(-gravity.y, -gravity.x, -gravity.z)
        180 -> Vec3(-gravity.x, gravity.y, -gravity.z)
        270 -> Vec3(gravity.y, gravity.x, -gravity.z)
        else -> throw IllegalArgumentException("unsupported sensor orientation $sensorOrientation")
    }
    return c.unit()
}

/** Camera elevation above the horizon in degrees (positive = looking up) and roll about the optical axis. */
fun elevationDeg(upCam: Vec3): Double = Math.toDegrees(Math.asin(upCam.z.coerceIn(-1.0, 1.0)))

fun rollDeg(upCam: Vec3): Double = Math.toDegrees(Math.atan2(upCam.x, -upCam.y))

/**
 * Rotation R with x_levelled = R * x_camera: the levelled camera looks horizontally, has no roll, and is then yawed
 * by `yawRad` about the vertical axis.
 */
fun levellingRotation(upCam: Vec3, yawRad: Double = 0.0): Mat3 {
    val up = upCam.unit()
    val down = -up
    val fwd = Vec3(0.0, 0.0, 1.0)
    val f = (fwd - down * fwd.dot(down)).unit()          // horizontal forward: drop the vertical part of the axis
    val right = down.cross(f)
    val r = Mat3.rows(right, down, f)
    val c = cos(yawRad)
    val s = sin(yawRad)
    val ry = Mat3(doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c))
    return ry * r
}

/**
 * Pure-rotation homography taking photo pixels to the pixels of the levelled camera.
 * `rollBiasRad` rotates the measured up vector about the optical axis (a learned correction for a biased sensor).
 */
fun rectifyHomography(k: Intrinsics, upCam: Vec3, yawRad: Double = 0.0, rollBiasRad: Double = 0.0): Mat3 {
    val up = if (rollBiasRad == 0.0) upCam else {
        val c = cos(rollBiasRad)
        val s = sin(rollBiasRad)
        Vec3(upCam.x * c - upCam.y * s, upCam.x * s + upCam.y * c, upCam.z)
    }
    val km = k.matrix()
    return km * levellingRotation(up, yawRad) * km.inverse()
}
