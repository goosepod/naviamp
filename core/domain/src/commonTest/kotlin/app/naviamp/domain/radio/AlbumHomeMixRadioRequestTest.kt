package app.naviamp.domain.radio

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
import app.naviamp.domain.settings.RecentRadioKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlbumHomeMixRadioRequestTest {
    private val album = Album(AlbumId("single"), "One Song Single", "Seed Artist", "cover", null)
    private val seed = track("seed", albumId = album.id)

    @Test
    fun mixIsASeedTrackStationPreFilledFromTheSeedsSimilarSongs() = runTest {
        val similar = (1..12).map { track("similar-$it", albumId = AlbumId("other-$it")) }
        val provider = MixProvider(similar = mapOf(seed.id to similar))

        val request = albumHomeMixSeededRadioRequest(album, seed)
        val result = assertIs<SeededRadioBuildResult.Ready>(seededRadioBuildResult(request, RadioService(provider)))

        assertEquals("One Song Single radio", request.label)
        assertEquals(RecentRadioKind.Track, request.recentRadioStream.kind)
        assertEquals("track:seed", request.recentRadioStream.id)
        assertEquals(seed, result.queue.first())
        assertEquals(similar.map { it.id }.toSet(), result.queue.drop(1).map { it.id }.toSet())
        assertTrue(result.queue.size > 1, "A mix must be a multi-track queue, not the one-track album.")
        assertEquals(listOf(seed.id), provider.similarRequests)
    }

    @Test
    fun savedMixStationRebuildsThroughSeedTrackRadioNeverAlbumRadio() {
        val stream = albumHomeMixSeededRadioRequest(album, seed).recentRadioStream

        val replay = assertIs<RecentRadioAction.PlayTrack>(recentRadioAction(stream))

        assertEquals(seed.id, replay.track.id)
        assertEquals(album.id, replay.track.albumId)
    }

    @Test
    fun mixNeverQueuesTheSeedAlbumEvenWhenSimilarSongsReturnItsTracks() = runTest {
        val albumSibling = track("sibling", albumId = album.id)
        val other = track("other", albumId = AlbumId("other"))
        val provider = MixProvider(similar = mapOf(seed.id to listOf(albumSibling, other)))

        val result = assertIs<SeededRadioBuildResult.Ready>(
            seededRadioBuildResult(albumHomeMixSeededRadioRequest(album, seed), RadioService(provider)),
        )

        assertEquals(listOf(seed.id, other.id), result.queue.map { it.id })
    }

    @Test
    fun emptySimilarSongsLeaveOnlyTheSeedSoSharedRadioReportsAnEmptyStation() = runTest {
        val provider = MixProvider(similar = emptyMap())

        val result = assertIs<SeededRadioBuildResult.Ready>(
            seededRadioBuildResult(albumHomeMixSeededRadioRequest(album, seed), RadioService(provider)),
        )

        // The shared seeded-radio starter treats a one-track build as an empty station; the mix
        // does not substitute the album's own tracks.
        assertEquals(listOf(seed.id), result.queue.map { it.id })
    }

    @Test
    fun unsupportedSimilarSongsFailLikeStartRadioWithoutAnAlbumFallback() = runTest {
        val provider = MixProvider(similar = emptyMap(), unsupported = true)

        val result = seededRadioBuildResult(albumHomeMixSeededRadioRequest(album, seed), RadioService(provider))

        assertIs<UnsupportedOperationException>(assertIs<SeededRadioBuildResult.Failed>(result).error)
    }

    private fun track(id: String, albumId: AlbumId) = Track(
        id = TrackId(id),
        title = "Track $id",
        artistId = ArtistId("artist-$id"),
        artistName = "Artist $id",
        albumId = albumId,
        albumTitle = "Album ${albumId.value}",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

    /** Album radio and album loading fail the test: a mix must only use similar songs. */
    private class MixProvider(
        private val similar: Map<TrackId, List<Track>>,
        private val unsupported: Boolean = false,
    ) : MediaProvider {
        override val id = ProviderId("mix")
        override val displayName = "Mix"
        override val capabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )
        val similarRequests = mutableListOf<TrackId>()

        override suspend fun instantMixTracks(trackId: TrackId, count: Int): List<Track> {
            similarRequests += trackId
            if (unsupported) throw UnsupportedOperationException("similar songs unsupported")
            return similar[trackId].orEmpty().take(count)
        }

        override suspend fun albumRadio(albumId: AlbumId, count: Int): List<Track> = error("A mix must not use album radio")
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("A mix must not load album tracks")
        override suspend fun validateConnection(): ConnectionValidation = error("unused")
        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int): List<Artist> = error("unused")
        override suspend fun tracks(limit: Int): List<Track> = error("unused")
        override suspend fun search(query: String, limit: Int): MediaSearchResults = error("unused")
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = error("unused")
    }
}
