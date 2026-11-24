package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.naviamp.app.NaviampCastHttpRequest
import app.naviamp.app.NaviampCastHttpResponse
import app.naviamp.app.NaviampCastHttpServerEffect
import app.naviamp.app.NaviampKtorMediaHttpServerEffect
import java.net.Inet4Address

/** Android ConnectivityManager address selection; HTTP framing and listener lifetime stay shared. */
class AndroidNaviampCastHttpServerEffect(context: Context) : NaviampCastHttpServerEffect {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val server = NaviampKtorMediaHttpServerEffect()

    override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String =
        server.start(lanAddresses().firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }?.hostAddress
            ?: error("No receiver-reachable Wi-Fi or Ethernet address is available."), handler)

    override suspend fun start(localAddress: String?, handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
        if (localAddress == null) return start(handler)
        val address = lanAddresses().firstOrNull { it.hostAddress == localAddress }
            ?: error("Selected receiver interface is unavailable.")
        return server.start(address.hostAddress, handler)
    }

    override suspend fun stop() = server.stop()

    private fun lanAddresses(): Sequence<Inet4Address> = connectivity.allNetworks.asSequence()
        .filter { network ->
            connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }
        .flatMap { network -> connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence() }
        .mapNotNull { it.address as? Inet4Address }
}
