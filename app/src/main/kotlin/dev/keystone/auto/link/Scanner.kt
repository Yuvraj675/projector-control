package dev.keystone.auto.link

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object Subnet {
    fun ipToInt(ip: String): Int = ip.split('.').fold(0) { acc, part -> (acc shl 8) or part.toInt() }

    fun intToIp(v: Int) = "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"

    /**
     * Usable host addresses of the subnet containing `ip`, excluding `ip` itself. Big subnets are limited to a window
     * of `maxHosts` addresses around `ip` so a scan stays quick.
     */
    fun hosts(ip: String, prefixLength: Int, maxHosts: Int = 1022): List<String> {
        val me = ipToInt(ip)
        val mask = if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength))
        val network = me and mask
        val broadcast = network or mask.inv()
        var first = network + 1
        var last = broadcast - 1
        if (last - first + 1 > maxHosts) {
            first = maxOf(first, me - maxHosts / 2)
            last = minOf(last, first + maxHosts - 1)
        }
        return (first..last).filter { it != me }.map(::intToIp)
    }
}

object AdbScanner {
    fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }

    /** Returns the hosts with the ADB port open, calling `onProgress(done, total)` as it goes. */
    fun scan(
        hosts: List<String>,
        port: Int = AdbConnection.DEFAULT_PORT,
        timeoutMs: Int = 500,
        parallelism: Int = 96,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        cancelled: () -> Boolean = { false },
    ): List<String> {
        if (hosts.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(parallelism, hosts.size))
        val done = AtomicInteger()
        try {
            val futures = hosts.map { h ->
                pool.submit(Callable {
                    val open = !cancelled() && portOpen(h, port, timeoutMs)
                    onProgress(done.incrementAndGet(), hosts.size)
                    if (open) h else null
                })
            }
            return futures.mapNotNull { it.get() }.sortedBy { Subnet.ipToInt(it) }
        } finally {
            pool.shutdownNow()
        }
    }
}
