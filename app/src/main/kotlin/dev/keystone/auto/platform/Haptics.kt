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
    enum class Kind(val ms: Long, val amplitude: Int) {
        Tick(8, 70), Click(16, 140), Heavy(34, 255),
    }

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
        if (now - last < 20) return
        last = now
        val v = get(context)?.takeIf { it.hasVibrator() } ?: return
        try {
            val effect = if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(kind.ms, kind.amplitude)
            else VibrationEffect.createOneShot(kind.ms + 8, VibrationEffect.DEFAULT_AMPLITUDE)
            v.vibrate(effect)
        } catch (_: Exception) { }
    }
}
