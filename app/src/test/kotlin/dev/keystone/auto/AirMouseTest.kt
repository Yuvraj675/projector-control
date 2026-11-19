package dev.keystone.auto

import dev.keystone.auto.core.AirMouse
import dev.keystone.auto.core.PadMotion
import dev.keystone.auto.core.Vec3
import org.junit.Assert.*
import org.junit.Test

class AirMouseTest {
    private val flatUp = Vec3(0.0, 0.0, 9.81)        // lying flat, screen up: gravity sensor reads +z
    private val uprightUp = Vec3(0.0, 9.81, 0.0)     // held upright facing you: +y

    @Test fun turningRightMovesThePointerRight() {
        // clockwise seen from above = negative rotation about the up axis
        val m = AirMouse.step(Vec3(0.0, 0.0, -0.5), flatUp, 0.01)
        assertTrue(m.dx > 0); assertEquals(0.0, m.dy, 1e-9)
    }

    @Test fun raisingTheTipMovesThePointerUp() {
        // flat phone, forward = +y, right = +x; tipping the top edge up is a positive rotation about +x
        val m = AirMouse.step(Vec3(0.5, 0.0, 0.0), flatUp, 0.01)
        assertTrue(m.dy < 0); assertEquals(0.0, m.dx, 1e-9)
    }

    @Test fun worksWhenThePhoneIsHeldUprightToo() {
        // upright: up = +y, pointing out of the back (-z), right = +x. Turn right = negative about +y.
        val turn = AirMouse.step(Vec3(0.0, -0.5, 0.0), uprightUp, 0.01)
        assertTrue(turn.dx > 0); assertEquals(0.0, turn.dy, 1e-9)
        // raise the back edge: forward(-z) swings toward up(+y), which is a positive rotation about +x
        val tilt = AirMouse.step(Vec3(0.5, 0.0, 0.0), uprightUp, 0.01)
        assertTrue(tilt.dy < 0)
    }

    @Test fun tremorIsIgnoredAndSpeedScales() {
        val still = AirMouse.step(Vec3(0.01, 0.0, 0.01), flatUp, 0.01)
        assertEquals(0.0, still.dx, 0.0); assertEquals(0.0, still.dy, 0.0)
        val slow = AirMouse.step(Vec3(0.0, 0.0, -0.5), flatUp, 0.01, speed = 1.0)
        val fast = AirMouse.step(Vec3(0.0, 0.0, -0.5), flatUp, 0.01, speed = 2.0)
        assertEquals(2.0, fast.dx / slow.dx, 1e-9)
        assertEquals(0.0, AirMouse.step(Vec3(1.0, 1.0, 1.0), Vec3(0.0, 0.0, 0.0), 0.01).dx, 0.0)
    }

    @Test fun aboutFiftyFiveDegreesCrossesThePanel() {
        val rad = Math.toRadians(55.0)
        val m = AirMouse.step(Vec3(0.0, 0.0, -rad / 0.5), flatUp, 0.5)     // 55 degrees over half a second
        assertEquals(1280.0, m.dx, 90.0)
    }

    @Test fun touchpadAcceleratesWithSpeed() {
        assertTrue(PadMotion.gain(2000.0) > PadMotion.gain(100.0) * 1.5)
        assertTrue(PadMotion.gain(0.0) > 1.0)
    }
}
