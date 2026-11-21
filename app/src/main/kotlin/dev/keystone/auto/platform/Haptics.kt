package dev.keystone.auto.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibration straight from the phone's vibrator. Compose's own haptic call goes through the system "touch feedback"
 * setting, which many phones ship switched off, so it did nothing at all. The app has its own switch in Settings.
 */
object Haptics {
    enum class Kind { Tick, Click, Heavy }

    @Volatile private var vibrator: Vibrator? = null
    private var last = 0L

    private fun get(context: Context): Vibrator? {
        vibrator?.let { return it }
        val v = try {
            if (Build.VERSION.SDK_INT >= 31) context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) { null }
        vibrator = v
        return v
    }

    fun play(context: Context, kind: Kind = Kind.Click) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - last < 35) return
        last = now
        val v = get(context)?.takeIf { it.hasVibrator() } ?: return
        try { v.vibrate(effect(v, kind)) } catch (_: Exception) { }
    }

    /**
     * The phone's own tuned effects first (the ones the system plays for recent apps and long presses feel strong on a
     * good motor); a full-amplitude pulse where the phone has none.
     */
    private fun effect(v: Vibrator, kind: Kind): VibrationEffect {
        if (Build.VERSION.SDK_INT >= 29) {
            val id = when (kind) {
                Kind.Tick -> VibrationEffect.EFFECT_CLICK
                Kind.Click -> VibrationEffect.EFFECT_HEAVY_CLICK
                Kind.Heavy -> -1
            }
            if (id != -1) return VibrationEffect.createPredefined(id)
            return VibrationEffect.createWaveform(longArrayOf(0, 40, 45, 70), intArrayOf(0, 255, 0, 255), -1)
        }
        val ms = when (kind) { Kind.Tick -> 22L; Kind.Click -> 38L; Kind.Heavy -> 70L }
        return if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(ms, 255) else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
    }
}
