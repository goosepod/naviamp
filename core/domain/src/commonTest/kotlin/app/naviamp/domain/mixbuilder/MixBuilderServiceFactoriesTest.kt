package app.naviamp.domain.mixbuilder

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.albummix.albumMixBuilderService
import app.naviamp.domain.artistmix.artistMixBuilderService
import app.naviamp.domain.genremix.genreMixBuilderService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.popular.ArtistPopularTracksClient
import app.naviamp.domain.popular.ArtistPopularTracksRepository
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MixBuilderServiceFactoriesTest {
    @Test
    fun artistFactoryUsesLocalSearchBeforeProviderFallback() = runTest {
        val provider = FakeMixProvider(searchArtists = listOf(artist("provider")))
        val service = artistMixBuilderService(
            sourceId = { "source" },
            provider = { provider },
            homeContent = { HomeContent() },
            localArtistSearch = { _, query, _ ->
                if (query == "local") listOf(artist("local")) else emptyList()
            },
            popularTracksService = fakePopularTracksService(),
            similarArtistsService = fakeSimilarArtistsService(),
        )

        assertEquals(listOf("local"), service.searchSuggestions("local", emptyList()).map { it.id.value })
        assertEquals(listOf("provider"), service.searchSuggestions("remote", emptyList()).map { it.id.value })
    }

    @Test
    fun albumFactoryUsesHomeCandidatesAndProviderFallback() = runTest {
        val provider = FakeMixProvider(randomAlbums = listOf(album("provider")))
        val home = HomeContent(randomAlbums = listOf(album("home")))
        val service = albumMixBuilderService(
            sourceId = { null },
            provider = { provider },
            homeContent = { home },
            localAlbumSearch = { _, _, _ -> emptyList() },
            localAlbumTracks = { _, _, _ -> emptyList() },
            providerAlbumTracks = { _, _ -> emptyList() },
            similarArtistsService = fakeSimilarArtistsService(),
        )

        assertEquals(listOf("home"), service.initialSuggestions(emptyList()).map { it.id.value })

        val fallback = albumMixBuilderService(
            sourceId = { null },
            provider = { provider },
            homeContent = { HomeContent() },
            localAlbumSearch = { _, _, _ -> emptyList() },
            localAlbumTracks = { _, _, _ -> emptyList() },
            providerAlbumTracks = { _, _ -> emptyList() },
            similarArtistsService = fakeSimilarArtistsService(),
        )
        assertEquals(listOf("provider"), fallback.initialSuggestions(emptyList()).map { it.id.value })
    }

    @Test
    fun albumFactoryPrefersProviderTracksAndFallsBackToLocalTracks() = runTest {
        val selectedAlbum = album("album")
        val providerTrack = track("provider-track", selectedAlbum)
        val localTrack = track("local-track", selectedAlbum)

        val providerService = albumMixBuilderService(
            sourceId = { "source" },
            provider = { FakeMixProvider() },
            homeContent = { HomeContent() },
            localAlbumSearch = { _, _, _ -> emptyList() },
            localAlbumTracks = { _, _, _ -> listOf(localTrack) },
            providerAlbumTracks = { _, _ -> listOf(providerTrack) },
            similarArtistsService = fakeSimilarArtistsService(),
        )
        assertEquals(listOf("provider-track"), providerService.selectedTracks(selectedAlbum).map { it.id.value })

        val localFallbackService = albumMixBuilderService(
            sourceId = { "source" },
            provider = { FakeMixProvider() },
            homeContent = { HomeContent() },
            localAlbumSearch = { _, _, _ -> emptyList() },
            localAlbumTracks = { _, _, _ -> listOf(localTrack) },
            providerAlbumTracks = { _, _ -> error("provider failed") },
            similarArtistsService = fakeSimilarArtistsService(),
        )
        assertEquals(listOf("local-track"), localFallbackService.selectedTracks(selectedAlbum).map { it.id.value })
    }

    @Test
    fun genreFactoryUsesProviderBeforeHomeFallback() = runTest {
        val home = HomeContent(genres = listOf(Genre("Home")))
        val providerService = genreMixBuilderService(
            provider = { FakeMixProvider(genres = listOf(Genre("Provider"))) },
            homeContent = { home },
        )
        assertEquals(listOf("Provider"), providerService.allGenres().map { it.name })

        val fallbackService = genreMixBuilderService(
            provider = { FakeMixProvider() },
            homeContent = { home },
        )
        assertEquals(listOf("Home"), fallbackService.allGenres().map { it.name })
    }

    private class FakeMixProvider(
        private val searchArtists: List<Artist> = emptyList(),
        private val randomAlbums: List<Album> = emptyList(),
        private val genres: List<Genre> = emptyList(),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("fake-mix")
        override val displayName: String = "Fake Mix"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            AlbumDetails(
                album = Album(
                    id = albumId,
                    title = albumId.value,
                    artistName = "Artist",
                    coverArtId = null,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
                tracks = emptyList(),
            )

        override suspend fun artist(artistId: ArtistId) = error("unused")

        override suspend fun artists(limit: Int): List<Artist> = emptyList()

        override suspend fun albumList(type: AlbumListType, limit: Int): List<Album> =
            if (type == AlbumListType.Random) randomAlbums else emptyList()

        override suspend fun tracks(limit: Int): List<Track> = emptyList()

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            MediaSearchResults(artists = searchArtists)

        override suspend fun genres(limit: Int): List<Genre> = genres

        override suspend fun streamUrl(request: StreamRequest): String = error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            "https://example.test/$coverArtId"
    }

    private object EmptyPopularTracksRepository : ArtistPopularTracksRepository {
        override fun artistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
        ): List<ArtistPopularTrackMatch> = emptyList()

        override fun replaceArtistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
            candidates: List<ArtistPopularTrackCandidate>,
            matchedTracksBySourceTrackId: Map<String, Track>,
            fetchedAtEpochMillis: Long,
        ) = Unit
    }

    private object EmptyPopularTracksClient : ArtistPopularTracksClient {
        override suspend fun popularTracks(
            artistName: String,
            limit: Int,
        ): List<ArtistPopularTrackCandidate> = emptyList()
    }

    private object EmptySimilarArtistsClient : SimilarArtistsClient {
        override suspend fun similarArtists(
            artistName: String,
            limit: Int,
        ): List<SimilarArtistCandidate> = emptyList()
    }

    private fun fakePopularTracksService(): ArtistPopularTracksService =
        ArtistPopularTracksService(
            repository = EmptyPopularTracksRepository,
            libraryTracksForArtist = { _, _ -> emptyList() },
            client = EmptyPopularTracksClient,
        )

    private fun fakeSimilarArtistsService(): SimilarArtistsService =
        SimilarArtistsService(
            libraryArtistsSearch = { _, _ -> emptyList() },
            client = EmptySimilarArtistsClient,
        )

    private fun artist(id: String): Artist =
        Artist(id = ArtistId(id), name = id)

    private fun album(id: String): Album =
        Album(
            id = AlbumId(id),
            title = id,
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
            releaseYear = null,
        )

    private fun track(id: String, album: Album): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistId = ArtistId("artist"),
            artistName = album.artistName,
            albumId = album.id,
            albumTitle = album.title,
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
