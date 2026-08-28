package app.naviamp.app

/**
 * A single ordered Naviamp Connect byte stream.
 *
 * Core owns connection lifecycle and interpretation of every frame. Implementations only provide
 * the platform socket effect and preserve message boundaries.
 */
interface NaviampConnectTransportConnection {
    val remoteAddress: String

    suspend fun send(frame: ByteArray)

    /** Returns null only when the peer has closed the connection cleanly. */
    suspend fun receive(): ByteArray?

    fun close()
}

/** A bound listener. Passing port zero to the factory requests an ephemeral port. */
interface NaviampConnectTransportListener {
    val port: Int

    suspend fun accept(): NaviampConnectTransportConnection

    fun close()
}

interface NaviampConnectTransportFactory {
    suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection

    fun listen(port: Int = 0): NaviampConnectTransportListener
}

enum class NaviampConnectTransportFailure {
    InvalidAddress,
    InvalidFrame,
    ConnectionFailed,
    Closed,
}

class NaviampConnectTransportException(
    val failure: NaviampConnectTransportFailure,
    cause: Throwable? = null,
) : IllegalStateException("Naviamp Connect transport failed: $failure", cause)
