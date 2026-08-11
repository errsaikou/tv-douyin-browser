package com.example.tvdouyin.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Network utility to obtain the device's WiFi IP address.
 * Used to generate the QR code URL for phone scanning.
 */
object NetworkUtils {

    /**
     * Get the WiFi IPv4 address of this device.
     * Tries WifiManager first (most reliable on TV), falls back to NetworkInterface enumeration.
     *
     * @return IPv4 address string (e.g., "192.168.1.105") or null if unavailable
     */
    fun getWifiIpAddress(context: Context): String? {
        // Method 1: WifiManager (works on most Android TV devices)
        try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0") return ip
            }
        } catch (_: Exception) {
        }

        // Method 2: Enumerate network interfaces (fallback for Ethernet / special configs)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }

        return null
    }
}
