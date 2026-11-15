package app.naviamp.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.*

/** JVM/Android network-interface and UDP ABI only. Core supplies SSDP bytes and scan policy. */
class JvmNaviampUpnpDiscoveryEffect : NaviampUpnpDiscoveryEffect {
    private val sockets = mutableListOf<DatagramSocket>()
    private var workers: CoroutineScope? = null
    @Synchronized override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        workers = scope
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            for (address in interfaces.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().take(24)) {
                val socket = DatagramSocket(null)
                try { socket.bind(InetSocketAddress(address, 0)); socket.soTimeout = 1_000 }
                catch (failure: Exception) { socket.close(); throw failure }
                sockets += socket
                scope.launch {
                    val bytes = ByteArray(16_385)
                    while (isActive && !socket.isClosed) {
                        val packet = DatagramPacket(bytes, bytes.size)
                        try { socket.receive(packet) }
                        catch (_: SocketTimeoutException) { continue }
                        catch (_: java.io.IOException) { break }
                        if (packet.length > 16_384) continue
                        val text = try { bytes.decodeToString(0, packet.length, throwOnInvalidSequence = true) } catch (_: Exception) { continue }
                        receive(NaviampUpnpDiscoveryPacket(packet.address.hostAddress, address.hostAddress, text))
                    }
                }
            }
            check(sockets.isNotEmpty())
            refresh(search)
        } catch (failure: Exception) { stop(); throw failure }
    }
    @Synchronized override fun refresh(search: String) {
        val bytes = search.encodeToByteArray()
        for (socket in sockets) socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("239.255.255.250"), 1900))
    }
    @Synchronized override fun stop() {
        sockets.forEach { it.close() }; sockets.clear()
        workers?.cancel(); workers = null
    }
}
