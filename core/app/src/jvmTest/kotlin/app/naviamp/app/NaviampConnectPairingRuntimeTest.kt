package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectPing
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectPairingRuntimeTest {
    @Test
    fun completesIdentityBoundPairingAndRetainsAuthenticatedSession(): Unit = runBlocking {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect()
        val controllerDevice = controllerIdentity.device("Pixel", NaviampConnectDeviceRole.Controller)
        val targetDevice = targetIdentity.device("Living Room", NaviampConnectDeviceRole.Target)
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val advertisement = advertisement(listener.port, targetIdentity.identity.identityFingerprint)
        val targetPolicy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, nowEpochMillis = 1_000)
        }
        val targetRuntime = targetRuntime(targetDevice, targetIdentity, targetPolicy)
        val controllerRuntime = controllerRuntime(controllerDevice, controllerIdentity, factory)

        val target = async(Dispatchers.Default) {
            val connection = listener.accept()
            val awaiting = assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(
                targetRuntime.receiveRequest(connection, nowEpochMillis = 1_100),
            )
            awaiting.request.approve(nowEpochMillis = 1_200, trustedDeviceId = "controller-trust")
        }
        val controller = async(Dispatchers.Default) {
            controllerRuntime.pair(
                host = "127.0.0.1",
                advertisement = advertisement,
                pairingCode = PairingCode.toCharArray(),
                pairedAtEpochMillis = 1_200,
                trustedDeviceId = "target-trust",
            )
        }

        val controllerPaired = assertIs<NaviampConnectPairingRuntimeResult.Paired>(controller.await())
        val targetPaired = assertIs<NaviampConnectPairingRuntimeResult.Paired>(target.await())
        try {
            assertEquals(targetDevice, controllerPaired.trust.peerDevice)
            assertEquals(controllerDevice, targetPaired.trust.peerDevice)
            assertEquals(targetIdentity.identity.publicKeyBase64, controllerPaired.trust.publicKeyBase64)
            assertEquals(controllerIdentity.identity.publicKeyBase64, targetPaired.trust.publicKeyBase64)
            assertContentEquals(controllerPaired.resumptionCredential, targetPaired.resumptionCredential)

            controllerPaired.session.send(NaviampConnectPing(42))
            assertEquals(NaviampConnectPing(42), targetPaired.session.receive().message)

            controllerPaired.session.close()
            targetPaired.session.close()
            listener.close()
            val resumeListener = factory.listen()
            val resumeAdvertisement = advertisement(resumeListener.port, targetIdentity.identity.identityFingerprint)
            val resumedTarget = async(Dispatchers.Default) {
                val connection = resumeListener.accept()
                NaviampConnectTargetResumptionRuntime(
                    targetDevice,
                    targetIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    connection = connection,
                    helloEnvelope = connection.receivePlaintext(),
                    advertisement = resumeAdvertisement,
                    trust = targetPaired.trust,
                    credential = targetPaired.resumptionCredential.copyOf(),
                    sessionId = "resumed-session",
                )
            }
            val resumedController = async(Dispatchers.Default) {
                NaviampConnectControllerResumptionRuntime(
                    controllerDevice,
                    controllerIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    factory,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    host = "127.0.0.1",
                    advertisement = resumeAdvertisement,
                    trust = controllerPaired.trust,
                    credential = controllerPaired.resumptionCredential.copyOf(),
                )
            }
            val controllerResumed = assertIs<NaviampConnectResumptionResult.Connected>(resumedController.await())
            val targetResumed = assertIs<NaviampConnectResumptionResult.Connected>(resumedTarget.await())
            try {
                controllerResumed.session.send(NaviampConnectPing(84))
                assertEquals(NaviampConnectPing(84), targetResumed.session.receive().message)
            } finally {
                controllerResumed.session.close()
                targetResumed.session.close()
                resumeListener.close()
            }

            val rejectedListener = factory.listen()
            val rejectedAdvertisement = advertisement(
                rejectedListener.port,
                targetIdentity.identity.identityFingerprint,
            )
            val rejectedTarget = async(Dispatchers.Default) {
                val connection = rejectedListener.accept()
                NaviampConnectTargetResumptionRuntime(
                    targetDevice,
                    targetIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    connection = connection,
                    helloEnvelope = connection.receivePlaintext(),
                    advertisement = rejectedAdvertisement,
                    trust = targetPaired.trust,
                    credential = targetPaired.resumptionCredential.copyOf(),
                    sessionId = "rejected-session",
                )
            }
            val rejectedController = async(Dispatchers.Default) {
                NaviampConnectControllerResumptionRuntime(
                    controllerDevice,
                    controllerIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    factory,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    host = "127.0.0.1",
                    advertisement = rejectedAdvertisement,
                    trust = controllerPaired.trust,
                    credential = ByteArray(controllerPaired.resumptionCredential.size) { 0x5a },
                )
            }
            try {
                assertIs<NaviampConnectResumptionResult.Failed>(rejectedController.await())
                assertIs<NaviampConnectResumptionResult.Failed>(rejectedTarget.await())
            } finally {
                rejectedListener.close()
            }
        } finally {
            controllerPaired.resumptionCredential.fill(0)
            targetPaired.resumptionCredential.fill(0)
            controllerPaired.session.close()
            targetPaired.session.close()
            listener.close()
        }
    }

    @Test
    fun wrongCodeFailsWithoutCreatingTrust() = runBlocking {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect()
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val advertisement = advertisement(listener.port, targetIdentity.identity.identityFingerprint)
        val targetPolicy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, nowEpochMillis = 1_000)
        }
        val targetRuntime = targetRuntime(
            targetIdentity.device("Living Room", NaviampConnectDeviceRole.Target),
            targetIdentity,
            targetPolicy,
        )
        val controllerRuntime = controllerRuntime(
            controllerIdentity.device("Pixel", NaviampConnectDeviceRole.Controller),
            controllerIdentity,
            factory,
        )

        val target = async(Dispatchers.Default) {
            val awaiting = assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(
                targetRuntime.receiveRequest(listener.accept(), nowEpochMillis = 1_100),
            )
            awaiting.request.approve(nowEpochMillis = 1_200, trustedDeviceId = "controller-trust")
        }
        val controller = async(Dispatchers.Default) {
            controllerRuntime.pair(
                "127.0.0.1",
                advertisement,
                "000000".toCharArray(),
                pairedAtEpochMillis = 1_200,
                trustedDeviceId = "target-trust",
            )
        }

        try {
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(controller.await())
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(target.await())
            assertTrue(targetPolicy.state !is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Paired)
        } finally {
            listener.close()
        }
    }

    @Test
    fun invalidTargetIdentityProofFailsBothPeersBeforeTrust() = runBlocking {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect(corruptSignatures = true)
        val factory = JvmNaviampConnectTcpTransportFactory()
        val listener = factory.listen()
        val advertisement = advertisement(listener.port, targetIdentity.identity.identityFingerprint)
        val targetPolicy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, nowEpochMillis = 1_000)
        }
        val targetRuntime = targetRuntime(
            targetIdentity.device("Living Room", NaviampConnectDeviceRole.Target),
            targetIdentity,
            targetPolicy,
        )
        val controllerRuntime = controllerRuntime(
            controllerIdentity.device("Pixel", NaviampConnectDeviceRole.Controller),
            controllerIdentity,
            factory,
        )

        val target = async(Dispatchers.Default) {
            val awaiting = assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(
                targetRuntime.receiveRequest(listener.accept(), nowEpochMillis = 1_100),
            )
            awaiting.request.approve(nowEpochMillis = 1_200, trustedDeviceId = "controller-trust")
        }
        val controller = async(Dispatchers.Default) {
            controllerRuntime.pair(
                "127.0.0.1",
                advertisement,
                PairingCode.toCharArray(),
                pairedAtEpochMillis = 1_200,
                trustedDeviceId = "target-trust",
            )
        }

        try {
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(controller.await())
            assertIs<NaviampConnectPairingRuntimeResult.Failed>(target.await())
            assertTrue(targetPolicy.state !is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Paired)
        } finally {
            listener.close()
        }
    }

    private fun controllerRuntime(
        device: NaviampConnectDevice,
        identity: TestIdentityEffect,
        transport: NaviampConnectTransportFactory,
    ) = NaviampConnectControllerPairingRuntime(
        device,
        identity,
        JvmNaviampConnectIdentityVerifier,
        transport,
        BouncyCastleNaviampConnectPakeFactory,
        JvmNaviampConnectAuthenticatedCipherFactory,
    )

    private fun targetRuntime(
        device: NaviampConnectDevice,
        identity: TestIdentityEffect,
        policy: NaviampConnectTargetPairingController,
    ) = NaviampConnectTargetPairingRuntime(
        device,
        identity,
        JvmNaviampConnectIdentityVerifier,
        policy,
        BouncyCastleNaviampConnectPakeFactory,
        JvmNaviampConnectAuthenticatedCipherFactory,
    )

    private fun advertisement(port: Int, fingerprint: String) = NaviampConnectAdvertisement(
        instanceId = "living-room-instance",
        displayName = "Living Room",
        protocolRange = NaviampConnectProtocolRange(),
        capabilities = setOf(NaviampConnectCapability.TransportControls),
        port = port,
        identityFingerprint = fingerprint,
        expiresAtEpochMillis = 60_000,
    )

    private class TestIdentityEffect(
        private val corruptSignatures: Boolean = false,
    ) : NaviampConnectDeviceIdentityEffect {
        private val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val identity = NaviampConnectDeviceIdentity(
            deviceId = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded).toHex().take(32),
            identityFingerprint = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded).toHex(),
            publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        )

        override fun loadOrCreate(): NaviampConnectDeviceIdentity = identity

        override fun sign(payload: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(payload)
                sign()
            }.also { signature ->
                if (corruptSignatures) signature[signature.lastIndex] = (signature.last().toInt() xor 1).toByte()
            }

        fun device(name: String, role: NaviampConnectDeviceRole) =
            NaviampConnectDevice(identity.deviceId, name, role)

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PairingSession = "pairing-session"
        const val PairingCode = "493821"
    }
}
