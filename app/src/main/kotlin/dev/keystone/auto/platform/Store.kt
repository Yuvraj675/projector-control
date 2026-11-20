package dev.keystone.auto.platform

import android.content.Context
import dev.keystone.auto.core.Offsets
import dev.keystone.auto.data.Codec
import dev.keystone.auto.data.HistoryEntry
import dev.keystone.auto.data.Profile
import dev.keystone.auto.data.SavedKeystone
import dev.keystone.auto.data.SavedKeystones
import org.json.JSONObject
import java.io.File

/** User settings the UI can change. */
data class Settings(
    val captureCount: Int = 4,
    val autoCapture: Boolean = true,
    val exposureStops: Double = -2.0,    // exposure compensation while photographing the bright pattern
    val pointerSpeed: Double = 1.0,      // touchpad sensitivity
    val airSpeed: Double = 1.0,          // air-mouse (gyroscope) sensitivity
    val naturalScroll: Boolean = true,   // two-finger scroll moves the content with the fingers
    val haptics: Boolean = true,
) {
    companion object {
        const val MIN_CAPTURES = 3
        const val MAX_CAPTURES = 6
    }
}

/** Everything persisted on the phone: settings, learned profile, history, the projector's original keystone. */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("autokeystone", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "data").also { it.mkdirs() }

    var lastHost: String?
        get() = prefs.getString("lastHost", null)
        set(v) { prefs.edit().putString("lastHost", v).apply() }

    /** Projector screen quality: false = smooth (light on the projector), true = sharp. */
    var mirrorSharp: Boolean
        get() = prefs.getBoolean("mirrorSharp", false)
        set(v) { prefs.edit().putBoolean("mirrorSharp", v).apply() }

    fun loadSettings() = Settings(
        captureCount = prefs.getInt("captureCount", 4).coerceIn(Settings.MIN_CAPTURES, Settings.MAX_CAPTURES),
        autoCapture = prefs.getBoolean("autoCapture", true),
        exposureStops = prefs.getFloat("exposureStops", -2f).toDouble(),
        pointerSpeed = prefs.getFloat("pointerSpeed", 1f).toDouble().coerceIn(0.4, 2.5),
        airSpeed = prefs.getFloat("airSpeed", 1f).toDouble().coerceIn(0.4, 2.5),
        naturalScroll = prefs.getBoolean("naturalScroll", true),
        haptics = prefs.getBoolean("haptics", true),
    )

    fun saveSettings(s: Settings) {
        prefs.edit().putInt("captureCount", s.captureCount).putBoolean("autoCapture", s.autoCapture)
            .putFloat("exposureStops", s.exposureStops.toFloat()).putFloat("pointerSpeed", s.pointerSpeed.toFloat())
            .putFloat("airSpeed", s.airSpeed.toFloat()).putBoolean("naturalScroll", s.naturalScroll).putBoolean("haptics", s.haptics).apply()
    }

    fun loadProfile(): Profile = File(dir, "profile.json").takeIf { it.exists() }?.let { Codec.profileFromJson(it.readText()) } ?: Profile()

    fun saveProfile(p: Profile) = File(dir, "profile.json").writeText(Codec.profileToJson(p))

    fun resetProfile() { File(dir, "profile.json").delete() }

    fun loadHistory(): List<HistoryEntry> = File(dir, "history.json").takeIf { it.exists() }?.let { Codec.historyListFromJson(it.readText()) } ?: emptyList()

    fun addHistory(e: HistoryEntry) {
        val all = (loadHistory() + e).takeLast(200)
        File(dir, "history.json").writeText(Codec.historyListToJson(all))
    }

    fun clearHistory() { File(dir, "history.json").delete() }

    private fun originalFile(id: String) = File(dir, "original_$id.json")

    /** The keystone the projector had the first time we met it, kept so the user can always go back. */
    fun originalKeystone(id: String): Offsets? = originalFile(id).takeIf { it.exists() }?.let { runCatching { Codec.offsetsFromJson(JSONObject(it.readText())) }.getOrNull() }

    fun saveOriginalIfMissing(id: String, o: Offsets) {
        val f = originalFile(id)
        if (!f.exists()) f.writeText(Codec.offsetsToJson(o).toString(2))
    }

    private fun savedFile(id: String) = File(dir, "saved_$id.json")

    /** Keystones the user saved by name for this projector. */
    fun savedKeystones(id: String): List<SavedKeystone> = savedFile(id).takeIf { it.exists() }?.let { SavedKeystones.fromJson(it.readText()) } ?: emptyList()

    fun writeSavedKeystones(id: String, list: List<SavedKeystone>) = savedFile(id).writeText(SavedKeystones.toJson(list))

    /** Apps the user pinned to Home for one-tap launching. */
    var pinnedApps: Set<String>
        get() = prefs.getStringSet("pinnedApps", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("pinnedApps", v).apply() }

    fun keyDir(): File = File(context.filesDir, "adb").also { it.mkdirs() }
}
