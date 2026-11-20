package dev.keystone.auto.link

/** Everything worth pasting into a bug report, gathered in one place. */
data class DiagnosticsInput(
    val info: ProjectorInfo,
    val status: ProjectorStatus?,
    val pingMs: Long?,
    val temperatureC: Double?,
    val firmwareAutoKeystone: Boolean?,
    val link: LinkState,
    val appVersion: String,
    val phone: String,
)

object Diagnostics {
    /** Plain-text report. Contains no IP address or MAC: it is meant to be shared. */
    fun report(d: DiagnosticsInput): String = buildString {
        appendLine("ProjectorConnect ${d.appVersion} on ${d.phone}")
        appendLine("Projector: ${d.info.displayName}, Android ${d.info.androidRelease} (API ${d.info.sdk})")
        appendLine("Supported (Hisilicon TV API): ${if (d.info.supported) "yes" else "no - ${d.info.unsupportedReason}"}")
        appendLine("Link: ${d.link}, response time: ${d.pingMs?.let { "$it ms" } ?: "unknown"} (${rate(d.pingMs)})")
        d.status?.let {
            appendLine("Memory: ${it.ramFreeMb} MB free of ${it.ramTotalMb} MB${if (ProjectorTools.isLowOnMemory(it)) " (LOW)" else ""}")
            appendLine("Storage: ${it.storageFreeMb} MB free of ${it.storageTotalMb} MB")
            appendLine("Processor: ${it.abi}")
        } ?: appendLine("Memory/storage: unknown")
        appendLine("Temperature: ${d.temperatureC?.let { "%.0f °C".format(it) } ?: "not reported"}")
        appendLine("Firmware Auto keystone: ${when (d.firmwareAutoKeystone) { true -> "ON (blocks changes)"; false -> "off"; null -> "unknown" }}")
    }

    fun rate(pingMs: Long?) = when {
        pingMs == null -> "no answer"
        pingMs < 80 -> "good"
        pingMs < 250 -> "okay"
        else -> "slow: move the router closer or use 5 GHz Wi-Fi"
    }

    /** Thermal zone files report milli-degrees on most boards, whole degrees on a few. */
    fun parseTemperature(raw: String): Double? {
        val v = raw.trim().toDoubleOrNull() ?: return null
        val c = if (v > 1000) v / 1000.0 else v
        return c.takeIf { it in 0.0..150.0 }
    }
}
