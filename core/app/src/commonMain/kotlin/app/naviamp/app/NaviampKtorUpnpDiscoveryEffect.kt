package app.naviamp.app

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/** Raw UDP execution over multiplatform sockets; Core's discovery controller owns scan policy.
 * Calls are serialized by that controller on its injected blocking-effect context.
 */
class NaviampKtorUpnpDiscoveryEffect(
    private val localAddresses: () -> List<String>,
    private val destinationHost: String = NAVIAMP_UPNP_MULTICAST_HOST,
    private val destinationPort: Int = NAVIAMP_UPNP_MULTICAST_PORT,
) : NaviampUpnpDiscoveryEffect {
    private val sockets = mutableListOf<BoundDatagramSocket>()
    private var selector: SelectorManager? = null
    private var workers: CoroutineScope? = null

    override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) {
        stop()
        val manager = SelectorManager(Dispatchers.Default)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        selector = manager; workers = scope
        try {
            runBlocking {
                for (address in localAddresses().distinct().take(24)) {
                    val socket = aSocket(manager).udp().bind(address, 0)
                    sockets += socket
                    scope.launch {
                        try {
                            while (isActive) {
                                val datagram = socket.receive()
                                datagram.packet.use { packet ->
                                    val bytes = packet.readByteArray()
                                    if (bytes.size > 16_384) return@use
                                    val source = datagram.address as? InetSocketAddress ?: return@use
                                    val text = try { bytes.decodeToString(throwOnInvalidSequence = true) }
                                        catch (_: Exception) { return@use }
                                    val ip = source.resolveAddress() ?: return@use
                                    if (ip.size != 4) return@use
                                    receive(NaviampUpnpDiscoveryPacket(ip.joinToString(".") { (it.toInt() and 255).toString() }, address, text))
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Stop closes sockets; the scan controller owns expiry/retry. */ }
                    }
                }
                check(sockets.isNotEmpty())
            }
            refresh(search)
        } catch (failure: Throwable) { stop(); throw failure }
    }

    override fun refresh(search: String) = runBlocking {
        val bytes = search.encodeToByteArray()
        val destination = InetSocketAddress(destinationHost, destinationPort)
        for (socket in sockets) socket.send(Datagram(Buffer().apply { write(bytes) }, destination))
    }

    override fun stop() {
        sockets.forEach { it.close() }; sockets.clear()
        workers?.cancel(); workers = null
        selector?.close(); selector = null
    }
}
