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
        try { Log.println(if (level == "E") Log.ERROR else Log.DEBUG, "ProjectorConnect", msg) } catch (_: Throwable) { }
        _lines.value = (_lines.value + "${fmt.format(Date())} $level $msg").takeLast(300)
    }

    fun clear() { _lines.value = emptyList() }

    fun text() = _lines.value.joinToString("\n")
}


/** Writes an uncaught crash to a file and shows it in the Activity log on the next start, so a crash is never lost. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(dir: java.io.File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { java.io.File(dir, FILE).writeText("${Date()} thread=${t.name}\n" + e.stackTraceToString().take(6000)) } catch (_: Throwable) { }
            previous?.uncaughtException(t, e)
        }
        val f = java.io.File(dir, FILE)
        if (f.exists()) {
            runCatching { AppLog.e("The app crashed last time:\n" + f.readText()) }
            f.delete()
        }
    }
}
