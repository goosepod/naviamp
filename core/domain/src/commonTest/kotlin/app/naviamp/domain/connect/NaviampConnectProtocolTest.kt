package app.naviamp.domain.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NaviampConnectProtocolTest {
    @Test
    fun deviceCapabilitiesAreIndependentFromTheActiveSessionRole() {
        val capabilities = setOf(
            NaviampConnectDeviceCapability.ControlPlayback,
            NaviampConnectDeviceCapability.PlaybackTarget,
        )
        val controllingPhone = NaviampConnectDevice(
            deviceId = "phone",
            displayName = "Pixel",
            role = NaviampConnectDeviceRole.Controller,
            deviceCapabilities = capabilities,
        )
        val playingPhone = controllingPhone.copy(role = NaviampConnectDeviceRole.Target)

        assertTrue(controllingPhone.canActAs(NaviampConnectDeviceRole.Controller))
        assertTrue(controllingPhone.canActAs(NaviampConnectDeviceRole.Target))
        assertEquals(capabilities, playingPhone.deviceCapabilities)
        assertFailsWith<IllegalArgumentException> {
            NaviampConnectDevice(
                deviceId = "tv",
                displayName = "Television",
                role = NaviampConnectDeviceRole.Controller,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.PlaybackTarget),
            )
        }
    }

    @Test
    fun negotiationSelectsTheHighestMutuallySupportedVersion() {
        assertEquals(
            3,
            negotiateNaviampConnectProtocol(
                NaviampConnectProtocolRange(1, 3),
                NaviampConnectProtocolRange(2, 4),
            ),
        )
        assertNull(
            negotiateNaviampConnectProtocol(
                NaviampConnectProtocolRange(1, 2),
                NaviampConnectProtocolRange(3, 4),
            ),
        )
    }

    @Test
    fun sourceIdentityIgnoresCaseOrderAndPresentationNoise() {
        val controller = NaviampConnectSourceIdentity(
            providerId = " Navidrome ",
            canonicalServerOrigin = "HTTPS://MUSIC.EXAMPLE.TEST/",
            accountIdentity = "Listener",
            libraryIds = listOf("rock", "jazz", "rock"),
        )
        val target = NaviampConnectSourceIdentity(
            providerId = "navidrome",
            canonicalServerOrigin = "https://music.example.test",
            accountIdentity = "listener",
            libraryIds = listOf("jazz", "rock"),
        )

        assertTrue(controller.isCompatibleWith(target))
        assertFalse(controller.isCompatibleWith(target.copy(accountIdentity = "someone-else")))
    }

    @Test
    fun versionedEnvelopeRoundTripsQueueOccurrencesAndGroups() {
        val original = NaviampConnectEnvelope(
            protocolVersion = 1,
            sessionId = "session",
            sequence = 7,
            requestId = "request",
            message = NaviampConnectSnapshotMessage(snapshot(revision = 9)),
        )

        val decoded = NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(original))

        assertEquals(original, decoded)
        val snapshot = assertIs<NaviampConnectSnapshotMessage>(decoded.message).snapshot
        assertEquals("occurrence-1", snapshot.queue.occurrences.single().occurrenceId)
        assertEquals("group-1", snapshot.queue.groups.single().groupId)
    }

    @Test
    fun clearUpNextCommandRoundTripsOnTheAuthenticatedWire() {
        val original = NaviampConnectEnvelope(
            protocolVersion = 1,
            sessionId = "session",
            sequence = 8,
            requestId = "clear",
            message = NaviampConnectCommandRequest(NaviampConnectClearUpNext, expectedRevision = 4),
        )

        assertEquals(original, NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(original)))
    }

    @Test
    fun pairingWireMessageContainsOnlyAnOpaqueHandshakePayload() {
        val encoded = NaviampConnectWireCodec.encode(
            NaviampConnectEnvelope(
                protocolVersion = 1,
                sequence = 0,
                message = NaviampConnectPairingHandshake(
                    pairingSessionId = "pairing",
                    step = 1,
                    payloadBase64 = "opaque-pake-output",
                ),
            ),
        )

        assertTrue(encoded.contains("opaque-pake-output"))
        assertFalse(encoded.contains("displayCode"))
        assertFalse(encoded.contains("password"))
    }

    @Test
    fun pairingOfferAndIdentityProofRoundTripPublicMaterialOnly() {
        val identity = NaviampConnectPublicIdentity(
            deviceId = "target",
            identityFingerprint = "fingerprint",
            publicKeyBase64 = "public-key",
        )
        val messages = listOf<NaviampConnectMessage>(
            NaviampConnectPairingOffer("pairing", targetDevice(), identity),
            NaviampConnectPairingIdentityProof(identity, "signature"),
            NaviampConnectPairingConfirmation("fingerprint"),
            NaviampConnectSessionReplaced,
            NaviampConnectConnectionProvisioningResult(true, "TV setup completed securely."),
        )

        messages.forEachIndexed { index, message ->
            val original = NaviampConnectEnvelope(1, "session", index.toLong(), message = message)
            assertEquals(original, NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(original)))
        }
    }

    @Test
    fun resumptionWireMessagesBindTheFreshControllerChallenge() {
        val controller = NaviampConnectDevice(
            deviceId = "controller",
            displayName = "Phone",
            role = NaviampConnectDeviceRole.Controller,
        )
        val target = targetDevice()
        val controllerIdentity = NaviampConnectPublicIdentity("controller", "controller-fingerprint", "controller-key")
        val targetIdentity = NaviampConnectPublicIdentity("target", "target-fingerprint", "target-key")
        val messages = listOf<NaviampConnectMessage>(
            NaviampConnectResumeHello(
                controller,
                controllerIdentity,
                NaviampConnectProtocolRange(),
                controllerNonce = "fresh-controller-challenge",
            ),
            NaviampConnectResumeOffer(
                sessionId = "resume-session",
                controllerNonce = "fresh-controller-challenge",
                protocolVersion = 1,
                target = target,
                identity = targetIdentity,
            ),
        )

        messages.forEachIndexed { index, message ->
            val original = NaviampConnectEnvelope(1, sequence = index.toLong(), message = message)
            assertEquals(original, NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(original)))
        }
        assertFailsWith<IllegalArgumentException> {
            NaviampConnectResumeHello(controller, controllerIdentity, NaviampConnectProtocolRange(), "")
        }
    }

    @Test
    fun wireRoundTripPreservesADeviceThatCanControlAndPlay() {
        val device = NaviampConnectDevice(
            deviceId = "desktop",
            displayName = "Office Mac",
            role = NaviampConnectDeviceRole.Controller,
            deviceCapabilities = setOf(
                NaviampConnectDeviceCapability.ControlPlayback,
                NaviampConnectDeviceCapability.PlaybackTarget,
            ),
        )
        val identity = NaviampConnectPublicIdentity("desktop", "fingerprint", "public-key")
        val envelope = NaviampConnectEnvelope(
            protocolVersion = 1,
            sequence = 0,
            message = NaviampConnectHello(
                device = device,
                identity = identity,
                protocolRange = NaviampConnectProtocolRange(),
                capabilities = emptySet(),
            ),
        )

        val decoded = NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(envelope))

        assertEquals(device, assertIs<NaviampConnectHello>(decoded.message).device)
    }

    @Test
    fun protocolV1DeviceWithoutCapabilitiesUsesItsSessionRoleAsTheFallback() {
        val decoded = NaviampConnectWireCodec.decode(
            """{
                "protocolVersion":1,
                "sequence":0,
                "message":{
                    "type":"hello",
                    "device":{"deviceId":"legacy","displayName":"Legacy Phone","role":"Controller"},
                    "identity":{
                        "deviceId":"legacy",
                        "identityFingerprint":"fingerprint",
                        "publicKeyBase64":"public-key"
                    },
                    "protocolRange":{"minimum":1,"maximum":1},
                    "capabilities":[]
                }
            }""".trimIndent(),
        )

        val device = assertIs<NaviampConnectHello>(decoded.message).device
        assertEquals(
            setOf(NaviampConnectDeviceCapability.ControlPlayback),
            device.deviceCapabilities,
        )
    }

    @Test
    fun everyRemoteCommandDeclaresItsRequiredCapability() {
        val commands = listOf<NaviampConnectCommand>(
            NaviampConnectPlay,
            NaviampConnectSeek(1_000),
            NaviampConnectSetFavorite("track", true),
            NaviampConnectSetRepeat(NaviampConnectRepeatMode.All),
            NaviampConnectSetShuffle(true),
            NaviampConnectSelectQueueOccurrence("occurrence"),
            NaviampConnectMoveQueueOccurrence("occurrence"),
            NaviampConnectRemoveQueueOccurrence("occurrence"),
            NaviampConnectClearUpNext,
            NaviampConnectRequestSnapshot,
            NaviampConnectShowSurface(NaviampConnectTargetSurface.Queue),
            NaviampConnectStartMedia(
                mediaType = NaviampConnectMediaType.Album,
                mediaId = "album",
                sourceIdentity = sourceIdentity(),
            ),
            NaviampConnectQueueMedia(
                mediaType = NaviampConnectMediaType.Track,
                mediaId = "track",
                placement = NaviampConnectQueuePlacement.PlayNext,
                sourceIdentity = sourceIdentity(),
            ),
            NaviampConnectOfferConnectionProvisioning(
                NaviampConnectProvisioningProfile(
                    providerId = "navidrome",
                    displayName = "Home",
                    serverUrl = "https://music.example.test",
                    username = "listener",
                    password = "secret",
                ),
            ),
            NaviampConnectHandoffQueue(
                sourceIdentity = sourceIdentity(),
                queue = NaviampConnectQueueSnapshot(),
                positionMillis = 0,
                repeatMode = NaviampConnectRepeatMode.Off,
                shuffled = false,
            ),
        )

        assertTrue(commands.all { it.requiredCapability() != null })
    }

    @Test
    fun queueSnapshotsRejectAmbiguousOccurrencesAndInvalidIndexes() {
        val occurrence = NaviampConnectQueueOccurrence(
            occurrenceId = "same-occurrence",
            mediaId = "track",
            title = "Track",
            artistName = "Artist",
        )

        assertFailsWith<IllegalArgumentException> {
            NaviampConnectQueueSnapshot(
                occurrences = listOf(occurrence, occurrence),
                currentIndex = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NaviampConnectQueueSnapshot(
                occurrences = listOf(occurrence),
                currentIndex = -1,
            )
        }
    }

    private fun snapshot(revision: Long) = NaviampConnectTargetSnapshot(
        revision = revision,
        target = targetDevice(),
        capabilities = setOf(NaviampConnectCapability.QueueRead),
        sourceIdentity = sourceIdentity(),
        queue = NaviampConnectQueueSnapshot(
            occurrences = listOf(
                NaviampConnectQueueOccurrence(
                    occurrenceId = "occurrence-1",
                    mediaId = "track-1",
                    title = "Track",
                    artistName = "Artist",
                ),
            ),
            currentIndex = 0,
            groups = listOf(
                NaviampConnectQueueGroup(
                    groupId = "group-1",
                    startIndex = 0,
                    endIndexExclusive = 1,
                    targetType = app.naviamp.domain.playback.PlaybackProfileTargetType.Album,
                    targetId = "album-1",
                ),
            ),
        ),
    )

    private fun sourceIdentity() = NaviampConnectSourceIdentity(
        providerId = "navidrome",
        canonicalServerOrigin = "https://music.example.test",
        accountIdentity = "listener",
    )

    private fun targetDevice() = NaviampConnectDevice(
        deviceId = "target",
        displayName = "Living Room",
        role = NaviampConnectDeviceRole.Target,
    )
}
