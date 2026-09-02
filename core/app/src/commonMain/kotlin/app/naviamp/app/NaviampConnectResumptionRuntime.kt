package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectPairingConfirmation
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectResumeHello
import app.naviamp.domain.connect.NaviampConnectResumeOffer
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.forPeerSessionRole
import app.naviamp.domain.connect.negotiateNaviampConnectProtocol

sealed interface NaviampConnectResumptionResult {
    data class Connected(
        val trust: NaviampConnectTrustRecord,
        val session: NaviampConnectAuthenticatedSession,
    ) : NaviampConnectResumptionResult

    data class Failed(val code: NaviampConnectErrorCode) : NaviampConnectResumptionResult
}

/** Opens a fresh authenticated channel from an already approved controller trust. */
class NaviampConnectControllerResumptionRuntime(
    private val localDevice: NaviampConnectDevice,
    private val identityEffect: NaviampConnectDeviceIdentityEffect,
    private val identityVerifier: NaviampConnectIdentityVerifier,
    private val transportFactory: NaviampConnectTransportFactory,
    private val cipherFactory: NaviampConnectAuthenticatedCipherFactory,
    private val localProtocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
) {
    suspend fun reconnect(
        host: String,
        advertisement: NaviampConnectAdvertisement,
        trust: NaviampConnectTrustRecord,
        credential: ByteArray,
        onConnectionOpened: (NaviampConnectTransportConnection) -> Unit = {},
    ): NaviampConnectResumptionResult {
        var connection: NaviampConnectTransportConnection? = null
        var session: NaviampConnectAuthenticatedSession? = null
        return try {
            require(localDevice.role == NaviampConnectDeviceRole.Controller)
            val sessionTrust = trust.forPeerSessionRole(NaviampConnectDeviceRole.Target)
            val localIdentity = identityEffect.loadOrCreate().asPublicIdentity()
            requireIdentityForResume(localIdentity, localDevice, identityVerifier)
            val protocolVersion = negotiateNaviampConnectProtocol(localProtocolRange, advertisement.protocolRange)
                ?: return NaviampConnectResumptionResult.Failed(NaviampConnectErrorCode.IncompatibleProtocol)
            connection = transportFactory.connect(host, advertisement.port)
            onConnectionOpened(connection)
            connection.sendPlaintext(
                NaviampConnectEnvelope(
                    protocolVersion = protocolVersion,
                    sequence = 0,
                    message = NaviampConnectResumeHello(localDevice, localIdentity, localProtocolRange),
                ),
            )
            val offerEnvelope = connection.receivePlaintext()
            val offer = offerEnvelope.message as? NaviampConnectResumeOffer
                ?: return failed(connection, NaviampConnectErrorCode.AuthenticationRequired)
            if (offerEnvelope.sequence != 0L ||
                offerEnvelope.sessionId != offer.sessionId ||
                offerEnvelope.protocolVersion != protocolVersion ||
                offer.protocolVersion != protocolVersion ||
                !offer.target.matchesTrustedPeerForResume(sessionTrust.peerDevice) ||
                offer.identity.deviceId != sessionTrust.peerDevice.deviceId ||
                offer.identity.identityFingerprint != trust.identityFingerprint ||
                offer.identity.publicKeyBase64 != trust.publicKeyBase64 ||
                identityVerifier.fingerprint(offer.identity.publicKeyBase64) != offer.identity.identityFingerprint ||
                advertisement.identityFingerprint != trust.identityFingerprint
            ) {
                return failed(connection, NaviampConnectErrorCode.AuthenticationRequired)
            }
            val channel = resumeChannel(
                credential = credential,
                role = NaviampConnectPakeRole.Controller,
                protocolVersion = protocolVersion,
                sessionId = offer.sessionId,
                cipherFactory = cipherFactory,
            )
            val refreshedTrust = sessionTrust.copy(
                peerDevice = offer.target,
                displayName = offer.target.displayName,
            )
            session = NaviampConnectAuthenticatedSession(
                refreshedTrust,
                connection,
                channel,
                protocolVersion,
                offer.sessionId,
            )
            connection = null
            val targetConfirmation = session.receive().message as? NaviampConnectPairingConfirmation
                ?: return failed(session, NaviampConnectErrorCode.AuthenticationRequired)
            if (targetConfirmation.verifiedIdentityFingerprint != localIdentity.identityFingerprint) {
                return failed(session, NaviampConnectErrorCode.AuthenticationRequired)
            }
            session.send(NaviampConnectPairingConfirmation(trust.identityFingerprint))
            NaviampConnectResumptionResult.Connected(refreshedTrust, session).also { session = null }
        } catch (_: Exception) {
            NaviampConnectResumptionResult.Failed(NaviampConnectErrorCode.AuthenticationRequired)
        } finally {
            credential.fill(0)
            session?.close()
            connection?.close()
        }
    }
}

/** Accepts a remembered controller without minting or modifying trust. */
class NaviampConnectTargetResumptionRuntime(
    private val localDevice: NaviampConnectDevice,
    private val identityEffect: NaviampConnectDeviceIdentityEffect,
    private val identityVerifier: NaviampConnectIdentityVerifier,
    private val cipherFactory: NaviampConnectAuthenticatedCipherFactory,
    private val localProtocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
) {
    suspend fun reconnect(
        connection: NaviampConnectTransportConnection,
        helloEnvelope: NaviampConnectEnvelope,
        advertisement: NaviampConnectAdvertisement,
        trust: NaviampConnectTrustRecord,
        credential: ByteArray,
        sessionId: String,
    ): NaviampConnectResumptionResult {
        var session: NaviampConnectAuthenticatedSession? = null
        var retainedConnection: NaviampConnectTransportConnection? = connection
        return try {
            require(localDevice.role == NaviampConnectDeviceRole.Target)
            val sessionTrust = trust.forPeerSessionRole(NaviampConnectDeviceRole.Controller)
            val hello = helloEnvelope.message as? NaviampConnectResumeHello
                ?: return failed(connection, NaviampConnectErrorCode.InvalidRequest)
            val protocolVersion = negotiateNaviampConnectProtocol(localProtocolRange, hello.protocolRange)
                ?: return failed(connection, NaviampConnectErrorCode.IncompatibleProtocol)
            if (helloEnvelope.sequence != 0L || helloEnvelope.sessionId != null ||
                helloEnvelope.protocolVersion != protocolVersion ||
                !hello.device.matchesTrustedPeerForResume(sessionTrust.peerDevice) ||
                hello.identity.deviceId != sessionTrust.peerDevice.deviceId ||
                hello.identity.identityFingerprint != trust.identityFingerprint ||
                hello.identity.publicKeyBase64 != trust.publicKeyBase64 ||
                identityVerifier.fingerprint(hello.identity.publicKeyBase64) != hello.identity.identityFingerprint
            ) {
                return failed(connection, NaviampConnectErrorCode.AuthenticationRequired)
            }
            val localIdentity = identityEffect.loadOrCreate().asPublicIdentity()
            requireIdentityForResume(localIdentity, localDevice, identityVerifier)
            if (advertisement.identityFingerprint != localIdentity.identityFingerprint ||
                protocolVersion !in advertisement.protocolRange.minimum..advertisement.protocolRange.maximum
            ) {
                return failed(connection, NaviampConnectErrorCode.AuthenticationRequired)
            }
            connection.sendPlaintext(
                NaviampConnectEnvelope(
                    protocolVersion = protocolVersion,
                    sessionId = sessionId,
                    sequence = 0,
                    message = NaviampConnectResumeOffer(sessionId, protocolVersion, localDevice, localIdentity),
                ),
            )
            val channel = resumeChannel(
                credential = credential,
                role = NaviampConnectPakeRole.Target,
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                cipherFactory = cipherFactory,
            )
            val refreshedTrust = sessionTrust.copy(
                peerDevice = hello.device,
                displayName = hello.device.displayName,
            )
            session = NaviampConnectAuthenticatedSession(
                refreshedTrust,
                connection,
                channel,
                protocolVersion,
                sessionId,
            )
            retainedConnection = null
            session.send(NaviampConnectPairingConfirmation(trust.identityFingerprint))
            val controllerConfirmation = session.receive().message as? NaviampConnectPairingConfirmation
                ?: return failed(session, NaviampConnectErrorCode.AuthenticationRequired)
            if (controllerConfirmation.verifiedIdentityFingerprint != localIdentity.identityFingerprint) {
                return failed(session, NaviampConnectErrorCode.AuthenticationRequired)
            }
            NaviampConnectResumptionResult.Connected(refreshedTrust, session).also { session = null }
        } catch (_: Exception) {
            NaviampConnectResumptionResult.Failed(NaviampConnectErrorCode.AuthenticationRequired)
        } finally {
            credential.fill(0)
            session?.close()
            retainedConnection?.close()
        }
    }
}

private fun resumeChannel(
    credential: ByteArray,
    role: NaviampConnectPakeRole,
    protocolVersion: Int,
    sessionId: String,
    cipherFactory: NaviampConnectAuthenticatedCipherFactory,
): NaviampConnectAuthenticatedChannel {
    val secret = NaviampConnectSessionSecret(credential)
    val cipher = cipherFactory.create(secret, role, protocolVersion, sessionId)
    return NaviampConnectAuthenticatedChannel(protocolVersion, sessionId, role, cipher)
}

private fun requireIdentityForResume(
    identity: app.naviamp.domain.connect.NaviampConnectPublicIdentity,
    device: NaviampConnectDevice,
    verifier: NaviampConnectIdentityVerifier,
) {
    require(identity.deviceId == device.deviceId)
    require(verifier.fingerprint(identity.publicKeyBase64) == identity.identityFingerprint)
}

/**
 * Trust is pinned to the peer's cryptographic identity, stable device ID, and session role.
 * Display names and advertised capabilities are mutable device metadata and may evolve between
 * app versions without forcing the user to pair the same identity again.
 */
private fun NaviampConnectDevice.matchesTrustedPeerForResume(
    trustedPeer: NaviampConnectDevice,
): Boolean = deviceId == trustedPeer.deviceId && role == trustedPeer.role

private fun failed(
    connection: NaviampConnectTransportConnection,
    code: NaviampConnectErrorCode,
): NaviampConnectResumptionResult.Failed {
    connection.close()
    return NaviampConnectResumptionResult.Failed(code)
}

private fun failed(
    session: NaviampConnectAuthenticatedSession,
    code: NaviampConnectErrorCode,
): NaviampConnectResumptionResult.Failed {
    session.close()
    return NaviampConnectResumptionResult.Failed(code)
}
