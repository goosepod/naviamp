package app.naviamp.desktop

import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.naviampConnectivityMonitor
import java.net.NetworkInterface

/** Synchronous JVM connectivity snapshot used by the shared application runtime. */
class DesktopConnectivityMonitor(
    networkAvailable: () -> Boolean = ::desktopNetworkAvailable,
) : NaviampConnectivityMonitor by naviampConnectivityMonitor(networkAvailable)

private fun desktopNetworkAvailable(): Boolean {
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (networkInterface.isUp && !networkInterface.isLoopback) return true
    }
    return false
}
