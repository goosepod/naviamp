package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectLinkOffer
import app.naviamp.domain.connect.NaviampConnectPing
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectTargetPairingState
import app.naviamp.domain.connect.naviampConnectLinkSecret
import app.naviamp.domain.connect.requiredDeviceCapability
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A scanned QR code uses the same J-PAKE, identity proof and confirmation as a typed code (#715). */
class NaviampConnectLinkPairingRuntimeTest {
    private val secret = naviampConnectLinkSecret(ByteArray(16) { (it * 13 + 5).toByte() })

    @Test fun aScannedLinkConnectsWithItsSecretAndNoCode() = runBlocking<Unit> {
        val run = scan { it }
        val controller = assertIs<NaviampConnectPairingRuntimeResult.Paired>(run.controller)
        val target = assertIs<NaviampConnectPairingRuntimeResult.Paired>(run.target)
        try {
            assertEquals(run.targetIdentity.identity.identityFingerprint, controller.trust.identityFingerprint)
            controller.session.send(NaviampConnectPing(3))
            assertEquals(NaviampConnectPing(3), target.session.receive().message)
        } finally {
            controller.session.close()
            target.session.close()
        }
    }

    @Test fun anAlteredSecretFailsTheHandshakeWithoutTrust() = runBlocking<Unit> {
        val altered = naviampConnectLinkSecret(ByteArray(16) { 9 })
        val run = scan { it.copy(secret = altered) }
        assertEquals(NaviampConnectErrorCode.AuthenticationRequired,
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(run.controller).code)
        assertIs<NaviampConnectPairingRuntimeResult.Failed>(run.target)
        assertTrue(run.policy.state !is NaviampConnectTargetPairingState.Paired)
    }

    @Test fun anEarlierOfferIsReportedAsNoLongerShownAndTheCurrentOfferSurvives() = runBlocking<Unit> {
        val run = scan(expectRequest = false) { it.copy(offerId = "an-earlier-offer") }
        assertEquals(NaviampConnectErrorCode.PairingExpired,
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(run.controller).code)
        assertEquals(NaviampConnectErrorCode.PairingExpired,
            assertIs<NaviampConnectTargetPairingRequestResult.Rejected>(run.request).code)
        assertIs<NaviampConnectTargetPairingState.Advertising>(run.policy.state,
            "The displayed code and QR are untouched, so typing the code still works")
    }

    @Test fun aDeviceThatCannotProveTheScannedIdentityIsRefused() = runBlocking<Unit> {
        val impostor = TestIdentityEffect()
        val run = scan(expectRequest = false) { it.copy(identityFingerprint = impostor.identity.identityFingerprint) }
        val failed = assertIs<NaviampConnectPairingRuntimeResult.Failed>(run.controller)
        assertEquals(NaviampConnectErrorCode.AuthenticationRequired, failed.code)
        assertEquals(NaviampConnectPairingFailureStage.PairingOffer, failed.stage)
    }

    private class Run(
        val controller: NaviampConnectPairingRuntimeResult,
        val target: NaviampConnectPairingRuntimeResult?,
        val request: NaviampConnectTargetPairingRequestResult?,
        val policy: NaviampConnectTargetPairingController,
        val targetIdentity: TestIdentityEffect,
    )

    private data class LinkFields(val identityFingerprint: String, val offerId: String, val secret: String)

    /** The target shows code [PairingCode] and a QR; the controller scans a link built from [edit]. */
    private suspend fun scan(expectRequest: Boolean = true, edit: (LinkFields) -> LinkFields): Run = kotlinx.coroutines.coroutineScope {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect()
        val transport = JvmNaviampConnectTcpTransportFactory()
        val listener = transport.listen()
        val advertisement = NaviampConnectAdvertisement(
            instanceId = "tv-instance", displayName = "Living Room", protocolRange = NaviampConnectProtocolRange(),
            capabilities = setOf(NaviampConnectCapability.TransportControls), port = listener.port,
            identityFingerprint = targetIdentity.identity.identityFingerprint, expiresAtEpochMillis = 60_000,
        )
        val policy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, nowEpochMillis = 1_000, qrSecret = secret)
        }
        val fields = edit(LinkFields(targetIdentity.identity.identityFingerprint, PairingSession, secret))
        val link = NaviampConnectLinkOffer("Living Room", listOf("127.0.0.1"), listener.port,
            fields.identityFingerprint, fields.offerId, fields.secret)
        var request: NaviampConnectTargetPairingRequestResult? = null
        val target = async(Dispatchers.Default) {
            val received = NaviampConnectTargetPairingRuntime(
                targetIdentity.device("Living Room", NaviampConnectDeviceRole.Target), targetIdentity,
                JvmNaviampConnectIdentityVerifier, policy, BouncyCastleNaviampConnectPakeFactory,
                JvmNaviampConnectAuthenticatedCipherFactory,
            ).receiveRequest(listener.accept(), 1_100)
            request = received
            if (expectRequest) assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(received)
            (received as? NaviampConnectTargetPairingRequestResult.AwaitingApproval)?.request?.approve(1_200, "phone")
        }
        val controller = async(Dispatchers.Default) {
            NaviampConnectControllerPairingRuntime(
                controllerIdentity.device("Phone", NaviampConnectDeviceRole.Controller), controllerIdentity,
                JvmNaviampConnectIdentityVerifier, transport, BouncyCastleNaviampConnectPakeFactory,
                JvmNaviampConnectAuthenticatedCipherFactory,
            ).pairWithLink("127.0.0.1", link, 1_200, "tv")
        }
        try {
            val controllerResult = controller.await()
            val targetResult = target.await()
            Run(controllerResult, targetResult, request, policy, targetIdentity)
        } finally {
            listener.close()
        }
    }

    private class TestIdentityEffect : NaviampConnectDeviceIdentityEffect {
        private val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        private val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
            .joinToString("") { "%02x".format(it) }
        val identity = NaviampConnectDeviceIdentity(digest.take(32), digest,
            Base64.getEncoder().encodeToString(keyPair.public.encoded))

        override fun loadOrCreate(): NaviampConnectDeviceIdentity = identity
        override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload)
            sign()
        }

        fun device(name: String, role: NaviampConnectDeviceRole) =
            NaviampConnectDevice(identity.deviceId, name, role, setOf(role.requiredDeviceCapability()))
    }

    private companion object {
        const val PairingSession = "pairing-session"
        const val PairingCode = "493821"
    }
}
