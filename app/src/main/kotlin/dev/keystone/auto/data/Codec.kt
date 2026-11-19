package dev.keystone.auto.data

import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Params
import org.json.JSONArray
import org.json.JSONObject

/** One finished session, kept for later learning and so the user can see what happened. */
data class HistoryEntry(
    val timeMs: Long,
    val projectorId: String,
    val before: Offsets,
    val applied: Offsets,
    val params: Params,
    val captures: Int,
    val spreadPx: Double,
    val verdict: String,                 // "accepted", "rejected:<FEEDBACK>", "undone"
    val note: String = "",
)

object Codec {
    fun offsetsToJson(o: Offsets) = JSONObject().also { j -> o.toMap().forEach { (k, v) -> j.put(k, v) } }

    fun offsetsFromJson(j: JSONObject) = Offsets.fromMap(Offsets.KEYS.associateWith { j.getInt(it) })

    fun paramsToJson(p: Params) = JSONObject().put("aspect", p.aspect).put("yawDeg", p.yawDeg).put("rollBiasDeg", p.rollBiasDeg)

    fun paramsFromJson(j: JSONObject) = Params(j.getDouble("aspect"), j.getDouble("yawDeg"), j.getDouble("rollBiasDeg"))

    fun profileToJson(p: Profile): String = JSONObject()
        .put("schema", 1).put("params", paramsToJson(p.params)).put("accepted", p.accepted).put("rejected", p.rejected).toString(2)

    fun profileFromJson(s: String): Profile = try {
        val j = JSONObject(s)
        Profile(paramsFromJson(j.getJSONObject("params")), j.optInt("accepted", 0), j.optInt("rejected", 0))
    } catch (_: Exception) {
        Profile()
    }

    fun historyToJson(h: HistoryEntry) = JSONObject()
        .put("time", h.timeMs).put("projector", h.projectorId)
        .put("before", offsetsToJson(h.before)).put("applied", offsetsToJson(h.applied))
        .put("params", paramsToJson(h.params)).put("captures", h.captures).put("spreadPx", h.spreadPx)
        .put("verdict", h.verdict).put("note", h.note)

    fun historyFromJson(j: JSONObject) = HistoryEntry(
        j.getLong("time"), j.getString("projector"), offsetsFromJson(j.getJSONObject("before")), offsetsFromJson(j.getJSONObject("applied")),
        paramsFromJson(j.getJSONObject("params")), j.getInt("captures"), j.optDouble("spreadPx", 0.0), j.getString("verdict"), j.optString("note", ""),
    )

    fun historyListFromJson(s: String): List<HistoryEntry> = try {
        val a = JSONArray(s)
        (0 until a.length()).map { historyFromJson(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    fun historyListToJson(l: List<HistoryEntry>): String = JSONArray().also { a -> l.forEach { a.put(historyToJson(it)) } }.toString(2)
}
