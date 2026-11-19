package dev.keystone.auto.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.keystone.auto.core.AirMouse
import dev.keystone.auto.core.Vec3

/** Reads the gyroscope and turns phone rotation into pointer movement (the maths is in [AirMouse]). */
class GyroPointer(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravity = GravityTracker(context)
    private var lastNs = 0L
    @Volatile var speed = 1.0
    var onMove: ((Double, Double) -> Unit)? = null

    val available get() = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null && gravity.available

    fun start() {
        val g = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        lastNs = 0L
        gravity.start()
        sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
        gravity.stop()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val last = lastNs
        lastNs = e.timestamp
        if (last == 0L) return
        val dt = (e.timestamp - last) / 1e9
        if (dt <= 0.0 || dt > 0.2) return
        val up = gravity.average(4) ?: return
        val m = AirMouse.step(Vec3(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()), up, dt, speed)
        if (m.dx != 0.0 || m.dy != 0.0) onMove?.invoke(m.dx, m.dy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
