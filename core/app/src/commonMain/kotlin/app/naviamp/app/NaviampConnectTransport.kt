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

/**
 * Replaces selected unroutable discovery hosts before invoking the real transport.
 *
 * This is intended for development environments such as an Android TV AVD whose DNS-SD record
 * contains an emulator-private NAT address. Core retains the routing rule while the host supplies
 * the reachable development endpoint.
 */
class NaviampConnectEndpointOverrideTransportFactory(
    private val delegate: NaviampConnectTransportFactory,
    private val overriddenHosts: Set<String>,
    private val replacementHost: String,
) : NaviampConnectTransportFactory {
    init {
        require(overriddenHosts.isNotEmpty()) { "At least one overridden host is required." }
        require(overriddenHosts.none(String::isBlank)) { "Overridden hosts must not be blank." }
        require(replacementHost.isNotBlank()) { "The replacement host must not be blank." }
    }

    override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection =
        delegate.connect(host.takeUnless(overriddenHosts::contains) ?: replacementHost, port)

    override fun listen(port: Int): NaviampConnectTransportListener = delegate.listen(port)
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
