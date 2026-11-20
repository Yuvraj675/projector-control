package dev.keystone.auto.data

import dev.keystone.auto.core.Offsets
import org.json.JSONArray
import org.json.JSONObject

/** A keystone the user saved under a name, e.g. "Bedroom ceiling". */
data class SavedKeystone(val name: String, val offsets: Offsets, val savedAt: Long)

object SavedKeystones {
    const val MAX = 12

    /** Adds or replaces (same name, ignoring case) and keeps the newest [MAX]. */
    fun put(list: List<SavedKeystone>, item: SavedKeystone): List<SavedKeystone> =
        (list.filterNot { it.name.equals(item.name, ignoreCase = true) } + item).takeLast(MAX)

    fun remove(list: List<SavedKeystone>, name: String) = list.filterNot { it.name.equals(name, ignoreCase = true) }

    /** "Saved 3" style default name that does not clash with an existing one. */
    fun defaultName(list: List<SavedKeystone>): String {
        var n = list.size + 1
        while (list.any { it.name.equals("Setup $n", ignoreCase = true) }) n++
        return "Setup $n"
    }

    fun toJson(list: List<SavedKeystone>): String = JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("name", it.name).put("savedAt", it.savedAt).put("offsets", Codec.offsetsToJson(it.offsets))) }
    }.toString(2)

    fun fromJson(text: String): List<SavedKeystone> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            SavedKeystone(j.getString("name"), Codec.offsetsFromJson(j.getJSONObject("offsets")), j.optLong("savedAt"))
        }
    }.getOrDefault(emptyList())
}
