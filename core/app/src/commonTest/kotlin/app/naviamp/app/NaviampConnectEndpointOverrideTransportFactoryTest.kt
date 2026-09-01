package app.naviamp.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NaviampConnectEndpointOverrideTransportFactoryTest {
    @Test
    fun replacesOnlyConfiguredDiscoveryHostsAndDelegatesListening() = runTest {
        val delegate = RecordingTransportFactory()
        val transport = NaviampConnectEndpointOverrideTransportFactory(
            delegate = delegate,
            overriddenHosts = setOf("10.0.2.15"),
            replacementHost = "192.168.1.25",
        )

        assertSame(delegate.connection, transport.connect("10.0.2.15", 42_425))
        assertEquals("192.168.1.25" to 42_425, delegate.lastConnection)

        transport.connect("192.168.1.80", 42_426)
        assertEquals("192.168.1.80" to 42_426, delegate.lastConnection)

        assertSame(delegate.listener, transport.listen(42_427))
        assertEquals(42_427, delegate.lastListenPort)
    }

    private class RecordingTransportFactory : NaviampConnectTransportFactory {
        val connection = object : NaviampConnectTransportConnection {
            override val remoteAddress = "test"
            override suspend fun send(frame: ByteArray) = Unit
            override suspend fun receive(): ByteArray? = null
            override fun close() = Unit
        }
        val listener = object : NaviampConnectTransportListener {
            override val port = 42_427
            override suspend fun accept(): NaviampConnectTransportConnection = connection
            override fun close() = Unit
        }
        var lastConnection: Pair<String, Int>? = null
        var lastListenPort: Int? = null

        override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
            lastConnection = host to port
            return connection
        }

        override fun listen(port: Int): NaviampConnectTransportListener {
            lastListenPort = port
            return listener
        }
    }
}
