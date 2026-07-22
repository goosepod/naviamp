package app.naviamp.desktop

import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import java.net.NetworkInterface

/** Synchronous JVM connectivity snapshot used by the shared application runtime. */
class DesktopConnectivityMonitor(
    private val networkAvailable: () -> Boolean = ::desktopNetworkAvailable,
) : NaviampConnectivityMonitor {
    override fun currentSnapshot(): NaviampConnectivitySnapshot =
        NaviampConnectivitySnapshot(
            // Preserve the existing online-first behavior if the JVM cannot inspect interfaces.
            available = runCatching(networkAvailable).getOrDefault(true),
        )
}

private fun desktopNetworkAvailable(): Boolean {
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (networkInterface.isUp && !networkInterface.isLoopback) return true
    }
    return false
}
