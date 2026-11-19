package dev.keystone.auto.platform

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small in-memory log the user can open in Settings to see what the app did (helps diagnose ADB problems). */
object AppLog {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun d(msg: String) = add("D", msg)

    fun e(msg: String, t: Throwable? = null) = add("E", msg + (t?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))

    private fun add(level: String, msg: String) {
        try { Log.println(if (level == "E") Log.ERROR else Log.DEBUG, "AutoKeystone", msg) } catch (_: Throwable) { }
        _lines.value = (_lines.value + "${fmt.format(Date())} $level $msg").takeLast(300)
    }

    fun clear() { _lines.value = emptyList() }

    fun text() = _lines.value.joinToString("\n")
}
