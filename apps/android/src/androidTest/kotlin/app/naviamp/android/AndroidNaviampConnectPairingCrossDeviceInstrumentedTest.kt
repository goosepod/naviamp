package app.naviamp.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.app.BouncyCastleNaviampConnectPakeFactory
import app.naviamp.app.JvmNaviampConnectAuthenticatedCipherFactory
import app.naviamp.app.JvmNaviampConnectIdentityVerifier
import app.naviamp.app.JvmNaviampConnectTcpTransportFactory
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectControllerPairingRuntime
import app.naviamp.app.NaviampConnectControllerSession
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectPairingRuntimeResult
import app.naviamp.app.NaviampConnectRequestIdFactory
import app.naviamp.app.NaviampConnectSessionTransport
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.app.NaviampConnectTargetPairingRequestResult
import app.naviamp.app.NaviampConnectTargetPairingRuntime
import app.naviamp.app.NaviampConnectTargetCommandExecutor
import app.naviamp.app.NaviampConnectTargetCommandResult
import app.naviamp.app.NaviampConnectTargetSession
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectPlaybackSnapshot
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectQueueSnapshot
import app.naviamp.domain.connect.NaviampConnectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectProvisioningProfile
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectWelcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectPairingCrossDeviceInstrumentedTest {
    @Test
    fun pairAccordingToRequestedDeviceRole() {
        when (InstrumentationRegistry.getArguments().getString("connectRole")) {
            "target" -> runTarget()
            "controller" -> runController()
            else -> error("The cross-device pairing test requires -e connectRole target|controller.")
        }
    }

    private fun runTarget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val identityEffect = AndroidNaviampConnectDeviceIdentityEffect()
        val identity = identityEffect.loadOrCreate()
        val transport = JvmNaviampConnectTcpTransportFactory()
        val listener = transport.listen(PairingPort)
        val advertisement = advertisement(PairingPort, identity.identityFingerprint)
        val registration = CountDownLatch(1)
        val advertising = AndroidNaviampConnectAdvertisingEffect(context)
        val start = advertising.start(
            NaviampConnectRegistrationService(
                serviceName = "Naviamp Pairing TV",
                port = PairingPort,
                textAttributes = NaviampConnectDiscoveryMetadata.encode(advertisement),
            ),
            object : NaviampConnectAdvertisingListener {
                override fun onServiceRegistered(registeredServiceName: String) = registration.countDown()
                override fun onRegistrationFailed(message: String) = registration.countDown()
            },
        )
        assertEquals(NaviampConnectAdvertisingStartResult.Started, start)
        assertTrue(registration.await(5, TimeUnit.SECONDS), "TV pairing advertisement timed out.")

        val policy = NaviampConnectTargetPairingController().apply {
            start(advertisement, PairingSession, PairingCode, System.currentTimeMillis())
        }
        val runtime = NaviampConnectTargetPairingRuntime(
            localDevice = NaviampConnectDevice(identity.deviceId, "Android TV Emulator", NaviampConnectDeviceRole.Target),
            identityEffect = identityEffect,
            identityVerifier = JvmNaviampConnectIdentityVerifier,
            pairingController = policy,
            pakeFactory = BouncyCastleNaviampConnectPakeFactory,
            cipherFactory = JvmNaviampConnectAuthenticatedCipherFactory,
        )
        try {
            runBlocking {
                val connection = withTimeout(30_000) { listener.accept() }
                val awaiting = assertIs<NaviampConnectTargetPairingRequestResult.AwaitingApproval>(
                    runtime.receiveRequest(connection, System.currentTimeMillis()),
                )
                val paired = assertIs<NaviampConnectPairingRuntimeResult.Paired>(
                    awaiting.request.approve(System.currentTimeMillis(), "pixel-controller"),
                )
                try {
                    val initial = snapshot(runtimeTargetDevice(identity.deviceId), revision = 0)
                    paired.session.send(
                        NaviampConnectWelcome(
                            sessionId = paired.session.sessionId,
                            protocolVersion = paired.session.protocolVersion,
                            target = initial.target,
                            capabilities = initial.capabilities,
                            snapshot = initial,
                        ),
                    )
                    val target = NaviampConnectTargetSession(
                        sessionId = paired.session.sessionId,
                        protocolVersion = paired.session.protocolVersion,
                        initialSnapshot = initial,
                        transport = NaviampConnectSessionTransport(paired.session::sendEnvelope),
                        executor = NaviampConnectTargetCommandExecutor { command, current ->
                            val updated = when (command) {
                                NaviampConnectPlay -> current.copy(
                                    revision = current.revision + 1,
                                    playback = current.playback.copy(state = NaviampConnectPlaybackState.Playing),
                                )
                                is NaviampConnectHandoffQueue -> current.copy(
                                    revision = current.revision + 1,
                                    queue = command.queue,
                                    playback = current.playback.copy(
                                        state = if (command.playing) {
                                            NaviampConnectPlaybackState.Playing
                                        } else {
                                            NaviampConnectPlaybackState.Paused
                                        },
                                        currentOccurrenceId = command.queue.occurrences[
                                            command.queue.currentIndex
                                        ].occurrenceId,
                                        positionMillis = command.positionMillis,
                                    ),
                                )
                                is NaviampConnectStartMedia -> current.copy(
                                    revision = current.revision + 1,
                                    playback = current.playback.copy(state = NaviampConnectPlaybackState.Playing),
                                )
                                is NaviampConnectOfferConnectionProvisioning -> current.copy(
                                    revision = current.revision + 1,
                                )
                                else -> error("Unexpected physical acceptance command: $command")
                            }
                            NaviampConnectTargetCommandResult.Success(
                                snapshot = updated,
                                changed = true,
                            )
                        },
                        initialOutboundSequence = paired.session.nextOutboundSequence(),
                    )
                    repeat(5) { target.receive(paired.session.receive()) }
                    assertEquals(NaviampConnectPlaybackState.Playing, target.snapshot().playback.state)
                    assertEquals("physical-track", target.snapshot().queue.occurrences.single().mediaId)
                } finally {
                    paired.session.close()
                }
            }
        } finally {
            advertising.stop()
            listener.close()
        }
    }

    private fun runController() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolved = AtomicReference<NaviampConnectResolvedService?>()
        val discovered = CountDownLatch(1)
        val discovery = AndroidNaviampConnectDiscoveryEffect(context)
        val start = discovery.start(
            object : NaviampConnectDiscoveryListener {
                override fun onServiceResolved(service: NaviampConnectResolvedService) {
                    val decoded = NaviampConnectDiscoveryMetadata.decode(
                        service.textAttributes,
                        service.port,
                        Long.MAX_VALUE,
                    )
                    if (decoded?.instanceId == PairingInstance) {
                        resolved.set(service)
                        discovered.countDown()
                    }
                }

                override fun onServiceLost(serviceName: String) = Unit
                override fun onDiscoveryFailed(message: String) = discovered.countDown()
            },
        )
        assertEquals(NaviampConnectDiscoveryStartResult.Started, start)
        assertTrue(discovered.await(20, TimeUnit.SECONDS), "Pixel pairing discovery timed out.")
        discovery.stop()
        val service = requireNotNull(resolved.get()) { "Pixel did not resolve the pairing target." }
        val advertisement = requireNotNull(
            NaviampConnectDiscoveryMetadata.decode(service.textAttributes, service.port, Long.MAX_VALUE),
        )
        val identityEffect = AndroidNaviampConnectDeviceIdentityEffect()
        val identity = identityEffect.loadOrCreate()
        val runtime = NaviampConnectControllerPairingRuntime(
            localDevice = NaviampConnectDevice(identity.deviceId, "Pixel 10a", NaviampConnectDeviceRole.Controller),
            identityEffect = identityEffect,
            identityVerifier = JvmNaviampConnectIdentityVerifier,
            transportFactory = JvmNaviampConnectTcpTransportFactory(),
            pakeFactory = BouncyCastleNaviampConnectPakeFactory,
            cipherFactory = JvmNaviampConnectAuthenticatedCipherFactory,
        )
        val connectHost = InstrumentationRegistry.getArguments().getString("connectHost")
            ?.takeIf(String::isNotBlank)
            ?: service.addresses.first()
        runBlocking {
            val paired = assertIs<NaviampConnectPairingRuntimeResult.Paired>(
                runtime.pair(
                    host = connectHost,
                    advertisement = advertisement,
                    pairingCode = PairingCode.toCharArray(),
                    pairedAtEpochMillis = System.currentTimeMillis(),
                    trustedDeviceId = "android-tv-target",
                ),
            )
            try {
                val welcomeEnvelope = paired.session.receive()
                val welcome = assertIs<NaviampConnectWelcome>(welcomeEnvelope.message)
                val controller = NaviampConnectControllerSession(
                    transport = NaviampConnectSessionTransport(paired.session::sendEnvelope),
                    requestIds = incrementingRequestIds(),
                )
                controller.connect(
                    sessionId = paired.session.sessionId,
                    protocolVersion = paired.session.protocolVersion,
                    target = welcome.target,
                    capabilities = welcome.capabilities,
                    snapshot = welcome.snapshot,
                    nextOutboundSequence = paired.session.nextOutboundSequence(),
                    lastReceivedSequence = welcomeEnvelope.sequence,
                )
                sendAndReceive(controller, paired.session, NaviampConnectPlay)
                assertEquals(NaviampConnectPlaybackState.Playing, controller.state.value.snapshot?.playback?.state)
                sendAndReceive(
                    controller,
                    paired.session,
                    NaviampConnectHandoffQueue(
                        sourceIdentity = sourceIdentity(),
                        queue = NaviampConnectQueueSnapshot(
                            occurrences = listOf(
                                NaviampConnectQueueOccurrence(
                                    occurrenceId = "0:physical-track",
                                    mediaId = "physical-track",
                                    title = "Physical Track",
                                    artistName = "Naviamp",
                                ),
                            ),
                            currentIndex = 0,
                        ),
                        positionMillis = 12_000,
                        repeatMode = NaviampConnectRepeatMode.Off,
                        shuffled = false,
                    ),
                )
                sendAndReceive(
                    controller,
                    paired.session,
                    NaviampConnectOfferConnectionProvisioning(
                        NaviampConnectProvisioningProfile(
                            providerId = "navidrome",
                            displayName = "Physical Acceptance",
                            serverUrl = "https://music.example.test",
                            username = "listener",
                            password = "encrypted-test-only-secret",
                        ),
                    ),
                )
                sendAndReceive(
                    controller,
                    paired.session,
                    NaviampConnectStartMedia(
                        mediaType = NaviampConnectMediaType.InternetRadioStation,
                        mediaId = "physical-radio-station",
                        sourceIdentity = sourceIdentity(),
                    ),
                )
                assertEquals("physical-track", controller.state.value.snapshot?.queue?.occurrences?.single()?.mediaId)
                sendAndReceive(
                    controller,
                    paired.session,
                    NaviampConnectStartMedia(
                        mediaType = NaviampConnectMediaType.Album,
                        mediaId = "physical-album",
                        sourceIdentity = sourceIdentity(),
                    ),
                )
            } finally {
                paired.session.close()
            }
        }
    }

    private fun advertisement(port: Int, fingerprint: String) = NaviampConnectAdvertisement(
        instanceId = PairingInstance,
        displayName = "Android TV Emulator",
        protocolRange = NaviampConnectProtocolRange(),
        capabilities = acceptanceCapabilities(),
        port = port,
        identityFingerprint = fingerprint,
        expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
    )

    private fun snapshot(target: NaviampConnectDevice, revision: Long) = NaviampConnectTargetSnapshot(
        revision = revision,
        target = target,
        capabilities = acceptanceCapabilities(),
        sourceIdentity = sourceIdentity(),
        playback = NaviampConnectPlaybackSnapshot(),
        queue = NaviampConnectQueueSnapshot(),
    )

    private fun runtimeTargetDevice(deviceId: String) =
        NaviampConnectDevice(deviceId, "Android TV Emulator", NaviampConnectDeviceRole.Target)

    private suspend fun sendAndReceive(
        controller: NaviampConnectControllerSession,
        session: app.naviamp.app.NaviampConnectAuthenticatedSession,
        command: app.naviamp.domain.connect.NaviampConnectCommand,
    ) {
        assertIs<app.naviamp.app.NaviampConnectCommandSendResult.Sent>(controller.send(command))
        controller.receive(session.receive())
        controller.receive(session.receive())
    }

    private fun incrementingRequestIds(): NaviampConnectRequestIdFactory {
        var next = 1
        return NaviampConnectRequestIdFactory { "physical-command-${next++}" }
    }

    private fun sourceIdentity() = NaviampConnectSourceIdentity(
        providerId = "navidrome",
        canonicalServerOrigin = "https://music.example.test",
        accountIdentity = "listener",
    )

    private fun acceptanceCapabilities() = setOf(
        NaviampConnectCapability.TransportControls,
        NaviampConnectCapability.QueueHandoff,
        NaviampConnectCapability.CatalogPlayback,
        NaviampConnectCapability.InternetRadio,
        NaviampConnectCapability.ConnectionProvisioning,
    )

    private companion object {
        const val PairingPort = 42_425
        const val PairingInstance = "naviamp-pairing-cross-device-tv"
        const val PairingSession = "android-cross-device-pairing"
        const val PairingCode = "482913"
    }
}
