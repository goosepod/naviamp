package app.naviamp.domain.sonichome

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SonicHomeDiscoveryServiceTest {
    @Test
    fun loadRowsUsesRecentStarredAndDeepCutSeeds() = runTest {
        val recent = track("recent", lastPlayedAt = "2026-06-10T00:00:00Z")
        val starred = track("starred", favorite = "2026-06-09T00:00:00Z")
        val provider = FakeSonicHomeProvider(
            matches = mapOf(
                recent.id to listOf(
                    SonicSimilarTrack(track("recent-match", lastPlayedAt = "2026-06-08T00:00:00Z"), 0.95),
                ),
                starred.id to listOf(
                    SonicSimilarTrack(track("starred-match", favorite = "2026-06-07T00:00:00Z"), 0.9),
                    SonicSimilarTrack(track("deep-cut", playCount = 0), 0.85),
                ),
            ),
        )

        val rows = SonicHomeDiscoveryService(provider).loadRows(listOf(recent, starred))

        assertEquals(listOf("recent-match"), rows.row(SonicHomeDiscoveryRowId.MoreLikeRecentPlays)?.tracks?.ids())
        assertEquals(
            listOf("recent-match", "deep-cut", "starred-match"),
            rows.row(SonicHomeDiscoveryRowId.SonicDeepCuts)?.tracks?.ids(),
        )
        assertEquals(listOf("starred-match", "deep-cut"), rows.row(SonicHomeDiscoveryRowId.SimilarToStarredTracks)?.tracks?.ids())
        assertTrue(provider.requestedTrackIds.containsAll(listOf(recent.id, starred.id)))
    }

    @Test
    fun loadRowsReturnsEmptyWithoutCapability() = runTest {
        val rows = SonicHomeDiscoveryService(
            FakeSonicHomeProvider(supportsSonicSimilarity = false),
        ).loadRows(listOf(track("recent", lastPlayedAt = "2026-06-10T00:00:00Z")))

        assertTrue(rows.rows.isEmpty())
    }

    private class FakeSonicHomeProvider(
        private val supportsSonicSimilarity: Boolean = true,
        private val matches: Map<TrackId, List<SonicSimilarTrack>> = emptyMap(),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
            supportsSonicSimilarity = supportsSonicSimilarity,
        )
        val requestedTrackIds = mutableListOf<TrackId>()

        override suspend fun validateConnection(): ConnectionValidation = error("unused")
        override suspend fun recentlyAddedAlbums(limit: Int) = error("unused")
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int) = error("unused")
        override suspend fun tracks(limit: Int) = error("unused")
        override suspend fun search(query: String, limit: Int): MediaSearchResults = error("unused")
        override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int): List<SonicSimilarTrack> {
            requestedTrackIds += trackId
            return matches[trackId].orEmpty()
        }
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = error("unused")
    }
}

private fun List<Track>.ids(): List<String> =
    map { track -> track.id.value }

private fun track(
    id: String,
    favorite: String? = null,
    playCount: Int? = null,
    lastPlayedAt: String? = null,
): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistId = ArtistId("artist-$id"),
        artistName = "Artist $id",
        albumId = AlbumId("album-$id"),
        albumTitle = "Album $id",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
        favoritedAtIso8601 = favorite,
        playCount = playCount,
        lastPlayedAtIso8601 = lastPlayedAt,
    )
