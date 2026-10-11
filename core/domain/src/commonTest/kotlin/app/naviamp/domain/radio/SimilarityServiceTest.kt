package app.naviamp.domain.radio

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SimilarityServiceTest {
    @Test
    fun sonicMatchesRetainScoresSeedOrderAndProviderOrderWithoutCallingRadio() = runTest {
        val provider = FakeProvider()
        provider.sonic = { seed, count ->
            assertEquals(21, count)
            listOf(SonicSimilarTrack(track(seed.value)), SonicSimilarTrack(track("queued")),
                SonicSimilarTrack(track("${seed.value}-match"), 0.8))
        }
        val result = SimilarityService(provider).load(SimilarityRequest(
            listOf(TrackId("one"), TrackId("two")), setOf(TrackId("queued")), 21))
        assertEquals(SimilarityEndpoint.Sonic, result.source)
        assertEquals(listOf("one", "two"), result.seedMatches.map { it.seedId.value })
        assertEquals(listOf("one-match", "two-match"), result.seedMatches.flatMap { it.matches }.map { it.track.id.value })
        assertEquals(listOf(0.8, 0.8), result.seedMatches.flatMap { it.matches }.map { it.similarity })
        assertNull(result.fallback)
        assertNull(result.failure)
        assertEquals(emptyList(), provider.regularRequests)
    }

    @Test
    fun emptySeedOnlyAndExcludedOnlySonicResponsesFallBack() = runTest {
        for (matches in listOf(emptyList(), listOf(SonicSimilarTrack(track("seed"))),
            listOf(SonicSimilarTrack(track("queued"))))) {
            val provider = FakeProvider()
            provider.sonic = { _, _ -> matches }
            provider.regular = { _, _ -> listOf(track("seed"), track("queued"), track("regular")) }
            val result = SimilarityService(provider).load(SimilarityRequest(
                listOf(TrackId("seed")), setOf(TrackId("queued"))))
            assertEquals(SimilarityEndpoint.Regular, result.source)
            assertEquals(SonicRadioFallbackReason.Empty, result.fallback)
            assertEquals(listOf("regular"), result.getOrThrow().single().matches.map { it.track.id.value })
            assertNull(result.seedMatches.single().matches.single().similarity)
        }
    }

    @Test
    fun disabledPreferenceOrMissingCapabilitySkipsSonicWithoutClaimingFailure() = runTest {
        for ((supported, preferred) in listOf(true to false, false to true)) {
            val provider = FakeProvider(supported)
            provider.regular = { _, _ -> listOf(track("regular")) }
            val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")), preferSonic = preferred))
            assertTrue(result.hasMatches)
            assertEquals(SimilarityEndpoint.Regular, result.source)
            assertNull(result.fallback)
            assertEquals(emptyList(), provider.sonicRequests)
        }
    }

    @Test
    fun sonicFailuresAndTransportTimeoutsProduceTypedFallbackReasons() = runTest {
        for ((error, reason) in listOf(IllegalStateException("SECRET") to SonicRadioFallbackReason.Failed,
            SimilarityRequestException(timedOut = true) to SonicRadioFallbackReason.TimedOut)) {
            val provider = FakeProvider()
            provider.sonic = { _, _ -> throw error }
            provider.regular = { _, _ -> listOf(track("regular")) }
            val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed"))))
            assertEquals(reason, result.fallback)
            assertTrue(result.hasMatches)
            assertNull(result.failure)
            assertFalse(result.toString().contains("SECRET"))
        }
    }

    @Test
    fun bothSeedBatchesHaveOneTenSecondDeadlineEach() = runTest {
        val provider = FakeProvider()
        provider.sonic = { _, _ -> delay(60_000); emptyList() }
        provider.regular = { _, _ -> delay(60_000); emptyList() }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("one"), TrackId("two"))))
        assertEquals(20_000L, testScheduler.currentTime)
        assertEquals(listOf("one"), provider.sonicRequests)
        assertEquals(listOf("one"), provider.regularRequests)
        assertEquals(SonicRadioFallbackReason.TimedOut, result.fallback)
        assertEquals(SimilarityResultKind.TimedOut, result.failure?.kind)
        assertTrue(assertFailsWith<SimilarityRequestException> { result.getOrThrow() }.timedOut)
    }

    @Test
    fun partialSonicResultsKeepTheirSeedSlotsAfterFailureAndLaterDeadline() = runTest {
        val provider = FakeProvider()
        provider.sonic = { seed, _ -> when (seed.value) {
            "one" -> error("SECRET")
            "two" -> listOf(SonicSimilarTrack(track("sonic"), 0.7))
            else -> { delay(60_000); emptyList() }
        } }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("one"), TrackId("two"), TrackId("three"))))
        assertEquals(listOf(0, 1, 0), result.getOrThrow().map { it.matches.size })
        assertEquals(SimilarityEndpoint.Sonic, result.source)
        assertNull(result.failure)
        assertNull(result.fallback)
        assertEquals(emptyList(), provider.regularRequests)
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun partialRegularSuccessDoesNotBecomeAFatalError() = runTest {
        val provider = FakeProvider()
        provider.regular = { seed, _ -> if (seed.value == "one") error("SECRET") else listOf(track("regular")) }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("one"), TrackId("two"))))
        assertEquals(listOf(0, 1), result.getOrThrow().map { it.matches.size })
        assertEquals(SonicRadioFallbackReason.Empty, result.fallback)
        assertNull(result.failure)
    }

    @Test
    fun fatalRegularErrorHasSafeMetadataAndAnExplicitOpaqueThrowingAccessor() = runTest {
        val provider = FakeProvider()
        val cause = IllegalStateException("SECRET request-url?password=SECRET")
        provider.regular = { _, _ -> throw cause }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed"))))
        assertEquals(SimilarityEndpointResult(SimilarityResultKind.Failed), result.failure)
        assertFalse(result.failure.toString().contains("SECRET"))
        assertFalse(result.toString().contains("SECRET"))
        assertSame(cause, assertFailsWith<IllegalStateException> { result.getOrThrow() })
    }

    @Test
    fun regularFailureRetainsOnlySafeProtocolCodes() = runTest {
        val provider = FakeProvider()
        provider.regular = { _, _ -> throw SimilarityRequestException(503, 0) }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed"))))
        assertEquals(SimilarityEndpointResult(SimilarityResultKind.Failed, httpStatus = 503, serverCode = 0), result.failure)
    }

    @Test
    fun callerDeadlineAndCancellationNeverStartFallbackOrReturnPartialMatches() = runTest {
        val provider = FakeProvider()
        provider.sonic = { _, _ -> delay(60_000); emptyList() }
        assertFailsWith<CancellationException> {
            withTimeout(100) { SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")))) }
        }
        assertEquals(emptyList(), provider.regularRequests)
        provider.sonic = { seed, _ -> if (seed.value == "one") listOf(SonicSimilarTrack(track("partial"))) else throw CancellationException() }
        assertFailsWith<CancellationException> {
            SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("one"), TrackId("two"))))
        }
        assertEquals(emptyList(), provider.regularRequests)
    }

    @Test
    fun regularCancellationStopsTheRemainingSeeds() = runTest {
        val provider = FakeProvider()
        provider.regular = { _, _ -> throw CancellationException() }
        assertFailsWith<CancellationException> {
            SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("one"), TrackId("two")), preferSonic = false))
        }
        assertEquals(listOf("one"), provider.regularRequests)
    }

    @Test
    fun normalizationAndEmptyResultsDoNotMakeRedundantRequests() = runTest {
        val provider = FakeProvider()
        val empty = SimilarityService(provider).load(SimilarityRequest(emptyList()))
        assertNull(empty.source)
        assertEquals(emptyList(), empty.getOrThrow())
        assertEquals(emptyList(), provider.sonicRequests)
        provider.regular = { _, count -> assertEquals(1, count); emptyList() }
        val result = SimilarityService(provider).load(SimilarityRequest(
            listOf(TrackId("one"), TrackId("one")), countPerSeed = 0, preferSonic = false))
        assertEquals(listOf("one"), provider.regularRequests)
        assertFalse(result.hasMatches)
        assertNull(result.failure)
        assertEquals(listOf(SimilaritySeedMatches(TrackId("one"), emptyList())), result.getOrThrow())
    }

    @Test
    fun shortDistinctSonicResultsAreSupplementedWithoutLosingScoresOrSeedSlots() = runTest {
        val provider = FakeProvider()
        provider.sonic = { _, _ -> listOf(SonicSimilarTrack(track("sonic"), 0.1), SonicSimilarTrack(track("sonic"), 0.2)) }
        provider.regular = { _, _ -> listOf(track("seed"), track("queued"), track("sonic"), track("regular")) }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")),
            excludedTrackIds = setOf(TrackId("queued")), minimumMatches = 2))
        assertEquals(SimilarityEndpoint.Sonic, result.source)
        assertNull(result.fallback)
        assertEquals(listOf(0.1, 0.2), result.getOrThrow().single().matches.map { it.similarity })
        assertEquals(listOf("regular"), result.topUp?.getOrThrow()?.single()?.matches?.map { it.track.id.value })
        assertEquals(SimilarityEndpoint.Regular, result.topUp?.source)
        assertEquals(listOf("seed"), provider.regularRequests)
    }

    @Test
    fun minimumSatisfiedByUniqueSonicResultsSkipsRegularRadio() = runTest {
        val provider = FakeProvider()
        provider.sonic = { _, _ -> listOf(SonicSimilarTrack(track("one")), SonicSimilarTrack(track("two"))) }
        val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")), minimumMatches = 2))
        assertNull(result.topUp)
        assertEquals(emptyList(), provider.regularRequests)
    }

    @Test
    fun supplementalFailureOrDeadlineRetainsSonicAndSafeFailureMetadata() = runTest {
        for (timeout in listOf(false, true)) {
            val provider = FakeProvider()
            provider.sonic = { _, _ -> listOf(SonicSimilarTrack(track("sonic"))) }
            provider.regular = { _, _ -> if (timeout) { delay(60_000); emptyList() } else error("SECRET") }
            val result = SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")), minimumMatches = 10))
            assertEquals(listOf("sonic"), result.getOrThrow().single().matches.map { it.track.id.value })
            assertEquals(if (timeout) SimilarityResultKind.TimedOut else SimilarityResultKind.Failed, result.topUp?.failure?.kind)
            assertFalse(result.topUp?.failure.toString().contains("SECRET"))
        }
    }

    @Test
    fun supplementalCancellationPropagatesAfterUsableSonicResults() = runTest {
        val provider = FakeProvider()
        provider.sonic = { _, _ -> listOf(SonicSimilarTrack(track("sonic"))) }
        provider.regular = { _, _ -> throw CancellationException() }
        assertFailsWith<CancellationException> {
            SimilarityService(provider).load(SimilarityRequest(listOf(TrackId("seed")), minimumMatches = 10))
        }
    }

    private class FakeProvider(supported: Boolean = true) : MediaProvider {
        override val id = ProviderId("similarity-test")
        override val displayName = "Similarity test"
        override val capabilities = ProviderCapabilities(supportsStreamingTranscode = false,
            supportsDownloadTranscode = false, supportsArtistRadio = false, supportsAlbumRadio = false,
            supportsTrackRadio = true, supportsSonicSimilarity = supported)
        val sonicRequests = mutableListOf<String>()
        val regularRequests = mutableListOf<String>()
        var sonic: suspend (TrackId, Int) -> List<SonicSimilarTrack> = { _, _ -> emptyList() }
        var regular: suspend (TrackId, Int) -> List<Track> = { _, _ -> emptyList() }
        override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int): List<SonicSimilarTrack> {
            sonicRequests += trackId.value
            return sonic(trackId, count)
        }
        override suspend fun sonicSimilarTracks(trackId: TrackId, count: Int): List<Track> = error("Use the scored canonical request")
        override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> {
            regularRequests += trackId.value
            return regular(trackId, count)
        }
        override suspend fun validateConnection() = ConnectionValidation(null, null)
        override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int) = emptyList<Artist>()
        override suspend fun tracks(limit: Int) = emptyList<Track>()
        override suspend fun search(query: String, limit: Int) = MediaSearchResults()
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = error("unused")
    }

    private fun track(id: String) = Track(TrackId(id), id, artistName = "Artist", albumTitle = null,
        durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
}
