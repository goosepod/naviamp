@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectHello
import app.naviamp.domain.connect.NaviampConnectMessage
import app.naviamp.domain.connect.NaviampConnectPairingHandshake
import app.naviamp.domain.connect.NaviampConnectPairingConfirmation
import app.naviamp.domain.connect.NaviampConnectPairingIdentityProof
import app.naviamp.domain.connect.NaviampConnectPairingOffer
import app.naviamp.domain.connect.NaviampConnectPublicIdentity
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectTargetPairingState
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.negotiateNaviampConnectProtocol
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface NaviampConnectPairingRuntimeResult {
    data class Paired(
        val trust: NaviampConnectTrustRecord,
        val session: NaviampConnectAuthenticatedSession,
    ) : NaviampConnectPairingRuntimeResult

    data class Failed(val code: NaviampConnectErrorCode) : NaviampConnectPairingRuntimeResult
}

/** Authenticated connection retained after pairing for snapshots and commands. */
class NaviampConnectAuthenticatedSession internal constructor(
    val trust: NaviampConnectTrustRecord?,
    private val connection: NaviampConnectTransportConnection,
    private val channel: NaviampConnectAuthenticatedChannel,
    val protocolVersion: Int,
    val sessionId: String,
) {
    private var nextOutboundSequence = 0L
    private var closed = false
    private val sendMutex = Mutex()

    suspend fun send(message: NaviampConnectMessage, requestId: String? = null) {
        sendEnvelope(
            NaviampConnectEnvelope(
            protocolVersion = protocolVersion,
            sessionId = sessionId,
            sequence = nextOutboundSequence,
            requestId = requestId,
            message = message,
            ),
        )
    }

    suspend fun sendEnvelope(envelope: NaviampConnectEnvelope) = sendMutex.withLock {
        check(!closed) { "The authenticated Connect session is closed." }
        require(envelope.protocolVersion == protocolVersion) { "The authenticated protocol version changed." }
        require(envelope.sessionId == sessionId) { "The authenticated session ID changed." }
        require(envelope.sequence == nextOutboundSequence) { "The authenticated sequence is not contiguous." }
        val packet = NaviampConnectTransportPacket.Encrypted(channel.seal(envelope))
        connection.send(NaviampConnectTransportPacketCodec.encode(packet))
        nextOutboundSequence += 1
    }

    fun nextOutboundSequence(): Long = nextOutboundSequence

    suspend fun receive(): NaviampConnectEnvelope {
        check(!closed) { "The authenticated Connect session is closed." }
        val bytes = connection.receive() ?: failClosed(NaviampConnectSecureChannelFailure.Closed)
        val packet = try {
            NaviampConnectTransportPacketCodec.decode(bytes)
        } catch (_: Exception) {
            failClosed(NaviampConnectSecureChannelFailure.InvalidPayload)
        }
        val frame = (packet as? NaviampConnectTransportPacket.Encrypted)?.frame
            ?: failClosed(NaviampConnectSecureChannelFailure.InvalidPayload)
        return when (val opened = channel.open(frame)) {
            is NaviampConnectSecureChannelOpenResult.Opened -> opened.envelope
            is NaviampConnectSecureChannelOpenResult.Rejected -> failClosed(opened.failure)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        channel.close()
        connection.close()
    }

    internal fun attachTrust(value: NaviampConnectTrustRecord): NaviampConnectAuthenticatedSession =
        NaviampConnectAuthenticatedSession(value, connection, channel, protocolVersion, sessionId).also {
            it.nextOutboundSequence = nextOutboundSequence
        }

    private fun failClosed(failure: NaviampConnectSecureChannelFailure): Nothing {
        close()
        throw NaviampConnectSecureSessionException(failure)
    }
}

class NaviampConnectSecureSessionException(
    val failure: NaviampConnectSecureChannelFailure,
) : IllegalStateException("Naviamp Connect secure session failed: $failure")

/** Controller-side Core orchestration from a resolved discovery result through durable trust. */
class NaviampConnectControllerPairingRuntime(
    private val localDevice: NaviampConnectDevice,
    private val identityEffect: NaviampConnectDeviceIdentityEffect,
    private val identityVerifier: NaviampConnectIdentityVerifier,
    private val transportFactory: NaviampConnectTransportFactory,
    private val pakeFactory: NaviampConnectPakeFactory,
    private val cipherFactory: NaviampConnectAuthenticatedCipherFactory,
    private val localProtocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
) {
    init {
        require(localDevice.role == NaviampConnectDeviceRole.Controller)
    }

    suspend fun pair(
        host: String,
        advertisement: app.naviamp.domain.connect.NaviampConnectAdvertisement,
        pairingCode: CharArray,
        pairedAtEpochMillis: Long,
        trustedDeviceId: String,
    ): NaviampConnectPairingRuntimeResult {
        var connection: NaviampConnectTransportConnection? = null
        var pake: NaviampConnectPakeSession? = null
        var secureSession: NaviampConnectAuthenticatedSession? = null
        return try {
            val localIdentity = identityEffect.loadOrCreate().asPublicIdentity()
            requireIdentity(localIdentity, localDevice, identityVerifier)
            val expectedProtocolVersion = negotiateNaviampConnectProtocol(
                localProtocolRange,
                advertisement.protocolRange,
            ) ?: run {
                pairingCode.fill('\u0000')
                return NaviampConnectPairingRuntimeResult.Failed(NaviampConnectErrorCode.IncompatibleProtocol)
            }
            connection = transportFactory.connect(host, advertisement.port)
            var outboundSequence = 0L
            connection.sendPlaintext(
                NaviampConnectEnvelope(
                    protocolVersion = expectedProtocolVersion,
                    sequence = outboundSequence++,
                    message = NaviampConnectHello(
                        device = localDevice,
                        identity = localIdentity,
                        protocolRange = localProtocolRange,
                        capabilities = emptySet(),
                    ),
                ),
            )
            val offerEnvelope = connection.receivePlaintext()
            val offer = offerEnvelope.message as? NaviampConnectPairingOffer
                ?: return failure(connection, pairingCode, NaviampConnectErrorCode.InvalidRequest)
            if (offerEnvelope.sequence != 0L ||
                offerEnvelope.protocolVersion != expectedProtocolVersion ||
                offerEnvelope.sessionId != offer.pairingSessionId ||
                offer.target.role != NaviampConnectDeviceRole.Target ||
                offer.identity.deviceId != offer.target.deviceId ||
                offer.identity.identityFingerprint != advertisement.identityFingerprint ||
                identityVerifier.fingerprint(offer.identity.publicKeyBase64) != offer.identity.identityFingerprint
            ) {
                return failure(connection, pairingCode, NaviampConnectErrorCode.AuthenticationRequired)
            }
            val protocolVersion = expectedProtocolVersion
            pake = pakeFactory.create(
                pairingSessionId = offer.pairingSessionId,
                protocolVersion = protocolVersion,
                localRole = NaviampConnectPakeRole.Controller,
                localDeviceId = localIdentity.deviceId,
                remoteDeviceId = offer.identity.deviceId,
                pairingCode = pairingCode,
            )
            val secret = exchangePake(
                connection,
                pake,
                protocolVersion,
                offer.pairingSessionId,
                firstOutboundSequence = outboundSequence,
                firstInboundSequence = 1,
            )
            pake = null
            val cipher = cipherFactory.create(
                secret,
                NaviampConnectPakeRole.Controller,
                protocolVersion,
                offer.pairingSessionId,
            )
            val channel = NaviampConnectAuthenticatedChannel(
                protocolVersion,
                offer.pairingSessionId,
                NaviampConnectPakeRole.Controller,
                cipher,
            )
            secureSession = NaviampConnectAuthenticatedSession(
                null,
                connection,
                channel,
                protocolVersion,
                offer.pairingSessionId,
            )
            val proofPayload = naviampConnectIdentityProofPayload(
                protocolVersion,
                offer.pairingSessionId,
                localIdentity,
                offer.identity,
            )
            try {
                secureSession.send(
                    NaviampConnectPairingIdentityProof(
                        identity = localIdentity,
                        signatureBase64 = identityEffect.signBase64(proofPayload),
                    ),
                )
                val remoteProof = secureSession.receive().message as? NaviampConnectPairingIdentityProof
                    ?: return failure(secureSession, NaviampConnectErrorCode.AuthenticationRequired)
                if (remoteProof.identity != offer.identity ||
                    !identityVerifier.verifyProof(remoteProof, proofPayload)
                ) {
                    return failure(secureSession, NaviampConnectErrorCode.AuthenticationRequired)
                }
                secureSession.send(
                    NaviampConnectPairingConfirmation(offer.identity.identityFingerprint),
                )
                val confirmation = secureSession.receive().message as? NaviampConnectPairingConfirmation
                    ?: return failure(secureSession, NaviampConnectErrorCode.AuthenticationRequired)
                if (confirmation.verifiedIdentityFingerprint != localIdentity.identityFingerprint) {
                    return failure(secureSession, NaviampConnectErrorCode.AuthenticationRequired)
                }
            } finally {
                proofPayload.fill(0)
            }
            val trust = NaviampConnectTrustRecord(
                trustedDeviceId = trustedDeviceId,
                peerDevice = offer.target,
                identityFingerprint = offer.identity.identityFingerprint,
                publicKeyBase64 = offer.identity.publicKeyBase64,
                pairedAtEpochMillis = pairedAtEpochMillis,
            )
            val authenticated = secureSession.attachTrust(trust)
            secureSession = null
            connection = null
            NaviampConnectPairingRuntimeResult.Paired(trust, authenticated)
        } catch (_: Exception) {
            pairingCode.fill('\u0000')
            connection?.close()
            NaviampConnectPairingRuntimeResult.Failed(NaviampConnectErrorCode.AuthenticationRequired)
        } finally {
            pake?.destroy()
            secureSession?.close()
            connection?.close()
            pairingCode.fill('\u0000')
        }
    }
}

sealed interface NaviampConnectTargetPairingRequestResult {
    data class AwaitingApproval(val request: NaviampConnectPendingTargetPairing) :
        NaviampConnectTargetPairingRequestResult
    data class Rejected(val code: NaviampConnectErrorCode) : NaviampConnectTargetPairingRequestResult
}

class NaviampConnectTargetPairingRuntime(
    private val localDevice: NaviampConnectDevice,
    private val identityEffect: NaviampConnectDeviceIdentityEffect,
    private val identityVerifier: NaviampConnectIdentityVerifier,
    private val pairingController: NaviampConnectTargetPairingController,
    private val pakeFactory: NaviampConnectPakeFactory,
    private val cipherFactory: NaviampConnectAuthenticatedCipherFactory,
) {
    init {
        require(localDevice.role == NaviampConnectDeviceRole.Target)
    }

    suspend fun receiveRequest(
        connection: NaviampConnectTransportConnection,
        nowEpochMillis: Long,
    ): NaviampConnectTargetPairingRequestResult = try {
        val envelope = connection.receivePlaintext()
        val hello = envelope.message as? NaviampConnectHello
            ?: return reject(connection, NaviampConnectErrorCode.InvalidRequest)
        val advertising = activeAdvertising()
        val negotiatedProtocol = negotiateNaviampConnectProtocol(
            advertising.advertisement.protocolRange,
            hello.protocolRange,
        )
        if (envelope.sequence != 0L || envelope.sessionId != null ||
            negotiatedProtocol == null || envelope.protocolVersion != negotiatedProtocol ||
            hello.device.role != NaviampConnectDeviceRole.Controller ||
            hello.identity.deviceId != hello.device.deviceId ||
            identityVerifier.fingerprint(hello.identity.publicKeyBase64) != hello.identity.identityFingerprint
        ) {
            return reject(connection, NaviampConnectErrorCode.InvalidRequest)
        }
        val localIdentity = identityEffect.loadOrCreate().asPublicIdentity()
        if (identityVerifier.fingerprint(localIdentity.publicKeyBase64) != localIdentity.identityFingerprint ||
            localIdentity.identityFingerprint != advertising.advertisement.identityFingerprint
        ) {
            pairingController.stop()
            return reject(connection, NaviampConnectErrorCode.AuthenticationRequired)
        }
        val state = pairingController.requestApproval(
            hello.device,
            pairingSessionId = advertising.pairingSessionId,
            nowEpochMillis = nowEpochMillis,
        )
        if (state !is NaviampConnectTargetPairingState.AwaitingApproval) {
            return reject(connection, NaviampConnectErrorCode.PairingExpired)
        }
        NaviampConnectTargetPairingRequestResult.AwaitingApproval(
            NaviampConnectPendingTargetPairing(
                connection = connection,
                hello = hello,
                protocolVersion = negotiatedProtocol,
                localDevice = localDevice,
                localIdentity = localIdentity,
                identityEffect = identityEffect,
                identityVerifier = identityVerifier,
                pairingController = pairingController,
                pakeFactory = pakeFactory,
                cipherFactory = cipherFactory,
            ),
        )
    } catch (_: Exception) {
        reject(connection, NaviampConnectErrorCode.InvalidRequest)
    }

    private fun activeAdvertising(): NaviampConnectTargetPairingState.Advertising =
        pairingController.state as? NaviampConnectTargetPairingState.Advertising
            ?: throw IllegalStateException("The target is not advertising for pairing.")
}

class NaviampConnectPendingTargetPairing internal constructor(
    private val connection: NaviampConnectTransportConnection,
    private val hello: NaviampConnectHello,
    private val protocolVersion: Int,
    private val localDevice: NaviampConnectDevice,
    private val localIdentity: NaviampConnectPublicIdentity,
    private val identityEffect: NaviampConnectDeviceIdentityEffect,
    private val identityVerifier: NaviampConnectIdentityVerifier,
    private val pairingController: NaviampConnectTargetPairingController,
    private val pakeFactory: NaviampConnectPakeFactory,
    private val cipherFactory: NaviampConnectAuthenticatedCipherFactory,
) {
    val controller: NaviampConnectDevice get() = hello.device
    private var consumed = false

    suspend fun approve(
        nowEpochMillis: Long,
        trustedDeviceId: String,
    ): NaviampConnectPairingRuntimeResult {
        check(!consumed) { "The pairing request has already been resolved." }
        consumed = true
        var pake: NaviampConnectPakeSession? = null
        var secureSession: NaviampConnectAuthenticatedSession? = null
        return try {
            requireIdentity(localIdentity, localDevice, identityVerifier)
            val handshakeStart = pairingController.approve(nowEpochMillis)
                ?: return failure(connection, CharArray(0), NaviampConnectErrorCode.PairingExpired)
            val handshaking = handshakeStart.state
            val advertising = handshaking.advertising
            connection.sendPlaintext(
                NaviampConnectEnvelope(
                    protocolVersion = protocolVersion,
                    sessionId = advertising.pairingSessionId,
                    sequence = 0,
                    message = NaviampConnectPairingOffer(
                        advertising.pairingSessionId,
                        localDevice,
                        localIdentity,
                    ),
                ),
            )
            pake = pakeFactory.create(
                pairingSessionId = advertising.pairingSessionId,
                protocolVersion = protocolVersion,
                localRole = NaviampConnectPakeRole.Target,
                localDeviceId = localIdentity.deviceId,
                remoteDeviceId = hello.identity.deviceId,
                pairingCode = handshakeStart.pairingCode,
            )
            val secret = exchangePake(
                connection,
                pake,
                protocolVersion,
                advertising.pairingSessionId,
                firstOutboundSequence = 1,
                firstInboundSequence = 1,
            )
            pake = null
            val cipher = cipherFactory.create(
                secret,
                NaviampConnectPakeRole.Target,
                protocolVersion,
                advertising.pairingSessionId,
            )
            val channel = NaviampConnectAuthenticatedChannel(
                protocolVersion,
                advertising.pairingSessionId,
                NaviampConnectPakeRole.Target,
                cipher,
            )
            secureSession = NaviampConnectAuthenticatedSession(
                null,
                connection,
                channel,
                protocolVersion,
                advertising.pairingSessionId,
            )
            val proofPayload = naviampConnectIdentityProofPayload(
                protocolVersion,
                advertising.pairingSessionId,
                hello.identity,
                localIdentity,
            )
            try {
                secureSession.send(
                    NaviampConnectPairingIdentityProof(
                        identity = localIdentity,
                        signatureBase64 = identityEffect.signBase64(proofPayload),
                    ),
                )
                val remoteProof = secureSession.receive().message as? NaviampConnectPairingIdentityProof
                    ?: return failHandshake(secureSession, nowEpochMillis)
                if (remoteProof.identity != hello.identity ||
                    !identityVerifier.verifyProof(remoteProof, proofPayload)
                ) {
                    return failHandshake(secureSession, nowEpochMillis)
                }
                val confirmation = secureSession.receive().message as? NaviampConnectPairingConfirmation
                    ?: return failHandshake(secureSession, nowEpochMillis)
                if (confirmation.verifiedIdentityFingerprint != localIdentity.identityFingerprint) {
                    return failHandshake(secureSession, nowEpochMillis)
                }
                secureSession.send(
                    NaviampConnectPairingConfirmation(hello.identity.identityFingerprint),
                )
            } finally {
                proofPayload.fill(0)
            }
            val trust = NaviampConnectTrustRecord(
                trustedDeviceId = trustedDeviceId,
                peerDevice = hello.device,
                identityFingerprint = hello.identity.identityFingerprint,
                publicKeyBase64 = hello.identity.publicKeyBase64,
                pairedAtEpochMillis = nowEpochMillis,
            )
            pairingController.complete(trust, nowEpochMillis)
            val authenticated = secureSession.attachTrust(trust)
            secureSession = null
            NaviampConnectPairingRuntimeResult.Paired(trust, authenticated)
        } catch (_: Exception) {
            pairingController.handshakeFailed(nowEpochMillis)
            connection.close()
            NaviampConnectPairingRuntimeResult.Failed(NaviampConnectErrorCode.AuthenticationRequired)
        } finally {
            pake?.destroy()
            secureSession?.close()
        }
    }

    fun reject() {
        if (consumed) return
        consumed = true
        pairingController.reject()
        connection.close()
    }

    private fun failHandshake(
        session: NaviampConnectAuthenticatedSession,
        nowEpochMillis: Long,
    ): NaviampConnectPairingRuntimeResult.Failed {
        session.close()
        pairingController.handshakeFailed(nowEpochMillis)
        return NaviampConnectPairingRuntimeResult.Failed(NaviampConnectErrorCode.AuthenticationRequired)
    }
}

private suspend fun exchangePake(
    connection: NaviampConnectTransportConnection,
    pake: NaviampConnectPakeSession,
    protocolVersion: Int,
    pairingSessionId: String,
    firstOutboundSequence: Long,
    firstInboundSequence: Long,
): NaviampConnectSessionSecret {
    var outboundSequence = firstOutboundSequence
    var inboundSequence = firstInboundSequence
    connection.sendPlaintext(
        NaviampConnectEnvelope(
            protocolVersion,
            pairingSessionId,
            outboundSequence++,
            message = pake.start(),
        ),
    )
    while (true) {
        val envelope = connection.receivePlaintext()
        if (envelope.protocolVersion != protocolVersion || envelope.sessionId != pairingSessionId) {
            throw NaviampConnectPakeException(NaviampConnectPakeFailure.InvalidSession)
        }
        if (envelope.sequence != inboundSequence++) {
            throw NaviampConnectPakeException(NaviampConnectPakeFailure.InvalidState)
        }
        val handshake = envelope.message as? NaviampConnectPairingHandshake
            ?: throw NaviampConnectPakeException(NaviampConnectPakeFailure.InvalidPayload)
        when (val progress = pake.receive(handshake)) {
            is NaviampConnectPakeProgress.Send -> connection.sendPlaintext(
                NaviampConnectEnvelope(
                    protocolVersion,
                    pairingSessionId,
                    outboundSequence++,
                    message = progress.handshake,
                ),
            )
            is NaviampConnectPakeProgress.Complete -> return progress.sessionSecret
        }
    }
}

private suspend fun NaviampConnectTransportConnection.sendPlaintext(envelope: NaviampConnectEnvelope) {
    send(NaviampConnectTransportPacketCodec.encode(NaviampConnectTransportPacket.Plaintext(envelope)))
}

private suspend fun NaviampConnectTransportConnection.receivePlaintext(): NaviampConnectEnvelope {
    val bytes = receive() ?: throw NaviampConnectTransportException(NaviampConnectTransportFailure.Closed)
    val packet = NaviampConnectTransportPacketCodec.decode(bytes)
    return (packet as? NaviampConnectTransportPacket.Plaintext)?.envelope
        ?: throw NaviampConnectTransportException(NaviampConnectTransportFailure.InvalidFrame)
}

private fun failure(
    connection: NaviampConnectTransportConnection,
    pairingCode: CharArray,
    code: NaviampConnectErrorCode,
): NaviampConnectPairingRuntimeResult.Failed {
    pairingCode.fill('\u0000')
    connection.close()
    return NaviampConnectPairingRuntimeResult.Failed(code)
}

private fun failure(
    session: NaviampConnectAuthenticatedSession,
    code: NaviampConnectErrorCode,
): NaviampConnectPairingRuntimeResult.Failed {
    session.close()
    return NaviampConnectPairingRuntimeResult.Failed(code)
}

private fun reject(
    connection: NaviampConnectTransportConnection,
    code: NaviampConnectErrorCode,
): NaviampConnectTargetPairingRequestResult.Rejected {
    connection.close()
    return NaviampConnectTargetPairingRequestResult.Rejected(code)
}

private fun requireIdentity(
    identity: NaviampConnectPublicIdentity,
    device: NaviampConnectDevice,
    verifier: NaviampConnectIdentityVerifier,
) {
    require(identity.deviceId == device.deviceId)
    require(verifier.fingerprint(identity.publicKeyBase64) == identity.identityFingerprint)
}

private fun NaviampConnectDeviceIdentityEffect.signBase64(payload: ByteArray): String {
    val signature = sign(payload)
    return try {
        Base64.encode(signature)
    } finally {
        signature.fill(0)
    }
}

private fun NaviampConnectIdentityVerifier.verifyProof(
    proof: NaviampConnectPairingIdentityProof,
    payload: ByteArray,
): Boolean {
    if (fingerprint(proof.identity.publicKeyBase64) != proof.identity.identityFingerprint) return false
    val signature = try {
        Base64.decode(proof.signatureBase64)
    } catch (_: Exception) {
        return false
    }
    return try {
        verify(proof.identity.publicKeyBase64, payload, signature)
    } finally {
        signature.fill(0)
    }
}
