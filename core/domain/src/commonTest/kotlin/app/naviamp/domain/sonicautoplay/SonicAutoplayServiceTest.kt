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
    }

    @Test
    fun continuationTracksReturnEmptyWhenSonicSimilarityIsUnavailable() = runTest {
        val provider = FakeSonicProvider(supportsSonicSimilarity = false)
        val service = SonicAutoplayService(provider = { provider })

        assertEquals(
            emptyList(),
            service.continuationTracks(PlaybackQueue(listOf(track("seed")), currentIndex = 0)),
        )
        assertEquals(emptyList(), provider.requests)
    }

    @Test
    fun sonicAutoplaySeedsPreferCurrentThenBackToHistory() {
        val queue = PlaybackQueue(
            tracks = listOf(track("one"), track("two"), track("three"), track("four")),
            currentIndex = 2,
        )

        assertEquals(listOf("three", "two", "one"), queue.sonicAutoplaySeeds(3).map { it.id.value })
    }

    private class FakeSonicProvider(
        private val supportsSonicSimilarity: Boolean = true,
        private val matchesBySeed: Map<String, List<SonicSimilarTrack>> = emptyMap(),
    ) : MediaProvider {
        val requests = mutableListOf<String>()
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
            return matchesBySeed[trackId.value].orEmpty().take(count)
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
