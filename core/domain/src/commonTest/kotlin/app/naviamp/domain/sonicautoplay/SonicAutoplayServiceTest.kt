package app.naviamp.domain.sonicautoplay

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.domain.queue.PlaybackQueue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals

class SonicAutoplayServiceTest {
    @Test
    fun continuationTracksUseRecentSeedsAndSkipExistingQueueTracks() = runTest {
        val provider = FakeSonicProvider(
            matchesBySeed = mapOf(
                "seed-2" to listOf(
                    SonicSimilarTrack(track("existing"), similarity = 0.99),
                    SonicSimilarTrack(track("candidate-1"), similarity = 0.75),
                ),
                "seed-1" to listOf(
                    SonicSimilarTrack(track("candidate-2"), similarity = 0.80),
                    SonicSimilarTrack(track("candidate-1"), similarity = 0.70),
                ),
            ),
        )
        val service = SonicAutoplayService(provider = { provider }, seedCount = 2, candidatesPerSeed = 5)
        val queue = PlaybackQueue(
            tracks = listOf(track("seed-1"), track("seed-2"), track("existing")),
            currentIndex = 1,
        )

        val tracks = service.continuationTracks(queue, limit = 5)

        assertEquals(listOf("candidate-2", "candidate-1"), tracks.map { it.id.value })
        assertEquals(listOf("seed-2", "seed-1"), provider.requests)
        assertEquals(emptyList(), provider.radioRequests)
    }

    @Test
    fun explicitInstantMixWorksWithoutSonicSupport() = runTest {
        val provider = FakeSonicProvider(supportsSonicSimilarity = false,
            radioBySeed = mapOf("seed" to listOf(track("regular"))))
        val service = SonicAutoplayService(provider = { provider })

        assertEquals(
            listOf(track("regular")),
            service.continuationTracks(PlaybackQueue(listOf(track("seed")), currentIndex = 0), preferSonicSimilarity = false),
        )
        assertEquals(emptyList(), provider.requests)
        assertEquals(listOf("seed"), provider.radioRequests)
    }

    @Test
    fun emptySonicNeverSubstitutes() = runTest {
        for (matches in listOf(emptyList(), listOf(SonicSimilarTrack(track("seed"))),
            listOf(SonicSimilarTrack(track("old"))))) {
            val provider = FakeSonicProvider(matchesBySeed = mapOf("seed" to matches),
                radioBySeed = mapOf("seed" to listOf(track("seed"), track("old"), track("new"), track("new"), track("extra"))))
            val result = SonicAutoplayService({ provider }, seedCount = 1).continuationTracks(
                PlaybackQueue(listOf(track("old"), track("seed")), 1), limit = 1)
            assertEquals(emptyList(), result)
            assertEquals(emptyList(), provider.radioRequests)
        }
    }

    @Test
    fun sonicFailureReturnsNoContinuation() = runTest {
        val provider = FakeSonicProvider(radioBySeed = mapOf(
            "seed" to listOf(track("first"), track("second")), "old" to listOf(track("third"))))
        provider.beforeSonic = { error("backend failure") }
        assertEquals(emptyList(), SonicAutoplayService({ provider }).continuationTracks(
            PlaybackQueue(listOf(track("old"), track("seed")), 1)).map { it.id.value })
    }

    @Test
    fun sonicBatchDeadlineStopsAfterTenSeconds() = runTest {
        val provider = FakeSonicProvider(radioBySeed = mapOf("seed" to listOf(track("regular"))))
        provider.beforeSonic = { delay(60_000) }
        val started = testScheduler.currentTime
        assertEquals(emptyList(), SonicAutoplayService({ provider }).continuationTracks(
            PlaybackQueue(listOf(track("seed")), 0)))
        assertEquals(10_000L, testScheduler.currentTime - started)
    }

    @Test
    fun usableSonicMatchesSurviveTimeoutWithoutTopUp() = runTest {
        val provider = FakeSonicProvider(matchesBySeed = mapOf("seed" to listOf(SonicSimilarTrack(track("sonic")))))
        provider.beforeSonic = { if (it == "old") delay(60_000) }
        assertEquals(listOf(track("sonic")), SonicAutoplayService({ provider }).continuationTracks(
            PlaybackQueue(listOf(track("old"), track("seed")), 1)))
        assertEquals(emptyList(), provider.radioRequests)
    }

    @Test
    fun cancellationFromEitherPhasePropagatesAndDoesNotRequestMoreSeeds() = runTest {
        for (cancelSonic in listOf(true, false)) {
            val provider = FakeSonicProvider()
            if (cancelSonic) provider.beforeSonic = { throw CancellationException() }
            else provider.beforeRadio = { throw CancellationException() }
            assertFailsWith<CancellationException> { SonicAutoplayService({ provider }).continuationTracks(
                PlaybackQueue(listOf(track("old"), track("seed")), 1), preferSonicSimilarity = cancelSonic) }
            assertEquals(if (cancelSonic) emptyList() else listOf("seed"), provider.radioRequests)
        }
    }

    @Test
    fun failedOrTimedOutRegularRadioReturnsNoContinuationAndRemainsBounded() = runTest {
        for (timeout in listOf(true, false)) {
            val provider = FakeSonicProvider()
            provider.beforeRadio = { if (timeout) delay(60_000) else error("regular failure") }
            val started = testScheduler.currentTime
            assertEquals(emptyList(), SonicAutoplayService({ provider }).continuationTracks(
                PlaybackQueue(listOf(track("seed")), 0), preferSonicSimilarity = false))
            assertEquals(if (timeout) 10_000L else 0L, testScheduler.currentTime - started)
            assertEquals(listOf("seed"), provider.radioRequests)
        }
    }

    @Test
    fun sonicAutoplaySeedsPreferCurrentThenBackToHistory() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three"), track("four")),
            currentIndex = 2,
        )

        assertEquals(listOf("three", "two", "one"), queue.sonicAutoplaySeeds(3).map { it.id.value })
    }

    @Test
    fun shortSonicBatchRemainsShortAtEverySelectedSize() = runTest {
        for (target in listOf(10, 50, 100)) {
            val provider = FakeSonicProvider(matchesBySeed = mapOf("seed" to listOf(SonicSimilarTrack(track("sonic"), 0.01))),
                radioBySeed = mapOf("seed" to (listOf(track("seed"), track("sonic"), track("queued")) +
                    (1..120).flatMap { listOf(track("regular-$it"), track("regular-$it")) })))
            val tracks = SonicAutoplayService({ provider }, seedCount = 1).continuationTracks(
                PlaybackQueue(listOf(track("queued"), track("seed")), 1), target)
            // Duplicate-heavy responses may exhaust the candidate limit before meeting the minimum.
            assertEquals("sonic", tracks.first().id.value)
            assertEquals(tracks.size, tracks.map { it.id }.distinct().size)
            assertEquals(1, tracks.size)
            kotlin.test.assertTrue(tracks.none { it.id.value in setOf("seed", "queued") })
            assertEquals(listOf(target * 2), provider.sonicCounts)
            assertEquals(emptyList(), provider.radioCounts)
        }
    }

    @Test
    fun sufficientSonicMatchesMeetSelectedSizeWithoutRadioAndRetainRanking() = runTest {
        val provider = FakeSonicProvider(matchesBySeed = mapOf("seed" to (1..100).map {
            SonicSimilarTrack(track("sonic-$it"), it / 100.0) }))
        val tracks = SonicAutoplayService({ provider }).continuationTracks(PlaybackQueue(listOf(track("seed")), 0), 30)
        assertEquals(30, tracks.size)
        assertEquals("sonic-60", tracks.first().id.value)
        assertEquals(emptyList(), provider.radioRequests)
    }

    private class FakeSonicProvider(
        private val supportsSonicSimilarity: Boolean = true,
        private val matchesBySeed: Map<String, List<SonicSimilarTrack>> = emptyMap(),
        private val radioBySeed: Map<String, List<Track>> = emptyMap(),
    ) : MediaProvider {
        val requests = mutableListOf<String>()
        val radioRequests = mutableListOf<String>()
        val sonicCounts = mutableListOf<Int>()
        val radioCounts = mutableListOf<Int>()
        var beforeSonic: suspend (String) -> Unit = {}
        var beforeRadio: suspend () -> Unit = {}
        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
            supportsSonicSimilarity = supportsSonicSimilarity,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int): List<Artist> = emptyList()
        override suspend fun tracks(limit: Int): List<Track> = emptyList()
        override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()
        override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int): List<SonicSimilarTrack> {
            requests += trackId.value
            sonicCounts += count
            beforeSonic(trackId.value)
            return matchesBySeed[trackId.value].orEmpty().take(count)
        }
        override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> {
            radioRequests += trackId.value
            radioCounts += count
            beforeRadio()
            return radioBySeed[trackId.value].orEmpty().take(count)
        }
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = error("unused")
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
