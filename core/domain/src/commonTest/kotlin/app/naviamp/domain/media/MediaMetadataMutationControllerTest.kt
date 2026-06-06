package app.naviamp.domain.media

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaMetadataMutationControllerTest {
    @Test
    fun trackFavoriteReturnsUpdatedResultAndAppliesSharedUpdate() = runTest {
        val track = track("track")
        var appliedTrack: Track? = null
        val provider = FakeMediaProvider(
            capabilities = providerCapabilities(supportsTrackFavorites = true),
        )
        val controller = controller(
            provider = provider,
            knownTracks = listOf(track),
            applyTrackUpdate = { appliedTrack = it },
        )

        val result = controller.toggleTrackFavoriteByIdResult("track")

        assertTrue(result is MediaMetadataMutationResult.TrackUpdated)
        assertEquals(track.id to true, provider.trackFavoriteCalls.single())
        assertEquals("favorite-time", result.track.favoritedAtIso8601)
        assertEquals(result.track, appliedTrack)
        assertTrue(result.shouldRunPlatformSideEffects)
    }

    @Test
    fun artistFavoriteReturnsUpdatedResultAndAppliesSharedUpdate() = runTest {
        val artist = artist("artist")
        var appliedArtist: Artist? = null
        val provider = FakeMediaProvider(
            capabilities = providerCapabilities(supportsArtistFavorites = true),
        )
        val controller = controller(
            provider = provider,
            knownArtists = listOf(artist),
            applyArtistUpdate = { appliedArtist = it },
        )

        val result = controller.toggleArtistFavoriteByIdResult("artist")

        assertTrue(result is MediaMetadataMutationResult.ArtistUpdated)
        assertEquals(artist.id to true, provider.artistFavoriteCalls.single())
        assertEquals("favorite-time", result.artist.favoritedAtIso8601)
        assertEquals(result.artist, appliedArtist)
        assertTrue(result.shouldRunPlatformSideEffects)
    }

    @Test
    fun albumFavoriteReturnsUpdatedResultAndAppliesSharedUpdate() = runTest {
        val album = album("album")
        var appliedAlbum: Album? = null
        val provider = FakeMediaProvider(
            capabilities = providerCapabilities(supportsAlbumFavorites = true),
        )
        val controller = controller(
            provider = provider,
            knownAlbums = listOf(album),
            applyAlbumUpdate = { appliedAlbum = it },
        )

        val result = controller.toggleAlbumFavoriteByIdResult("album")

        assertTrue(result is MediaMetadataMutationResult.AlbumUpdated)
        assertEquals(album.id to true, provider.albumFavoriteCalls.single())
        assertEquals("favorite-time", result.album.favoritedAtIso8601)
        assertEquals(result.album, appliedAlbum)
        assertTrue(result.shouldRunPlatformSideEffects)
    }

    @Test
    fun trackRatingReturnsUpdatedResultAndAppliesSharedUpdate() = runTest {
        val track = track("track")
        var appliedTrack: Track? = null
        val provider = FakeMediaProvider(
            capabilities = providerCapabilities(supportsTrackRatings = true),
        )
        val controller = controller(
            provider = provider,
            knownTracks = listOf(track),
            applyTrackUpdate = { appliedTrack = it },
        )

        val result = controller.setTrackRatingResult(track, rating = 4)

        assertTrue(result is MediaMetadataMutationResult.TrackUpdated)
        assertEquals(track.id to 4, provider.trackRatingCalls.single())
        assertEquals(4, result.track.userRating)
        assertEquals(result.track, appliedTrack)
        assertTrue(result.shouldRunPlatformSideEffects)
    }

    @Test
    fun missingItemReturnsSharedFailureStatus() = runTest {
        var status: String? = null
        val provider = FakeMediaProvider(
            capabilities = providerCapabilities(supportsTrackFavorites = true),
        )
        val controller = controller(
            provider = provider,
            setStatus = { status = it },
        )

        val result = controller.toggleTrackFavoriteByIdResult("missing")

        assertEquals(MediaMetadataMutationResult.Failed("Track not found."), result)
        assertEquals("Track not found.", status)
        assertTrue(provider.trackFavoriteCalls.isEmpty())
    }

    @Test
    fun unsupportedProviderCapabilitySkipsWithoutStatus() = runTest {
        var status: String? = null
        var appliedTrack: Track? = null
        val track = track("track")
        val controller = controller(
            provider = FakeMediaProvider(),
            setStatus = { status = it },
            applyTrackUpdate = { appliedTrack = it },
        )

        val result = controller.toggleTrackFavoriteResult(track)

        assertEquals(MediaMetadataMutationResult.Skipped, result)
        assertNull(status)
        assertNull(appliedTrack)
    }

    private fun controller(
        provider: MediaProvider? = FakeMediaProvider(),
        setStatus: (String) -> Unit = {},
        knownTracks: List<Track> = emptyList(),
        knownArtists: List<Artist> = emptyList(),
        knownAlbums: List<Album> = emptyList(),
        applyTrackUpdate: (Track) -> Unit = {},
        applyArtistUpdate: (Artist) -> Unit = {},
        applyAlbumUpdate: (Album) -> Unit = {},
    ): MediaMetadataMutationController =
        MediaMetadataMutationController(
            provider = { provider },
            favoritedAtIso8601 = { "favorite-time" },
            setStatus = setStatus,
            knownTracks = { knownTracks },
            knownArtists = { knownArtists },
            knownAlbums = { knownAlbums },
            applyTrackUpdate = applyTrackUpdate,
            applyArtistUpdate = applyArtistUpdate,
            applyAlbumUpdate = applyAlbumUpdate,
        )

    private fun providerCapabilities(
        supportsTrackFavorites: Boolean = false,
        supportsArtistFavorites: Boolean = false,
        supportsAlbumFavorites: Boolean = false,
        supportsTrackRatings: Boolean = false,
    ): ProviderCapabilities =
        ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
            supportsTrackFavorites = supportsTrackFavorites,
            supportsArtistFavorites = supportsArtistFavorites,
            supportsAlbumFavorites = supportsAlbumFavorites,
            supportsTrackRatings = supportsTrackRatings,
        )

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )

    private fun artist(id: String): Artist =
        Artist(
            id = ArtistId(id),
            name = "Artist $id",
        )

    private fun album(id: String): Album =
        Album(
            id = AlbumId(id),
            title = "Album $id",
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
        )

    private class FakeMediaProvider(
        override val capabilities: ProviderCapabilities =
            ProviderCapabilities(
                supportsStreamingTranscode = false,
                supportsDownloadTranscode = false,
                supportsArtistRadio = false,
                supportsAlbumRadio = false,
                supportsTrackRadio = false,
            ),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
        val trackFavoriteCalls = mutableListOf<Pair<TrackId, Boolean>>()
        val artistFavoriteCalls = mutableListOf<Pair<ArtistId, Boolean>>()
        val albumFavoriteCalls = mutableListOf<Pair<AlbumId, Boolean>>()
        val trackRatingCalls = mutableListOf<Pair<TrackId, Int?>>()

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            AlbumDetails(
                album = Album(
                    id = albumId,
                    title = "Album ${albumId.value}",
                    artistName = "Artist",
                    coverArtId = null,
                    recentlyAddedAtIso8601 = null,
                ),
                tracks = emptyList(),
            )

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            ArtistDetails(
                artist = Artist(
                    id = artistId,
                    name = "Artist ${artistId.value}",
                ),
                albums = emptyList(),
            )

        override suspend fun artists(limit: Int): List<Artist> = emptyList()

        override suspend fun tracks(limit: Int): List<Track> = emptyList()

        override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()

        override suspend fun streamUrl(request: StreamRequest): String = "stream"

        override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
            trackFavoriteCalls += trackId to favorite
        }

        override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
            artistFavoriteCalls += artistId to favorite
        }

        override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
            albumFavoriteCalls += albumId to favorite
        }

        override suspend fun setTrackRating(trackId: TrackId, rating: Int?) {
            trackRatingCalls += trackId to rating
        }

        override fun coverArtUrl(coverArtId: String): String = coverArtId
    }
}
