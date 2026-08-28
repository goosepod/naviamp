package app.naviamp.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Android/Desktop raw TCP effect with a four-byte, big-endian length prefix per frame. */
class JvmNaviampConnectTcpTransportFactory(
    private val maximumFrameBytes: Int = DefaultMaximumFrameBytes,
    private val connectTimeoutMillis: Int = DefaultConnectTimeoutMillis,
) : NaviampConnectTransportFactory {
    init {
        require(maximumFrameBytes > 0) { "The maximum frame size must be positive." }
        require(connectTimeoutMillis > 0) { "The connect timeout must be positive." }
    }

    override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
        if (host.isBlank() || port !in 1..65_535) {
            throw NaviampConnectTransportException(NaviampConnectTransportFailure.InvalidAddress)
        }
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
                JvmNaviampConnectTcpConnection(socket, maximumFrameBytes)
            } catch (failure: Exception) {
                runCatching { socket.close() }
                throw NaviampConnectTransportException(
                    NaviampConnectTransportFailure.ConnectionFailed,
                    failure,
                )
            }
        }
    }

    override fun listen(port: Int): NaviampConnectTransportListener {
        if (port !in 0..65_535) {
            throw NaviampConnectTransportException(NaviampConnectTransportFailure.InvalidAddress)
        }
        return try {
            JvmNaviampConnectTcpListener(ServerSocket(port), maximumFrameBytes)
        } catch (failure: Exception) {
            throw NaviampConnectTransportException(
                NaviampConnectTransportFailure.ConnectionFailed,
                failure,
            )
        }
    }

    private companion object {
        const val DefaultMaximumFrameBytes = 1_048_576
        const val DefaultConnectTimeoutMillis = 5_000
    }
}

private class JvmNaviampConnectTcpListener(
    private val serverSocket: ServerSocket,
    private val maximumFrameBytes: Int,
) : NaviampConnectTransportListener {
    override val port: Int = serverSocket.localPort

    override suspend fun accept(): NaviampConnectTransportConnection = withContext(Dispatchers.IO) {
        try {
            val socket = serverSocket.accept()
            socket.tcpNoDelay = true
            JvmNaviampConnectTcpConnection(socket, maximumFrameBytes)
        } catch (failure: SocketException) {
            val reason = if (serverSocket.isClosed) {
                NaviampConnectTransportFailure.Closed
            } else {
                NaviampConnectTransportFailure.ConnectionFailed
            }
            throw NaviampConnectTransportException(reason, failure)
        } catch (failure: Exception) {
            throw NaviampConnectTransportException(
                NaviampConnectTransportFailure.ConnectionFailed,
                failure,
            )
        }
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }
}

private class JvmNaviampConnectTcpConnection(
    private val socket: Socket,
    private val maximumFrameBytes: Int,
) : NaviampConnectTransportConnection {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())
    private val readMutex = Mutex()
    private val writeMutex = Mutex()

    override val remoteAddress: String = socket.inetAddress.hostAddress.orEmpty()

    override suspend fun send(frame: ByteArray) {
        if (frame.isEmpty() || frame.size > maximumFrameBytes) {
            close()
            throw NaviampConnectTransportException(NaviampConnectTransportFailure.InvalidFrame)
        }
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    output.writeInt(frame.size)
                    output.write(frame)
                    output.flush()
                } catch (failure: Exception) {
                    close()
                    throw NaviampConnectTransportException(
                        NaviampConnectTransportFailure.ConnectionFailed,
                        failure,
                    )
                }
            }
        }
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        readMutex.withLock {
            val frameSize = try {
                input.readInt()
            } catch (_: EOFException) {
                return@withLock null
            } catch (failure: Exception) {
                close()
                throw NaviampConnectTransportException(
                    NaviampConnectTransportFailure.ConnectionFailed,
                    failure,
                )
            }
            if (frameSize !in 1..maximumFrameBytes) {
                close()
                throw NaviampConnectTransportException(NaviampConnectTransportFailure.InvalidFrame)
            }
            try {
                ByteArray(frameSize).also(input::readFully)
            } catch (failure: Exception) {
                close()
                throw NaviampConnectTransportException(
                    NaviampConnectTransportFailure.ConnectionFailed,
                    failure,
                )
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
