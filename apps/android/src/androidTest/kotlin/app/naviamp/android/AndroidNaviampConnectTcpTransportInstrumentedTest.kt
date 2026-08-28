package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.naviamp.app.JvmNaviampConnectTcpTransportFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectTcpTransportInstrumentedTest {
    @Test
    fun framedTcpRoundTripsOnAndroidRuntime() = runBlocking {
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val accepted = async { listener.accept() }
        val controller = factory.connect("127.0.0.1", listener.port)
        val target = accepted.await()

        try {
            val request = "pixel-to-target".encodeToByteArray()
            controller.send(request)
            assertArrayEquals(request, target.receive())

            val response = "target-to-pixel".encodeToByteArray()
            target.send(response)
            assertArrayEquals(response, controller.receive())
        } finally {
            controller.close()
            target.close()
            listener.close()
        }
    }
}
