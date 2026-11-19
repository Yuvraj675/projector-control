package dev.keystone.auto.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.keystone.auto.core.Vec3
import kotlin.math.sqrt

/** Averages the phone's gravity sensor over the last moments and tells whether the phone is being held still. */
class GravityTracker(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val samples = ArrayDeque<Vec3>()
    var onSample: (() -> Unit)? = null

    val available: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) != null || sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun start() {
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
        synchronized(samples) { samples.clear() }
    }

    override fun onSensorChanged(e: SensorEvent) {
        synchronized(samples) {
            samples.addLast(Vec3(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()))
            while (samples.size > 30) samples.removeFirst()
        }
        onSample?.invoke()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Mean of the most recent `n` samples (about half a second at the game rate), or null if none yet. */
    fun average(n: Int = 20): Vec3? = synchronized(samples) {
        if (samples.isEmpty()) return null
        val last = samples.toList().takeLast(n)
        Vec3(last.sumOf { it.x } / last.size, last.sumOf { it.y } / last.size, last.sumOf { it.z } / last.size)
    }

    /** True when the last samples barely vary: the phone is held still. */
    fun steady(n: Int = 15, limit: Double = 0.07): Boolean = synchronized(samples) {
        if (samples.size < n) return false
        val last = samples.toList().takeLast(n)
        val mx = last.sumOf { it.x } / n
        val my = last.sumOf { it.y } / n
        val mz = last.sumOf { it.z } / n
        val v = last.sumOf { (it.x - mx) * (it.x - mx) + (it.y - my) * (it.y - my) + (it.z - mz) * (it.z - mz) } / n
        sqrt(v) < limit
    }
}
