package app.naviamp.desktop.cast

import app.naviamp.app.NaviampCastChannelCodec
import app.naviamp.app.NaviampCastEndpoint
import app.naviamp.app.NaviampCastTransportConnection
import app.naviamp.app.NaviampCastTransportFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** JVM TLS ABI only. Cast self-signed TLS is authenticated by Core's device proof before launch. */
class DesktopNaviampCastTransportFactory : NaviampCastTransportFactory {
    private val context = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = error("Not a TLS server.")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { require(chain.isNotEmpty()) }
        }), null)
    }

    override suspend fun connect(endpoint: NaviampCastEndpoint): NaviampCastTransportConnection {
        val socket = context.socketFactory.createSocket() as SSLSocket
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { socket.close() }
            CastSocketExecutor.execute {
                try {
                    endpoint.localAddress?.let { socket.bind(InetSocketAddress(it, 0)) }
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port))
                    socket.tcpNoDelay = true
                    socket.startHandshake()
                    val connection = DesktopNaviampCastTlsConnection(socket)
                    continuation.resume(connection) { _, value, _ -> value.close() }
                } catch (failure: Exception) {
                    socket.close()
                    continuation.resumeWithException(failure)
                }
            }
        }
    }
}

private val CastSocketExecutor = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "naviamp-cast-socket").apply { isDaemon = true }
}

private class DesktopNaviampCastTlsConnection(private val socket: SSLSocket) : NaviampCastTransportConnection {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    override val peerCertificateDer = socket.session.peerCertificates.first().encoded
    override val localAddress: String = socket.localAddress.hostAddress

    override suspend fun send(frame: ByteArray) {
        require(frame.size in 1..NaviampCastChannelCodec.MaximumFrameBytes)
        operation {
            output.writeInt(frame.size)
            output.write(frame)
            output.flush()
        }
    }

    override suspend fun receive(): ByteArray? = operation {
        val first = input.read()
        if (first < 0) null
        else {
            val length = (first shl 24) or (input.readUnsignedByte() shl 16) or
                (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            require(length in 1..NaviampCastChannelCodec.MaximumFrameBytes)
            ByteArray(length).also(input::readFully)
        }
    }

    private suspend fun <T> operation(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        CastSocketExecutor.execute {
            try { continuation.resume(block()) }
            catch (failure: Exception) { close(); continuation.resumeWithException(failure) }
        }
    }

    override fun close() { runCatching { socket.close() } }
}
