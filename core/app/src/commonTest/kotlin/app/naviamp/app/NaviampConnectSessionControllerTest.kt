package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectCommandRequest
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectErrorMessage
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
import app.naviamp.domain.connect.NaviampConnectPause
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectPlaybackSnapshot
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectProvisioningProfile
import app.naviamp.domain.connect.NaviampConnectQueueSnapshot
import app.naviamp.domain.connect.NaviampConnectSeek
import app.naviamp.domain.connect.NaviampConnectSnapshotMessage
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectSessionControllerTest {
    @Test
    fun retainedSessionContinuesSequencesConsumedByPairingAndWelcome() = runTest {
        lateinit var controller: NaviampConnectControllerSession
        lateinit var target: NaviampConnectTargetSession
        val controllerSent = mutableListOf<Long>()
        val targetSent = mutableListOf<Long>()
        controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport {
                controllerSent += it.sequence
                target.receive(it)
            },
            requestIds = incrementingRequestIds(),
        )
        target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0),
            transport = NaviampConnectSessionTransport {
                targetSent += it.sequence
                controller.receive(it)
            },
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                NaviampConnectTargetCommandResult.Success(
                    current.copy(
                        revision = current.revision + 1,
                        playback = current.playback.copy(state = NaviampConnectPlaybackState.Playing),
                    ),
                    changed = true,
                )
            },
            initialOutboundSequence = 3,
        )
        controller.connect(
            sessionId = SessionId,
            protocolVersion = 1,
            target = targetDevice(),
            capabilities = capabilities(),
            snapshot = snapshot(0),
            nextOutboundSequence = 2,
            lastReceivedSequence = 2,
        )

        controller.send(NaviampConnectPlay)

        assertEquals(listOf(2L), controllerSent)
        assertEquals(listOf(3L, 4L), targetSent)
        assertEquals(1, controller.state.value.snapshot?.revision)
    }

    @Test
    fun fakeTransportExecutesACommandAndReconcilesTheAuthoritativeSnapshot() = runTest {
        lateinit var controller: NaviampConnectControllerSession
        lateinit var target: NaviampConnectTargetSession
        var executionCount = 0
        controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport { target.receive(it) },
            requestIds = incrementingRequestIds(),
        )
        target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0),
            transport = NaviampConnectSessionTransport { controller.receive(it) },
            executor = NaviampConnectTargetCommandExecutor { command, current ->
                assertEquals(NaviampConnectPlay, command)
                executionCount += 1
                NaviampConnectTargetCommandResult.Success(
                    snapshot = current.copy(
                        revision = current.revision + 1,
                        playback = current.playback.copy(state = NaviampConnectPlaybackState.Playing),
                    ),
                    changed = true,
                )
            },
        )
        controller.connect(SessionId, 1, targetDevice(), capabilities(), snapshot(0))

        assertIs<NaviampConnectCommandSendResult.Sent>(controller.send(NaviampConnectPlay))

        assertEquals(1, executionCount)
        assertTrue(controller.state.value.pendingRequests.isEmpty())
        assertEquals(1, controller.state.value.snapshot?.revision)
        assertEquals(NaviampConnectPlaybackState.Playing, controller.state.value.snapshot?.playback?.state)
    }

    @Test
    fun controllerRejectsUnsupportedCommandsWithoutTouchingTheTransport() = runTest {
        val sent = mutableListOf<NaviampConnectEnvelope>()
        val controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport(sent::add),
            requestIds = incrementingRequestIds(),
        )
        controller.connect(
            SessionId,
            1,
            targetDevice(),
            setOf(NaviampConnectCapability.TransportControls),
            snapshot(0),
        )

        val result = assertIs<NaviampConnectCommandSendResult.Rejected>(controller.send(NaviampConnectSeek(10_000)))

        assertEquals(NaviampConnectErrorCode.UnsupportedCapability, result.error.code)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun targetDeduplicatesARequestBeforeApplyingReplayProtection() = runTest {
        val responses = mutableListOf<NaviampConnectEnvelope>()
        var executionCount = 0
        val target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0),
            transport = NaviampConnectSessionTransport(responses::add),
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                executionCount += 1
                NaviampConnectTargetCommandResult.Success(current.copy(revision = 1), changed = true)
            },
        )
        val envelope = NaviampConnectEnvelope(
            protocolVersion = 1,
            sessionId = SessionId,
            sequence = 0,
            requestId = "request-1",
            message = NaviampConnectCommandRequest(NaviampConnectPlay, expectedRevision = 0),
        )

        target.receive(envelope)
        target.receive(envelope)

        assertEquals(1, executionCount)
        assertEquals(3, responses.size)
        assertEquals(2, responses.count { it.responseToRequestId == "request-1" })
    }

    @Test
    fun targetDoesNotRetainOrReplayACompletedProvisioningCredential() = runTest {
        val responses = mutableListOf<NaviampConnectEnvelope>()
        var executionCount = 0
        val target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0).copy(
                capabilities = capabilities() + NaviampConnectCapability.ConnectionProvisioning,
            ),
            transport = NaviampConnectSessionTransport(responses::add),
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                executionCount += 1
                NaviampConnectTargetCommandResult.Success(current, changed = false)
            },
        )
        val envelope = NaviampConnectEnvelope(
            protocolVersion = 1,
            sessionId = SessionId,
            sequence = 0,
            requestId = "provisioning-request",
            message = NaviampConnectCommandRequest(
                NaviampConnectOfferConnectionProvisioning(
                    NaviampConnectProvisioningProfile(
                        providerId = "navidrome",
                        displayName = "Home",
                        serverUrl = "https://music.example.test",
                        username = "listener",
                        password = "one-time-secret",
                    ),
                ),
                expectedRevision = 0,
            ),
        )

        target.receive(envelope)
        target.receive(envelope)

        assertEquals(1, executionCount)
        assertEquals(2, responses.size)
        val replayError = assertIs<NaviampConnectErrorMessage>(responses.last().message)
        assertEquals(NaviampConnectErrorCode.InvalidRequest, replayError.code)
    }

    @Test
    fun reconnectRetriesOnlyIdempotentPendingRequests() = runTest {
        val sent = mutableListOf<NaviampConnectEnvelope>()
        val controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport(sent::add),
            requestIds = incrementingRequestIds(),
        )
        controller.connect(SessionId, 1, targetDevice(), capabilities(), snapshot(0))
        controller.send(NaviampConnectPause)
        controller.send(NaviampConnectNext)
        controller.disconnect()
        sent.clear()

        controller.reconnect("new-session", 1, targetDevice(), capabilities(), snapshot(2))

        assertEquals(1, sent.size)
        val retried = assertIs<NaviampConnectCommandRequest>(sent.single().message)
        assertEquals(NaviampConnectPause, retried.command)
        assertEquals(listOf(NaviampConnectPause), controller.state.value.pendingRequests.map { it.command })
    }

    @Test
    fun controllerIgnoresSnapshotsThatDoNotAdvanceTheRevision() = runTest {
        val controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport {},
            requestIds = incrementingRequestIds(),
        )
        controller.connect(SessionId, 1, targetDevice(), capabilities(), snapshot(4))

        controller.receive(
            NaviampConnectEnvelope(
                protocolVersion = 1,
                sessionId = SessionId,
                sequence = 0,
                message = NaviampConnectSnapshotMessage(snapshot(3)),
            ),
        )

        assertEquals(4, controller.state.value.snapshot?.revision)
    }

    @Test
    fun targetReturnsRevisionConflictAndItsCurrentSnapshot() = runTest {
        lateinit var controller: NaviampConnectControllerSession
        lateinit var target: NaviampConnectTargetSession
        controller = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport { target.receive(it) },
            requestIds = incrementingRequestIds(),
        )
        target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(2),
            transport = NaviampConnectSessionTransport { controller.receive(it) },
            executor = NaviampConnectTargetCommandExecutor { _, _ -> error("A conflicting command must not execute.") },
        )
        controller.connect(SessionId, 1, targetDevice(), capabilities(), snapshot(1))

        controller.send(NaviampConnectPlay)

        assertEquals(NaviampConnectErrorCode.RevisionConflict, controller.state.value.lastError?.code)
        assertEquals(2, controller.state.value.snapshot?.revision)
        assertTrue(controller.state.value.pendingRequests.isEmpty())
    }

    @Test
    fun localTargetMutationPublishesAnAuthoritativeSnapshot() = runTest {
        val sent = mutableListOf<NaviampConnectEnvelope>()
        val target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0),
            transport = NaviampConnectSessionTransport(sent::add),
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                NaviampConnectTargetCommandResult.Success(current, changed = false)
            },
        )

        target.publishLocalSnapshot(snapshot(1))

        assertEquals(1, target.snapshot().revision)
        assertEquals(1, assertIs<NaviampConnectSnapshotMessage>(sent.single().message).snapshot.revision)
    }

    @Test
    fun queueHandoffIsRejectedBeforeExecutionWhenSourcesDiffer() = runTest {
        val sent = mutableListOf<NaviampConnectEnvelope>()
        var executed = false
        val target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0).copy(
                capabilities = capabilities() + NaviampConnectCapability.QueueHandoff,
                sourceIdentity = sourceIdentity("listener"),
            ),
            transport = NaviampConnectSessionTransport(sent::add),
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                executed = true
                NaviampConnectTargetCommandResult.Success(current, changed = false)
            },
        )
        target.receive(
            NaviampConnectEnvelope(
                protocolVersion = 1,
                sessionId = SessionId,
                sequence = 0,
                requestId = "handoff",
                message = NaviampConnectCommandRequest(
                    command = NaviampConnectHandoffQueue(
                        sourceIdentity = sourceIdentity("different-account"),
                        queue = NaviampConnectQueueSnapshot(),
                        positionMillis = 0,
                        repeatMode = NaviampConnectRepeatMode.Off,
                        shuffled = false,
                    ),
                    expectedRevision = 0,
                ),
            ),
        )

        assertEquals(false, executed)
        val error = assertIs<NaviampConnectErrorMessage>(sent.single().message)
        assertEquals(NaviampConnectErrorCode.SourceMismatch, error.code)
    }

    @Test
    fun catalogStartIsRejectedBeforeExecutionWhenSourcesDiffer() = runTest {
        val sent = mutableListOf<NaviampConnectEnvelope>()
        var executed = false
        val target = NaviampConnectTargetSession(
            sessionId = SessionId,
            protocolVersion = 1,
            initialSnapshot = snapshot(0).copy(
                capabilities = capabilities() + NaviampConnectCapability.CatalogPlayback,
                sourceIdentity = sourceIdentity("listener"),
            ),
            transport = NaviampConnectSessionTransport(sent::add),
            executor = NaviampConnectTargetCommandExecutor { _, current ->
                executed = true
                NaviampConnectTargetCommandResult.Success(current, changed = false)
            },
        )

        target.receive(
            NaviampConnectEnvelope(
                protocolVersion = 1,
                sessionId = SessionId,
                sequence = 0,
                requestId = "start-media",
                message = NaviampConnectCommandRequest(
                    command = NaviampConnectStartMedia(
                        mediaType = NaviampConnectMediaType.Album,
                        mediaId = "album",
                        sourceIdentity = sourceIdentity("different-account"),
                    ),
                    expectedRevision = 0,
                ),
            ),
        )

        assertEquals(false, executed)
        val error = assertIs<NaviampConnectErrorMessage>(sent.single().message)
        assertEquals(NaviampConnectErrorCode.SourceMismatch, error.code)
    }

    private fun snapshot(revision: Long) = NaviampConnectTargetSnapshot(
        revision = revision,
        target = targetDevice(),
        capabilities = capabilities(),
        playback = NaviampConnectPlaybackSnapshot(),
        queue = NaviampConnectQueueSnapshot(),
    )

    private fun capabilities() = setOf(
        NaviampConnectCapability.TransportControls,
        NaviampConnectCapability.Seeking,
        NaviampConnectCapability.QueueRead,
    )

    private fun sourceIdentity(account: String) = NaviampConnectSourceIdentity(
        providerId = "navidrome",
        canonicalServerOrigin = "https://music.example.test",
        accountIdentity = account,
    )

    private fun targetDevice() = NaviampConnectDevice(
        deviceId = "target",
        displayName = "Living Room",
        role = NaviampConnectDeviceRole.Target,
    )

    private fun incrementingRequestIds(): NaviampConnectRequestIdFactory {
        var next = 1
        return NaviampConnectRequestIdFactory { "request-${next++}" }
    }

    private companion object {
        const val SessionId = "session"
    }
}
