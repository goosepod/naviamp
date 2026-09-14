package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectWireCodec

data class NaviampConnectEncryptedFrame(
    val protocolVersion: Int,
    val sessionId: String,
    val sequence: Long,
    val ciphertext: ByteArray,
) {
    init {
        require(protocolVersion > 0) { "The encrypted frame protocol version must be positive." }
        require(sessionId.isNotBlank()) { "The encrypted frame requires a session ID." }
        require(sequence >= 0) { "The encrypted frame sequence must not be negative." }
        require(ciphertext.isNotEmpty()) { "The encrypted frame requires ciphertext." }
    }
}

enum class NaviampConnectCipherFailure {
    AuthenticationFailed,
    InvalidKey,
    InvalidState,
}

class NaviampConnectCipherException(
    val failure: NaviampConnectCipherFailure,
    cause: Throwable? = null,
) : IllegalStateException("Naviamp Connect cipher failed: $failure", cause)

interface NaviampConnectAuthenticatedCipher {
    fun seal(sequence: Long, plaintext: ByteArray, authenticatedData: ByteArray): ByteArray
    fun open(sequence: Long, ciphertext: ByteArray, authenticatedData: ByteArray): ByteArray
    fun destroy()
}

/** Platform crypto adapter. Creating a cipher consumes and destroys [sessionSecret]. */
interface NaviampConnectAuthenticatedCipherFactory {
    fun create(
        sessionSecret: NaviampConnectSessionSecret,
        role: NaviampConnectPakeRole,
        protocolVersion: Int,
        sessionId: String,
    ): NaviampConnectAuthenticatedCipher
}

enum class NaviampConnectSecureChannelFailure {
    InvalidSession,
    InvalidSequence,
    AuthenticationFailed,
    InvalidPayload,
    Closed,
}

sealed interface NaviampConnectSecureChannelOpenResult {
    data class Opened(val envelope: NaviampConnectEnvelope) : NaviampConnectSecureChannelOpenResult
    data class Rejected(val failure: NaviampConnectSecureChannelFailure) : NaviampConnectSecureChannelOpenResult
}

/**
 * Shared authenticated-session framing policy. The cipher effect performs only the native AEAD
 * operation; Core owns session binding, directional sequence enforcement, and envelope validation.
 */
class NaviampConnectAuthenticatedChannel(
    private val protocolVersion: Int,
    private val sessionId: String,
    private val outboundDirection: NaviampConnectPakeRole,
    private val cipher: NaviampConnectAuthenticatedCipher,
    private val maximumCiphertextBytes: Int = 1_048_576,
) {
    private var nextOutboundSequence = 0L
    private var nextInboundSequence = 0L
    private var closed = false

    init {
        require(protocolVersion > 0) { "The secure-channel protocol version must be positive." }
        require(sessionId.isNotBlank()) { "The secure channel requires a session ID." }
        require(maximumCiphertextBytes > 0) { "The ciphertext limit must be positive." }
    }

    fun seal(envelope: NaviampConnectEnvelope): NaviampConnectEncryptedFrame {
        check(!closed) { "The Naviamp Connect secure channel is closed." }
        require(envelope.protocolVersion == protocolVersion) { "The envelope protocol version changed." }
        require(envelope.sessionId == sessionId) { "The envelope session ID changed." }
        require(envelope.sequence == nextOutboundSequence) { "The outbound sequence is not contiguous." }
        val plaintext = NaviampConnectWireCodec.encode(envelope).encodeToByteArray()
        val authenticatedData = authenticatedData(envelope.sequence, outboundDirection)
        return try {
            try {
                val ciphertext = cipher.seal(envelope.sequence, plaintext, authenticatedData)
                require(ciphertext.size <= maximumCiphertextBytes) { "The encrypted frame is too large." }
                nextOutboundSequence += 1
                NaviampConnectEncryptedFrame(protocolVersion, sessionId, envelope.sequence, ciphertext)
            } catch (failure: Exception) {
                close()
                throw failure
            }
        } finally {
            plaintext.fill(0)
            authenticatedData.fill(0)
        }
    }

    fun open(frame: NaviampConnectEncryptedFrame): NaviampConnectSecureChannelOpenResult {
        if (closed) return NaviampConnectSecureChannelOpenResult.Rejected(NaviampConnectSecureChannelFailure.Closed)
        if (frame.protocolVersion != protocolVersion || frame.sessionId != sessionId) {
            return reject(NaviampConnectSecureChannelFailure.InvalidSession)
        }
        if (frame.sequence != nextInboundSequence) {
            return reject(NaviampConnectSecureChannelFailure.InvalidSequence)
        }
        if (frame.ciphertext.size > maximumCiphertextBytes) {
            return reject(NaviampConnectSecureChannelFailure.InvalidPayload)
        }
        val inboundDirection = when (outboundDirection) {
            NaviampConnectPakeRole.Controller -> NaviampConnectPakeRole.Target
            NaviampConnectPakeRole.Target -> NaviampConnectPakeRole.Controller
        }
        val authenticatedData = authenticatedData(frame.sequence, inboundDirection)
        val plaintext = try {
            cipher.open(frame.sequence, frame.ciphertext, authenticatedData)
        } catch (_: NaviampConnectCipherException) {
            return reject(NaviampConnectSecureChannelFailure.AuthenticationFailed)
        } finally {
            authenticatedData.fill(0)
        }
        return try {
            val envelope = NaviampConnectWireCodec.decode(plaintext.decodeToString(throwOnInvalidSequence = true))
            if (envelope.protocolVersion != protocolVersion ||
                envelope.sessionId != sessionId ||
                envelope.sequence != frame.sequence
            ) {
                reject(NaviampConnectSecureChannelFailure.InvalidPayload)
            } else {
                nextInboundSequence += 1
                NaviampConnectSecureChannelOpenResult.Opened(envelope)
            }
        } catch (_: Exception) {
            reject(NaviampConnectSecureChannelFailure.InvalidPayload)
        } finally {
            plaintext.fill(0)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        cipher.destroy()
    }

    private fun authenticatedData(sequence: Long, direction: NaviampConnectPakeRole): ByteArray =
        buildString {
            append("Naviamp Connect AEAD v1|")
            append(protocolVersion)
            append('|')
            append(sessionId.length)
            append(':')
            append(sessionId)
            append('|')
            append(sequence)
            append('|')
            append(direction.protocolId)
        }.encodeToByteArray()

    private fun reject(
        failure: NaviampConnectSecureChannelFailure,
        close: Boolean = true,
    ): NaviampConnectSecureChannelOpenResult.Rejected {
        if (close) close()
        return NaviampConnectSecureChannelOpenResult.Rejected(failure)
    }
}
