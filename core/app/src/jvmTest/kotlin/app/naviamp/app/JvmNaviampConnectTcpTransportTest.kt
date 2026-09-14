package app.naviamp.app

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JvmNaviampConnectTcpTransportTest {
    @Test
    fun preservesFramesInBothDirections() = runTest {
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val accepted = async { listener.accept() }
        val controller = factory.connect("127.0.0.1", listener.port)
        val target = accepted.await()

        try {
            controller.send(byteArrayOf(1, 2, 3))
            assertContentEquals(byteArrayOf(1, 2, 3), target.receive())

            target.send("reply".encodeToByteArray())
            assertContentEquals("reply".encodeToByteArray(), controller.receive())
            assertEquals("127.0.0.1", controller.remoteAddress)
        } finally {
            controller.close()
            target.close()
            listener.close()
        }
    }

    @Test
    fun cleanPeerCloseReturnsEndOfStream() = runTest {
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val accepted = async { listener.accept() }
        val controller = factory.connect("127.0.0.1", listener.port)
        val target = accepted.await()

        try {
            controller.close()
            assertNull(target.receive())
        } finally {
            target.close()
            listener.close()
        }
    }

    @Test
    fun oversizedOutboundFrameClosesConnection() = runTest {
        val factory = JvmNaviampConnectTcpTransportFactory(maximumFrameBytes = 4)
        val listener = factory.listen()
        val accepted = async { listener.accept() }
        val controller = factory.connect("127.0.0.1", listener.port)
        val target = accepted.await()

        try {
            val failure = assertFailsWith<NaviampConnectTransportException> {
                controller.send(ByteArray(5))
            }
            assertEquals(NaviampConnectTransportFailure.InvalidFrame, failure.failure)
            assertNull(target.receive())
        } finally {
            controller.close()
            target.close()
            listener.close()
        }
    }
}
