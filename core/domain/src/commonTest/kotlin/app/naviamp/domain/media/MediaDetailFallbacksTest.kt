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
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.source.SavedMediaSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDetailFallbacksTest {
    @Test
    fun albumDetailsFromLibraryTracksReturnsNullWithoutTracks() {
        assertNull(
            albumDetailsFromLibraryTracks(
                albumId = AlbumId("album"),
                fallbackTitle = "Album",
                fallbackArtistName = "Artist",
                tracks = emptyList(),
            ),
        )
    }

    @Test
    fun albumDetailsFromLibraryTracksBuildsDetailFromFirstTrack() {
        val tracks = listOf(
            track("one", albumId = "album", albumTitle = "Track Album", releaseYear = 2020),
            track("two", albumId = "album", albumTitle = "Track Album", releaseYear = 2020),
        )

        assertEquals(
            AlbumDetails(
                album = Album(
                    id = AlbumId("album"),
                    title = "Fallback Album",
                    artistName = "Fallback Artist",
                    coverArtId = "cover-one",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = 2020,
                ),
                tracks = tracks,
            ),
            albumDetailsFromLibraryTracks(
                albumId = AlbumId("album"),
                fallbackTitle = "Fallback Album",
                fallbackArtistName = "Fallback Artist",
                tracks = tracks,
            ),
        )
    }

    @Test
    fun artistDetailsFromLibraryTracksReturnsNullWithoutTracksOrName() {
        assertNull(
            artistDetailsFromLibraryTracks(
                artistId = ArtistId("artist"),
                fallbackName = null,
                tracks = emptyList(),
            ),
        )
    }

    @Test
    fun artistDetailsFromLibraryTracksUsesFallbackNameWithoutTracks() {
        val detail = artistDetailsFromLibraryTracks(
            artistId = ArtistId("artist"),
            fallbackName = "Fallback Artist",
            tracks = emptyList(),
        )

        assertEquals(Artist(ArtistId("artist"), "Fallback Artist"), detail?.artist)
        assertEquals(emptyList(), detail?.albums)
    }

    @Test
    fun artistDetailsFromLibraryTracksBuildsSortedAlbumsFromTracks() {
        val detail = artistDetailsFromLibraryTracks(
            artistId = ArtistId("artist"),
            fallbackName = null,
            tracks = listOf(
                track("three", albumId = "album-c", albumTitle = "Zeta", releaseYear = null),
                track("one", albumId = "album-a", albumTitle = "Alpha", releaseYear = 2020),
                track("two", albumId = "album-b", albumTitle = "Beta", releaseYear = 2019),
                track("duplicate", albumId = "album-a", albumTitle = "Alpha", releaseYear = 2020),
            ),
        )

        assertEquals(Artist(ArtistId("artist"), "Artist"), detail?.artist)
        assertEquals(
            listOf(
                Album(
                    id = AlbumId("album-b"),
                    title = "Beta",
                    artistName = "Artist",
                    coverArtId = "cover-two",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = 2019,
                ),
                Album(
                    id = AlbumId("album-a"),
                    title = "Alpha",
                    artistName = "Artist",
                    coverArtId = "cover-one",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = 2020,
                ),
                Album(
                    id = AlbumId("album-c"),
                    title = "Zeta",
                    artistName = "Artist",
                    coverArtId = "cover-three",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
            ),
            detail?.albums,
        )
    }

    @Test
    fun artistDetailsFromLibraryTracksBuildsUnknownAlbumForAlbumlessTracks() {
        val detail = artistDetailsFromLibraryTracks(
            artistId = ArtistId("orchid"),
            fallbackName = "Orchid",
            tracks = listOf(
                track(
                    "orchid-track",
                    artistId = "orchid",
                    artistName = "Orchid",
                    albumId = null,
                    albumTitle = null,
                    releaseYear = null,
                ),
            ),
        )

        assertEquals(Artist(ArtistId("orchid"), "Orchid"), detail?.artist)
        assertEquals(
            listOf(
                Album(
                    id = AlbumId("artist:orchid:local-album:unknown-album:orchid"),
                    title = "Unknown Album",
                    artistName = "Orchid",
                    coverArtId = "cover-orchid-track",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
            ),
            detail?.albums,
        )
    }

    @Test
    fun loadArtistDetailsBackfillsEmptyProviderAlbumsFromLocalArtistNameMatches() = runTest {
        val providerDetail = ArtistDetails(
            artist = Artist(ArtistId("provider-orchid"), "Orchid"),
            albums = emptyList(),
            info = null,
        )
        val localTrack = track(
            "orchid-track",
            artistId = "local-orchid",
            artistName = "Orchid",
            albumId = null,
            albumTitle = null,
            releaseYear = null,
        )

        val detail = loadArtistDetails(
            libraryIndexRepository = FakeLibraryIndexRepository(
                tracksByArtistName = mapOf("Orchid" to listOf(localTrack)),
            ),
            providerResponseService = fakeProviderResponseService(),
            provider = FakeFlowProvider(artistDetails = providerDetail),
            artistId = ArtistId("provider-orchid"),
            fallbackName = "Orchid",
            sourceId = "source",
        )

        assertEquals(providerDetail.artist, detail.artist)
        assertEquals(
            listOf(
                Album(
                    id = AlbumId("artist:provider-orchid:local-album:unknown-album:orchid"),
                    title = "Unknown Album",
                    artistName = "Orchid",
                    coverArtId = "cover-orchid-track",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
            ),
            detail.albums,
        )
    }

    @Test
    fun loadAlbumDetailsBackfillsSyntheticUnknownAlbumFromLocalArtistNameMatches() = runTest {
        val localTrack = track(
            "orchid-track",
            artistId = "local-orchid",
            artistName = "Orchid",
            albumId = null,
            albumTitle = null,
            releaseYear = null,
        )
        val albumId = AlbumId("artist:provider-orchid:local-album:unknown-album:orchid")

        val detail = loadAlbumDetails(
            libraryIndexRepository = FakeLibraryIndexRepository(
                tracksByArtistName = mapOf("Orchid" to listOf(localTrack)),
            ),
            providerResponseService = fakeProviderResponseService(),
            provider = FakeFlowProvider(),
            albumId = albumId,
            fallbackTitle = "Unknown Album",
            fallbackArtistName = "Orchid",
            sourceId = "source",
        )

        assertEquals(
            AlbumDetails(
                album = Album(
                    id = albumId,
                    title = "Unknown Album",
                    artistName = "Orchid",
                    coverArtId = "cover-orchid-track",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
                tracks = listOf(localTrack),
            ),
            detail,
        )
    }

    @Test
    fun artistDetailStatusHelpersMatchSharedCopy() {
        assertEquals("Loading artist...", artistDetailLoadingStatus(null))
        assertEquals("Loading Artist...", artistDetailLoadingStatus("Artist"))
        assertEquals(
            "No albums found for Artist.",
            artistDetailLoadedStatus(
                app.naviamp.domain.ArtistDetails(
                    artist = Artist(ArtistId("artist"), "Artist"),
                    albums = emptyList(),
                    info = null,
                ),
            ),
        )
        assertEquals("Could not load artist.", artistDetailLoadErrorStatus(RuntimeException()))
        assertEquals("network failed", artistDetailLoadErrorStatus(RuntimeException("network failed")))
    }

    @Test
    fun popularTracksUpdateLimitsTracksAndReportsEmptyMatches() {
        val matches = listOf(popularMatch("one"), popularMatch("two"), popularMatch("three"))

        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = matches.take(2).map { it.matchedTrack },
                status = null,
            ),
            artistPopularTracksUpdate(matches, displayLimit = 2),
        )
        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = emptyList(),
                status = "No popular tracks matched songs in your library.",
            ),
            artistPopularTracksUpdate(emptyList(), displayLimit = 2),
        )
        assertEquals("Loading popular tracks...", loadingPopularTracksStatus())
        assertEquals(
            "Popular tracks unavailable: no connected media source.",
            missingPopularTracksSourceStatus(),
        )
        assertEquals("Popular tracks unavailable: unknown error", popularTracksUnavailableStatus(RuntimeException()))
    }

    @Test
    fun loadArtistPopularTracksUpdateHandlesMissingSourceAndFailure() = runTest {
        val artist = Artist(ArtistId("artist"), "Artist")
        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = emptyList(),
                status = "Popular tracks unavailable: no connected media source.",
            ),
            loadArtistPopularTracksUpdate(
                sourceId = null,
                artist = artist,
                loadPopularTracks = { _, _, _ -> error("Should not load without source") },
            ),
        )
        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = emptyList(),
                status = "Popular tracks unavailable: failed",
            ),
            loadArtistPopularTracksUpdate(
                sourceId = "source",
                artist = artist,
                loadPopularTracks = { _, _, _ -> throw RuntimeException("failed") },
            ),
        )
    }

    @Test
    fun similarArtistsUpdateLimitsArtistsAndReportsEmptyMatches() {
        val artists = listOf(similarArtist("one"), similarArtist("two"), similarArtist("three"))

        assertEquals(
            SimilarArtistsUpdate(
                artists = artists.take(2),
                status = null,
            ),
            similarArtistsUpdate(artists, displayLimit = 2),
        )
        assertEquals(
            SimilarArtistsUpdate(
                artists = emptyList(),
                status = "No similar artists found.",
            ),
            similarArtistsUpdate(emptyList(), displayLimit = 2),
        )
        assertEquals("Finding similar artists...", loadingSimilarArtistsStatus())
        assertEquals("Similar artists unavailable: unknown error", similarArtistsUnavailableStatus(RuntimeException()))
    }

    @Test
    fun loadSimilarArtistsUpdateHandlesFailure() = runTest {
        assertEquals(
            SimilarArtistsUpdate(
                artists = emptyList(),
                status = "Similar artists unavailable: failed",
            ),
            loadSimilarArtistsUpdate(
                artist = Artist(ArtistId("artist"), "Artist"),
                loadSimilarArtists = { _, _ -> throw RuntimeException("failed") },
            ),
        )
    }

    @Test
    fun albumDetailStatusHelpersMatchSharedCopy() {
        assertEquals("Loading album...", albumDetailLoadingStatus(null))
        assertEquals("Loading Album...", albumDetailLoadingStatus("Album"))
        assertEquals("Connected.", albumDetailLoadedStatus())
        assertEquals("Could not load album.", albumDetailLoadErrorStatus(RuntimeException()))
        assertEquals("network failed", albumDetailLoadErrorStatus(RuntimeException("network failed")))
    }

    @Test
    fun artistDetailFlowCoordinatorPublishesLoadingLoadedAndAfterLoaded() = runTest {
        val statuses = mutableListOf<String?>()
        var appliedDetail: app.naviamp.domain.ArtistDetails? = null
        var afterLoadedArtist: Artist? = null
        val detail = app.naviamp.domain.ArtistDetails(
            artist = Artist(ArtistId("artist"), "Artist"),
            albums = listOf(album("album")),
            info = null,
        )

        ArtistDetailFlowCoordinator(
            setStatus = { statuses += it },
            applyDetail = { appliedDetail = it },
        ).load(
            request = artistDetailFlowRequest("Artist"),
            loadDetails = { detail },
            afterLoaded = { afterLoadedArtist = it.artist },
        )

        assertEquals(listOf<String?>("Loading Artist...", "Connected."), statuses)
        assertEquals(detail, appliedDetail)
        assertEquals(detail.artist, afterLoadedArtist)
    }

    @Test
    fun artistDetailFlowCoordinatorPublishesFailureStatus() = runTest {
        val statuses = mutableListOf<String?>()
        var appliedDetail: app.naviamp.domain.ArtistDetails? = null

        ArtistDetailFlowCoordinator(
            setStatus = { statuses += it },
            applyDetail = { appliedDetail = it },
        ).load(
            request = artistDetailFlowRequest("Artist"),
            loadDetails = { throw RuntimeException("artist failed") },
        )

        assertEquals(listOf<String?>("Loading Artist...", "artist failed"), statuses)
        assertNull(appliedDetail)
    }

    @Test
    fun albumDetailFlowCoordinatorPublishesLoadingLoadedAndFailure() = runTest {
        val successStatuses = mutableListOf<String?>()
        var appliedDetail: AlbumDetails? = null
        val detail = AlbumDetails(
            album = album("album"),
            tracks = listOf(track("one", albumId = "album", albumTitle = "Album", releaseYear = 2020)),
        )

        AlbumDetailFlowCoordinator(
            setStatus = { successStatuses += it },
            applyDetail = { appliedDetail = it },
        ).load(
            request = albumDetailFlowRequest("Album"),
            loadDetails = { detail },
        )

        val failureStatuses = mutableListOf<String?>()
        AlbumDetailFlowCoordinator(
            setStatus = { failureStatuses += it },
            applyDetail = {},
        ).load(
            request = albumDetailFlowRequest("Album"),
            loadDetails = { throw RuntimeException("album failed") },
        )

        assertEquals(listOf<String?>("Loading Album...", "Connected."), successStatuses)
        assertEquals(detail, appliedDetail)
        assertEquals(listOf<String?>("Loading Album...", "album failed"), failureStatuses)
    }

    private fun track(
        id: String,
        artistId: String = "artist",
        artistName: String = "Artist",
        albumId: String?,
        albumTitle: String?,
        releaseYear: Int?,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistId = ArtistId(artistId),
            artistName = artistName,
            albumId = albumId?.let(::AlbumId),
            albumTitle = albumTitle,
            albumReleaseYear = releaseYear,
            durationSeconds = 120,
            coverArtId = "cover-$id",
            audioInfo = null,
            replayGain = null,
        )

    private fun album(id: String): Album =
        Album(
            id = AlbumId(id),
            title = "Album",
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
        )

    private fun popularMatch(id: String): ArtistPopularTrackMatch =
        ArtistPopularTrackMatch(
            candidate = ArtistPopularTrackCandidate(
                source = "test",
                sourceTrackId = id,
                rank = 1,
                title = "Track $id",
            ),
            matchedTrack = track(id, albumId = "album", albumTitle = "Album", releaseYear = 2020),
            fetchedAtEpochMillis = 1L,
        )

    private fun similarArtist(id: String): SimilarArtistMatch =
        SimilarArtistMatch(
            candidate = SimilarArtistCandidate(
                source = "test",
                sourceArtistId = id,
                name = "Artist $id",
            ),
            matchedArtist = Artist(ArtistId(id), "Artist $id"),
        )

    private fun artistDetailFlowRequest(fallbackName: String?): ArtistDetailFlowRequest =
        ArtistDetailFlowRequest(
            libraryIndexRepository = FakeLibraryIndexRepository(),
            providerResponseService = fakeProviderResponseService(),
            provider = FakeFlowProvider(),
            artistId = ArtistId("artist"),
            fallbackName = fallbackName,
            sourceId = "source",
        )

    private fun albumDetailFlowRequest(fallbackTitle: String?): AlbumDetailFlowRequest =
        AlbumDetailFlowRequest(
            libraryIndexRepository = FakeLibraryIndexRepository(),
            providerResponseService = fakeProviderResponseService(),
            provider = FakeFlowProvider(),
            albumId = AlbumId("album"),
            fallbackTitle = fallbackTitle,
            fallbackArtistName = "Artist",
            sourceId = "source",
        )

    private fun fakeProviderResponseService(): ProviderResponseService =
        ProviderResponseService(
            object : ProviderResponseCacheRepository {
                override suspend fun <T> cachedProviderResponse(
                    provider: MediaProvider,
                    resourceType: String,
                    resourceId: String,
                    decode: (String) -> T,
                    encode: (T) -> String,
                    fetch: suspend () -> T,
                ): T = fetch()

                override fun invalidateProviderResponses(provider: MediaProvider, resourceType: String) = Unit

                override fun invalidateProviderResponse(provider: MediaProvider, resourceType: String, resourceId: String) = Unit
            },
        )

    private class FakeLibraryIndexRepository(
        private val tracksByArtist: Map<ArtistId, List<Track>> = emptyMap(),
        private val tracksByArtistName: Map<String, List<Track>> = emptyMap(),
    ) : LocalLibraryIndexRepository {
        override fun mediaSource(sourceId: String): SavedMediaSource? = error("unused")
        override fun markLibraryScanChecked(sourceId: String, signature: String) = Unit
        override fun markLibrarySyncStarted(sourceId: String) = Unit
        override fun markLibrarySyncCompleted(sourceId: String) = Unit
        override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) = Unit
        override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) = Unit
        override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) = Unit
        override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot = error("unused")
        override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot = error("unused")
        override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? = error("unused")
        override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> = emptyList()
        override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? = error("unused")
        override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
            tracksByArtist[artistId].orEmpty().take(limit.toInt())

        override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
            tracksByArtistName[artistName].orEmpty().take(limit.toInt())
        override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> = emptyList()
        override fun libraryIndexStats(sourceId: String): LibraryIndexStats = error("unused")
        override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> = emptyList()
        override fun clearLibraryData(sourceId: String?) = Unit
        override fun artistPopularTracks(sourceId: String, artistId: ArtistId, source: String): List<ArtistPopularTrackMatch> =
            emptyList()

        override fun replaceArtistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
            candidates: List<ArtistPopularTrackCandidate>,
            matchedTracksBySourceTrackId: Map<String, Track>,
            fetchedAtEpochMillis: Long,
        ) = Unit
    }

    private class FakeFlowProvider(
        private val artistDetails: ArtistDetails? = null,
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
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
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = artistDetails ?: error("unused")
        override suspend fun artists(limit: Int): List<Artist> = emptyList()
        override suspend fun tracks(limit: Int): List<Track> = emptyList()
        override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = "https://example.test/$coverArtId"
    }
}
