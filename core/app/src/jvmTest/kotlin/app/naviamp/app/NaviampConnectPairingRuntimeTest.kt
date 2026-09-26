package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceCapability
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectPing
import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectPairingRuntimeTest {
    @Test
    fun manualAddressUsesTheSameCodeApprovalAndVerifiedIdentity() = runBlocking {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect()
        val capabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback, NaviampConnectDeviceCapability.PlaybackTarget)
        val controllerDevice = controllerIdentity.device("Controller", NaviampConnectDeviceRole.Controller, capabilities)
        val targetDevice = targetIdentity.device("Target", NaviampConnectDeviceRole.Target, capabilities)
        val transport = JvmNaviampConnectTcpTransportFactory()
        val listener = transport.listen()
        val advertisement = advertisement(listener.port, targetIdentity.identity.identityFingerprint)
        val policy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, nowEpochMillis = 1_000)
        }
        val target = async(Dispatchers.Default) {
            val request = assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(
                targetRuntime(targetDevice, targetIdentity, policy).receiveRequest(listener.accept(), 1_100),
            )
            request.request.approve(1_200, "controller-trust")
        }
        val controller = async(Dispatchers.Default) {
            controllerRuntime(controllerDevice, controllerIdentity, transport).pairManual(
                "127.0.0.1", listener.port, PairingCode.toCharArray(), 1_200, "target-trust",
            )
        }
        val pairedController = assertIs<NaviampConnectPairingRuntimeResult.Paired>(controller.await())
        val pairedTarget = assertIs<NaviampConnectPairingRuntimeResult.Paired>(target.await())
        try {
            assertEquals(targetIdentity.identity.identityFingerprint, pairedController.trust.identityFingerprint)
            assertContentEquals(pairedController.resumptionCredential, pairedTarget.resumptionCredential)
            pairedController.session.send(NaviampConnectPing(7))
            assertEquals(NaviampConnectPing(7), pairedTarget.session.receive().message)
        } finally {
            pairedController.session.close()
            pairedTarget.session.close()
            listener.close()
        }
    }

    @Test
    fun completesIdentityBoundPairingAndRetainsAuthenticatedSession(): Unit = runBlocking {
        val controllerIdentity = TestIdentityEffect()
        val targetIdentity = TestIdentityEffect()
        val deviceCapabilities = setOf(
            NaviampConnectDeviceCapability.ControlPlayback,
            NaviampConnectDeviceCapability.PlaybackTarget,
        )
        val controllerDevice = controllerIdentity.device(
            "Pixel",
            NaviampConnectDeviceRole.Controller,
            deviceCapabilities,
        )
        val targetDevice = targetIdentity.device(
            "Living Room",
            NaviampConnectDeviceRole.Target,
            deviceCapabilities,
        )
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
            val firstApplicationEnvelope = targetPaired.session.receive()
            assertEquals(NaviampConnectPing(42), firstApplicationEnvelope.message)

            controllerPaired.session.sendOrderedEnvelope(
                NaviampConnectEnvelope(
                    protocolVersion = 1,
                    sessionId = controllerPaired.session.sessionId,
                    sequence = 99,
                    message = NaviampConnectPing(43),
                ),
            )
            val ordered = targetPaired.session.receive()
            assertEquals(firstApplicationEnvelope.sequence + 1L, ordered.sequence)
            assertEquals(NaviampConnectPing(43), ordered.message)

            controllerPaired.session.close()
            targetPaired.session.close()
            listener.close()
            val resumeListener = factory.listen()
            val resumeAdvertisement = advertisement(resumeListener.port, targetIdentity.identity.identityFingerprint)
            val evolvedControllerDevice = controllerDevice.copy(
                displayName = "Renamed Pixel",
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
            )
            val evolvedTargetDevice = targetDevice.copy(
                displayName = "Renamed Living Room",
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.PlaybackTarget),
            )
            val resumedTarget = async(Dispatchers.Default) {
                val connection = resumeListener.accept()
                NaviampConnectTargetResumptionRuntime(
                    evolvedTargetDevice,
                    targetIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    connection = connection,
                    helloEnvelope = connection.receivePlaintext(),
                    advertisement = resumeAdvertisement,
                    trust = targetPaired.trust,
                    credential = targetPaired.resumptionCredential.copyOf(),
                    targetNonce = "resumed-target-nonce",
                )
            }
            val resumedController = async(Dispatchers.Default) {
                NaviampConnectControllerResumptionRuntime(
                    evolvedControllerDevice,
                    controllerIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    factory,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    host = "127.0.0.1",
                    advertisement = resumeAdvertisement,
                    trust = controllerPaired.trust,
                    credential = controllerPaired.resumptionCredential.copyOf(),
                    controllerNonce = "resume-controller-nonce",
                )
            }
            val controllerResumed = assertIs<NaviampConnectResumptionResult.Connected>(resumedController.await())
            val targetResumed = assertIs<NaviampConnectResumptionResult.Connected>(resumedTarget.await())
            try {
                assertEquals("Renamed Living Room", controllerResumed.trust.peerDevice.displayName)
                assertEquals("Renamed Pixel", targetResumed.trust.peerDevice.displayName)
                controllerResumed.session.send(NaviampConnectPing(84))
                assertEquals(NaviampConnectPing(84), targetResumed.session.receive().message)
            } finally {
                controllerResumed.session.close()
                targetResumed.session.close()
                resumeListener.close()
            }

            val reverseListener = factory.listen()
            val reverseAdvertisement = advertisement(
                reverseListener.port,
                controllerIdentity.identity.identityFingerprint,
            )
            val reversedTarget = async(Dispatchers.Default) {
                val connection = reverseListener.accept()
                NaviampConnectTargetResumptionRuntime(
                    controllerDevice.copy(role = NaviampConnectDeviceRole.Target),
                    controllerIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    connection = connection,
                    helloEnvelope = connection.receivePlaintext(),
                    advertisement = reverseAdvertisement,
                    trust = controllerPaired.trust,
                    credential = controllerPaired.resumptionCredential.copyOf(),
                    targetNonce = "reverse-target-nonce",
                )
            }
            val reversedController = async(Dispatchers.Default) {
                NaviampConnectControllerResumptionRuntime(
                    targetDevice.copy(role = NaviampConnectDeviceRole.Controller),
                    targetIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    factory,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    host = "127.0.0.1",
                    advertisement = reverseAdvertisement,
                    trust = targetPaired.trust,
                    credential = targetPaired.resumptionCredential.copyOf(),
                    controllerNonce = "reverse-controller-nonce",
                )
            }
            val reverseControllerConnected =
                assertIs<NaviampConnectResumptionResult.Connected>(reversedController.await())
            val reverseTargetConnected =
                assertIs<NaviampConnectResumptionResult.Connected>(reversedTarget.await())
            try {
                assertEquals(
                    NaviampConnectDeviceRole.Target,
                    reverseControllerConnected.trust.peerDevice.role,
                )
                assertEquals(
                    NaviampConnectDeviceRole.Controller,
                    reverseTargetConnected.trust.peerDevice.role,
                )
                reverseControllerConnected.session.send(NaviampConnectPing(126))
                assertEquals(NaviampConnectPing(126), reverseTargetConnected.session.receive().message)
            } finally {
                reverseControllerConnected.session.close()
                reverseTargetConnected.session.close()
                reverseListener.close()
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
                    targetNonce = "rejected-target-nonce",
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
                    controllerNonce = "rejected-controller-nonce",
                )
            }
            try {
                assertIs<NaviampConnectResumptionResult.Failed>(rejectedController.await())
                assertIs<NaviampConnectResumptionResult.Failed>(rejectedTarget.await())
            } finally {
                rejectedListener.close()
            }

            val replayListener = factory.listen()
            val replayAdvertisement = advertisement(
                replayListener.port,
                targetIdentity.identity.identityFingerprint,
            )
            val replayingTarget = async(Dispatchers.Default) {
                val connection = replayListener.accept()
                val hello = assertIs<app.naviamp.domain.connect.NaviampConnectResumeHello>(
                    connection.receivePlaintext().message,
                )
                assertEquals("fresh-controller-nonce", hello.controllerNonce)
                connection.sendPlaintext(
                    NaviampConnectEnvelope(
                        protocolVersion = 1,
                        sessionId = "recorded-session",
                        sequence = 0,
                        message = app.naviamp.domain.connect.NaviampConnectResumeOffer(
                            sessionId = "recorded-session",
                            controllerNonce = "recorded-controller-nonce",
                            protocolVersion = 1,
                            target = targetDevice,
                            identity = targetIdentity.identity.asPublicIdentity(),
                        ),
                    ),
                )
                connection.close()
            }
            val replayedController = async(Dispatchers.Default) {
                NaviampConnectControllerResumptionRuntime(
                    controllerDevice,
                    controllerIdentity,
                    JvmNaviampConnectIdentityVerifier,
                    factory,
                    JvmNaviampConnectAuthenticatedCipherFactory,
                ).reconnect(
                    host = "127.0.0.1",
                    advertisement = replayAdvertisement,
                    trust = controllerPaired.trust,
                    credential = controllerPaired.resumptionCredential.copyOf(),
                    controllerNonce = "fresh-controller-nonce",
                )
            }
            try {
                assertEquals(
                    NaviampConnectErrorCode.AuthenticationRequired,
                    assertIs<NaviampConnectResumptionResult.Failed>(replayedController.await()).code,
                )
                replayingTarget.await()
            } finally {
                replayListener.close()
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

        fun device(
            name: String,
            role: NaviampConnectDeviceRole,
            capabilities: Set<NaviampConnectDeviceCapability> = setOf(role.requiredDeviceCapability()),
        ) = NaviampConnectDevice(identity.deviceId, name, role, capabilities)

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PairingSession = "pairing-session"
        const val PairingCode = "493821"
    }
}
