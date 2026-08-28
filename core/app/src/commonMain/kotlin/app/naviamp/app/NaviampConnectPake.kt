package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectPairingHandshake

enum class NaviampConnectPakeRole(val protocolId: String) {
    Controller("controller"),
    Target("target"),
}

enum class NaviampConnectPakeFailure {
    InvalidState,
    InvalidSession,
    InvalidPayload,
    AuthenticationFailed,
}

class NaviampConnectPakeException(
    val failure: NaviampConnectPakeFailure,
    cause: Throwable? = null,
) : IllegalStateException("Naviamp Connect pairing failed: $failure", cause)

/** A short-lived, destroyable root secret. It is released only after mutual PAKE confirmation. */
class NaviampConnectSessionSecret internal constructor(bytes: ByteArray) {
    private var secret: ByteArray? = bytes.copyOf()

    val isDestroyed: Boolean
        get() = secret == null

    fun copyBytes(): ByteArray = secret?.copyOf()
        ?: throw IllegalStateException("The Naviamp Connect session secret has been destroyed.")

    fun destroy() {
        secret?.fill(0)
        secret = null
    }
}

sealed interface NaviampConnectPakeProgress {
    data class Send(val handshake: NaviampConnectPairingHandshake) : NaviampConnectPakeProgress
    data class Complete(val sessionSecret: NaviampConnectSessionSecret) : NaviampConnectPakeProgress
}

/**
 * Ordered three-round PAKE exchange. Implementations consume and clear [pairingCode].
 * A session must be discarded after any exception.
 */
interface NaviampConnectPakeFactory {
    fun create(
        pairingSessionId: String,
        protocolVersion: Int,
        localRole: NaviampConnectPakeRole,
        localDeviceId: String,
        remoteDeviceId: String,
        pairingCode: CharArray,
    ): NaviampConnectPakeSession
}

interface NaviampConnectPakeSession {
    fun start(): NaviampConnectPairingHandshake

    fun receive(handshake: NaviampConnectPairingHandshake): NaviampConnectPakeProgress

    fun destroy()
}
