package dev.keystone.auto.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the phone's rotation into pointer movement, like a Magic Remote.
 *
 * Device axes: x to the right, y to the top, z out of the screen. The gyroscope reports angular velocity about them
 * (rad/s, counter-clockwise positive). `up` is the gravity-sensor vector, which points up in the device frame.
 *
 * The pointing direction is the top edge of the phone when it is held flat or tilted like a TV remote, and the back of
 * the phone when it is held upright (screen facing you) - so it works however the phone is held. Turning right moves the
 * pointer right, raising the tip moves it up.
 */
object AirMouse {
    /** Pixels the pointer moves per radian of rotation at speed 1.0 (about 55 degrees sweeps the 1280 px panel). */
    const val PIXELS_PER_RADIAN = 1300.0

    /** Rotation slower than this is hand tremor, not intent (rad/s). */
    const val DEADZONE = 0.02

    data class Move(val dx: Double, val dy: Double)

    fun pointingAxis(up: Vec3): Vec3 {
        val u = up.unit()
        return if (abs(u.y) > 0.8) Vec3(0.0, 0.0, -1.0) else Vec3(0.0, 1.0, 0.0)
    }

    fun step(omega: Vec3, up: Vec3, dtSeconds: Double, speed: Double = 1.0): Move {
        if (up.norm() < 1e-6) return Move(0.0, 0.0)
        val u = up.unit()
        val forward = pointingAxis(u)
        val right = forward.cross(u).unit()
        val yaw = omega.dot(u)          // about the vertical: negative when turning right
        val pitch = omega.dot(right)    // about the right-hand axis: positive when the tip rises
        fun dead(v: Double) = if (abs(v) < DEADZONE) 0.0 else v - Math.copySign(DEADZONE, v)
        val k = PIXELS_PER_RADIAN * speed * dtSeconds
        return Move(-dead(yaw) * k, -dead(pitch) * k)
    }
}

/** Touchpad pointer acceleration: slow strokes are precise, fast strokes cross the screen. */
object PadMotion {
    /** Multiplier for a finger moving at [speedPxPerSec] (screen pixels of the phone). */
    fun gain(speedPxPerSec: Double, base: Double = 1.0): Double {
        val s = max(0.0, speedPxPerSec)
        val boost = 1.0 + 1.6 * (s / (s + 900.0))          // 1.0 .. 2.6
        return base * 1.4 * boost
    }
}
