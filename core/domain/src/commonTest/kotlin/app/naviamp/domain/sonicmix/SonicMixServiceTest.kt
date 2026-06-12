package app.naviamp.domain.sonicmix

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

class SonicMixServiceTest {
    @Test
    fun buildMixBlendsSeedsAndReturnsPlainTracks() = runTest {
        val seedOne = track("seed-one", artist = "Seed One")
        val seedTwo = track("seed-two", artist = "Seed Two")
        val provider = FakeSonicMixProvider(
            matches = mapOf(
                seedOne.id to listOf(
                    SonicSimilarTrack(seedOne, 1.0),
                    SonicSimilarTrack(track("one-a", artist = "Artist A"), 0.95),
                    SonicSimilarTrack(track("shared", artist = "Artist C"), 0.8),
                ),
                seedTwo.id to listOf(
                    SonicSimilarTrack(seedTwo, 1.0),
                    SonicSimilarTrack(track("two-a", artist = "Artist B"), 0.94),
                    SonicSimilarTrack(track("shared", artist = "Artist C"), 0.9),
                ),
            ),
        )

        val mix = SonicMixService(provider).buildMix(
            SonicMixRequest(listOf(seedOne, seedTwo), targetLength = 5),
        )

        assertEquals(listOf("one-a", "two-a", "shared"), mix.map { track -> track.id.value })
        assertEquals(listOf(seedOne.id, seedTwo.id), provider.requestedTrackIds)
    }

    @Test
    fun buildMixReturnsEmptyWithoutEnoughSeedsOrCapability() = runTest {
        val provider = FakeSonicMixProvider(supportsSonicSimilarity = false)

        val unavailable = SonicMixService(provider).buildMix(
            SonicMixRequest(listOf(track("one"), track("two"))),
        )
        val oneSeed = SonicMixService(FakeSonicMixProvider()).buildMix(
            SonicMixRequest(listOf(track("one"))),
        )

        assertEquals(emptyList(), unavailable)
        assertEquals(emptyList(), oneSeed)
    }

    @Test
    fun blendMixCanBiasFavorites() {
        val seedOne = track("seed-one")
        val seedTwo = track("seed-two")
        val favorite = track("favorite", favorite = true)
        val closer = track("closer")

        val mix = blendSonicMix(
            seeds = listOf(seedOne, seedTwo),
            matchesBySeed = listOf(
                seedOne to listOf(SonicSimilarTrack(closer, 0.9), SonicSimilarTrack(favorite, 0.7)),
                seedTwo to emptyList(),
            ),
            targetLength = 2,
            bias = SonicMixBias.Favorites,
        )

        assertEquals(listOf("favorite", "closer"), mix.map { track -> track.id.value })
    }

    private class FakeSonicMixProvider(
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

private fun track(
    id: String,
    artist: String = "Artist",
    favorite: Boolean = false,
): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistId = ArtistId(artist),
        artistName = artist,
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
        favoritedAtIso8601 = if (favorite) "2026-06-12T00:00:00Z" else null,
    )
