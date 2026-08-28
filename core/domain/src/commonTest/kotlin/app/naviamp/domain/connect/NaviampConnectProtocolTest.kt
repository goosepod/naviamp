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
        )

        messages.forEachIndexed { index, message ->
            val original = NaviampConnectEnvelope(1, "session", index.toLong(), message = message)
            assertEquals(original, NaviampConnectWireCodec.decode(NaviampConnectWireCodec.encode(original)))
        }
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
            NaviampConnectRequestSnapshot,
            NaviampConnectShowSurface(NaviampConnectTargetSurface.Queue),
            NaviampConnectStartMedia(
                mediaType = NaviampConnectMediaType.Album,
                mediaId = "album",
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
