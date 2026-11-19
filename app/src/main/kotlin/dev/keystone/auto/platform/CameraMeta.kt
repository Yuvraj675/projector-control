package dev.keystone.auto.platform

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import dev.keystone.auto.core.Intrinsics
import dev.keystone.auto.core.intrinsicsForJpeg
import dev.keystone.auto.core.intrinsicsFromFocalLength

/** What the solver needs to know about the phone's camera, read once from Camera2. */
data class CameraMeta(
    val fx: Double, val fy: Double, val cx: Double, val cy: Double,
    val activeArray: IntArray,          // left, top, right, bottom
    val sensorOrientation: Int,
    val fromCalibration: Boolean,       // false = derived from focal length and sensor size (less exact)
) {
    fun intrinsicsFor(jpegW: Int, jpegH: Int): Intrinsics = intrinsicsForJpeg(fx, fy, cx, cy, activeArray, jpegW, jpegH)

    override fun equals(other: Any?) = other is CameraMeta && fx == other.fx && fy == other.fy && cx == other.cx && cy == other.cy &&
        activeArray.contentEquals(other.activeArray) && sensorOrientation == other.sensorOrientation

    override fun hashCode() = fx.hashCode() * 31 + sensorOrientation
}

@OptIn(ExperimentalCamera2Interop::class)
fun readCameraMeta(info: CameraInfo): CameraMeta? {
    val c2 = Camera2CameraInfo.from(info)
    val aa = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
    val array = intArrayOf(aa.left, aa.top, aa.right, aa.bottom)
    val orientation = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    val calib = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
    if (calib != null && calib.size >= 4 && calib[0] > 0f && calib[1] > 0f) {
        return CameraMeta(calib[0].toDouble(), calib[1].toDouble(), calib[2].toDouble(), calib[3].toDouble(), array, orientation, true)
    }
    // Fallback: focal length (mm) and physical sensor size give the focal length in pixels.
    val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
    val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    val pixels = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
    val k = intrinsicsFromFocalLength(focal.toDouble(), size.width.toDouble(), size.height.toDouble(), pixels.width, pixels.height, array, pixels.width, pixels.height)
    return CameraMeta(k.fx, k.fy, k.cx, k.cy, array, orientation, false)
}
