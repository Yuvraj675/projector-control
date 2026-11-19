package dev.keystone.auto.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's Wi-Fi IPv4 address and prefix length, or null if it is not on Wi-Fi / Ethernet. */
data class LocalNet(val ip: String, val prefixLength: Int)

object Network {
    fun local(context: Context): LocalNet? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidates = buildList {
            cm.activeNetwork?.let { add(it) }
            @Suppress("DEPRECATION") addAll(cm.allNetworks.toList())
        }
        for (n in candidates) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            val lan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!lan) continue
            val addr = cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            if (addr != null) return LocalNet(addr.address.hostAddress ?: continue, addr.prefixLength)
        }
        return null
    }
}
