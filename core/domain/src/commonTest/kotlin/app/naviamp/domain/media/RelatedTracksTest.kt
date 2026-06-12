package app.naviamp.domain.media

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.domain.StreamRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RelatedTracksTest {
    @Test
    fun relatedTracksResultPrefersSonicMatchesWithSimilarityScores() = runTest {
        val provider = FakeRelatedProvider(
            supportsSonicSimilarity = true,
            sonicMatches = listOf(
                SonicSimilarTrack(track("seed"), similarity = 1.0),
                SonicSimilarTrack(track("match"), similarity = 0.91),
            ),
            radioTracks = listOf(track("radio")),
        )

        val result = relatedTracksResult(
            seedTrack = track("seed"),
            activeSourceId = null,
            provider = provider,
            localLibraryIndexRepository = null,
            preferSonicSimilarity = true,
            limit = 20,
            fallbackSources = listOf(RelatedTracksSource.ProviderRadio),
        )

        assertEquals(RelatedTracksSource.SonicSimilarity, result.source)
        assertEquals(listOf("match"), result.tracks.map { it.id.value })
        assertEquals(mapOf(TrackId("match") to 0.91), result.similarityByTrackId)
    }

    @Test
    fun relatedTracksResultFallsBackToProviderRadioWhenSonicIsEmpty() = runTest {
        val provider = FakeRelatedProvider(
            supportsSonicSimilarity = true,
            sonicMatches = emptyList(),
            radioTracks = listOf(track("radio")),
        )

        val result = relatedTracksResult(
            seedTrack = track("seed"),
            activeSourceId = null,
            provider = provider,
            localLibraryIndexRepository = null,
            preferSonicSimilarity = true,
            limit = 20,
            fallbackSources = listOf(RelatedTracksSource.ProviderRadio),
        )

        assertEquals(RelatedTracksSource.ProviderRadio, result.source)
        assertEquals(listOf("radio"), result.tracks.map { it.id.value })
        assertEquals(emptyMap(), result.similarityByTrackId)
    }

    @Test
    fun playMoreLikeThisQueueIncludesSeedForPlayNow() = runTest {
        val provider = FakeRelatedProvider(
            supportsSonicSimilarity = true,
            sonicMatches = listOf(
                SonicSimilarTrack(track("match-one"), similarity = 0.92),
                SonicSimilarTrack(track("match-two"), similarity = 0.88),
            ),
            radioTracks = listOf(track("radio")),
        )

        val queue = playMoreLikeThisQueue(
            seedTrack = track("seed"),
            provider = provider,
            preferSonicSimilarity = true,
            includeSeedTrack = true,
        )

        assertEquals(RelatedTracksSource.SonicSimilarity, queue.source)
        assertEquals(listOf("seed", "match-one", "match-two"), queue.tracks.map { it.id.value })
    }

    @Test
    fun playMoreLikeThisQueueOmitsSeedForQueueInsertion() = runTest {
        val provider = FakeRelatedProvider(
            supportsSonicSimilarity = true,
            sonicMatches = listOf(
                SonicSimilarTrack(track("seed"), similarity = 1.0),
                SonicSimilarTrack(track("match"), similarity = 0.92),
            ),
            radioTracks = listOf(track("radio")),
        )

        val queue = playMoreLikeThisQueue(
            seedTrack = track("seed"),
            provider = provider,
            preferSonicSimilarity = true,
            includeSeedTrack = false,
        )

        assertEquals(RelatedTracksSource.SonicSimilarity, queue.source)
        assertEquals(listOf("match"), queue.tracks.map { it.id.value })
    }

    @Test
    fun playMoreLikeThisQueueFallsBackToTrackRadioWithoutSonicSupport() = runTest {
        val provider = FakeRelatedProvider(
            supportsSonicSimilarity = false,
            sonicMatches = listOf(SonicSimilarTrack(track("sonic"), similarity = 0.92)),
            radioTracks = listOf(track("radio")),
        )

        val queue = playMoreLikeThisQueue(
            seedTrack = track("seed"),
            provider = provider,
            preferSonicSimilarity = true,
            includeSeedTrack = true,
        )

        assertEquals(RelatedTracksSource.ProviderRadio, queue.source)
        assertEquals(listOf("seed", "radio"), queue.tracks.map { it.id.value })
    }

    private class FakeRelatedProvider(
        supportsSonicSimilarity: Boolean,
        private val sonicMatches: List<SonicSimilarTrack>,
        private val radioTracks: List<Track>,
    ) : MediaProvider {
        override val id = ProviderId("fake")
        override val displayName = "Fake"
        override val capabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = true,
            supportsSonicSimilarity = supportsSonicSimilarity,
        )

        override suspend fun validateConnection(): ConnectionValidation = ConnectionValidation(null, null)
        override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<app.naviamp.domain.Album>()
        override suspend fun album(albumId: app.naviamp.domain.AlbumId): app.naviamp.domain.AlbumDetails = error("unused")
        override suspend fun artist(artistId: app.naviamp.domain.ArtistId): app.naviamp.domain.ArtistDetails = error("unused")
        override suspend fun artists(limit: Int) = emptyList<app.naviamp.domain.Artist>()
        override suspend fun tracks(limit: Int) = emptyList<Track>()
        override suspend fun search(query: String, limit: Int) = app.naviamp.domain.provider.MediaSearchResults()
        override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> = radioTracks
        override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int): List<SonicSimilarTrack> = sonicMatches
        override suspend fun streamUrl(request: StreamRequest): String = "fake"
        override fun coverArtUrl(coverArtId: String): String = "fake"
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = null,
            durationSeconds = null,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
