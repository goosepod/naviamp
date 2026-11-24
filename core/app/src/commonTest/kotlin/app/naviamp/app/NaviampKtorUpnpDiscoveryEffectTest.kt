package app.naviamp.app

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.*

class NaviampKtorUpnpDiscoveryEffectTest {
    @Test fun loopbackDatagramsPreserveInterfaceAndSourceAndRestartCleanly() = runTest { withContext(Dispatchers.Default) {
        val manager = SelectorManager(Dispatchers.Default)
        // Darwin's small default UDP send buffer otherwise rejects the oversized fixture at send().
        val receiver = aSocket(manager).udp().bind("127.0.0.1", 0) { sendBufferSize = 65_536 }
        val port = (receiver.localAddress as InetSocketAddress).port
        val packets = Channel<NaviampUpnpDiscoveryPacket>(Channel.UNLIMITED)
        val effect = NaviampKtorUpnpDiscoveryEffect({ listOf("127.0.0.1") }, "127.0.0.1", port)
        try {
            repeat(2) {
                effect.start("search-$it") { packet -> packets.trySend(packet) }
                val request = withTimeout(5_000) { receiver.receive() }
                assertEquals("search-$it", request.packet.readByteArray().decodeToString())
                // Neither invalid UTF-8 nor oversized datagrams may reach the protocol parser.
                receiver.send(Datagram(Buffer().apply { write(byteArrayOf(0xff.toByte())) }, request.address))
                receiver.send(Datagram(Buffer().apply { write(ByteArray(16_385)) }, request.address))
                receiver.send(Datagram(Buffer().apply { write("reply-$it".encodeToByteArray()) }, request.address))
                val packet = withTimeout(5_000) { packets.receive() }
                assertEquals("127.0.0.1", packet.sourceHost)
                assertEquals("127.0.0.1", packet.localAddress)
                assertEquals("reply-$it", packet.datagram)
                effect.refresh("refresh-$it")
                assertEquals("refresh-$it", withTimeout(5_000) { receiver.receive() }.packet.readByteArray().decodeToString())
                effect.stop(); effect.stop()
            }
        } finally { effect.stop(); receiver.close(); manager.close(); packets.close() }
    } }

    @Test fun failedStartReleasesItsListenerAndCanBeRetried() {
        var addresses = emptyList<String>()
        val effect = NaviampKtorUpnpDiscoveryEffect({ addresses }, "127.0.0.1", 9)
        try {
            assertFailsWith<IllegalStateException> { effect.start("search") {} }
            addresses = listOf("127.0.0.1")
            effect.start("search") {}
        } finally { effect.stop() }
    }
}
