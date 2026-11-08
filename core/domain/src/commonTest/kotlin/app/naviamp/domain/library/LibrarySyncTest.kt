package app.naviamp.domain.library

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.Genre
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibrarySyncTest {
    private fun previewTrack(id: String) = Track(TrackId(id), "Song", artistName = "Artist", albumTitle = null, durationSeconds = 60,
        coverArtId = null, audioInfo = null, replayGain = null,
        musicFolderId = "1")

    @Test fun previewSyncOnlyLoadsTracksReportsTotalsAndPrunesAfterSuccess() = kotlinx.coroutines.test.runTest {
        val repository = FakeLibraryIndexRepository()
        repository.tracks += previewTrack("stale").copy(musicFolderId = null)
        val events = mutableListOf<LibrarySyncProgress>()
        var requests = 0
        val provider = object : MediaProvider by FakeLibraryProvider() {
            override suspend fun artists(limit: Int): List<Artist> = error("Preview must not load artists")
            override suspend fun albumsPage(request: app.naviamp.domain.provider.MediaPageRequest): app.naviamp.domain.provider.MediaPage<Album> = error("Preview must not load albums when tracks are available")
            override suspend fun genres(limit: Int): List<Genre> = error("Preview must not load genres")
            override suspend fun libraryTracksPage(request: app.naviamp.domain.provider.MediaPageRequest): app.naviamp.domain.provider.MediaPage<Track> {
                requests++
                return app.naviamp.domain.provider.MediaPage(listOf(previewTrack("$requests")), request.offset, request.limit,
                    hasMore = requests == 1, nextContinuationToken = if (requests == 1) "next" else null, totalItemCount = 2)
            }
        }
        assertEquals(2, syncSmartPlaylistPreviewTracks("source", provider, repository, events::add))
        assertEquals(0, events.first().trackCount)
        assertEquals(listOf(1, 2), events.filter { it.total == 2 }.map { it.trackCount })
        assertEquals(setOf(TrackId("1"), TrackId("2")), repository.pruned)
        assertEquals(listOf("1", "2"), repository.tracks.map { it.id.value })
        assertTrue(!repository.syncStarted && !repository.syncCompleted)
        assertEquals(null, repository.checkedScanSignature)
    }

    @Test fun failedCancelledAndTruncatedPreviewSyncNeverPrune() = kotlinx.coroutines.test.runTest {
        for (mode in listOf("error", "cancel", "truncated")) {
            val repository = FakeLibraryIndexRepository()
            repository.tracks += previewTrack("previous")
            var requests = 0
            val provider = object : MediaProvider by FakeLibraryProvider() {
                override suspend fun libraryTracksPage(request: app.naviamp.domain.provider.MediaPageRequest): app.naviamp.domain.provider.MediaPage<Track> {
                    requests++
                    if (requests == 2) {
                        if (mode == "cancel") throw kotlinx.coroutines.CancellationException("Stopped")
                        error("Connection failed")
                    }
                    return app.naviamp.domain.provider.MediaPage(listOf(previewTrack("new")), 0, request.limit,
                        hasMore = mode != "truncated", totalItemCount = 2)
                }
            }
            kotlin.test.assertFails { syncSmartPlaylistPreviewTracks("source", provider, repository) }
            assertEquals(null, repository.pruned)
            assertTrue(repository.tracks.any { it.id.value == "previous" })
        }
    }

    @Test fun previewFallbackLoadsAlbumTracksWithoutArtistOrGenreRefresh() = kotlinx.coroutines.test.runTest {
        val repository = FakeLibraryIndexRepository()
        val provider = object : MediaProvider by FakeLibraryProvider(albums = listOf(album("fallback")),
            tracksByAlbum = mapOf(AlbumId("fallback") to listOf(previewTrack("fallback")))) {
            override suspend fun artists(limit: Int): List<Artist> = error("Unnecessary artist lookup")
            override suspend fun genres(limit: Int): List<Genre> = error("Unnecessary genre lookup")
        }
        val events = mutableListOf<LibrarySyncProgress>()
        syncSmartPlaylistPreviewTracks("source", provider, repository, events::add)
        assertTrue(events.any { it.phase == LibrarySyncProgressPhase.LoadingAlbums })
        assertTrue(repository.tracks.isNotEmpty())
        assertEquals(repository.tracks.map { it.id }.toSet(), repository.pruned)
    }

    @Test fun bulkTrackSyncUsesPagesWithoutAlbumDetailRequests() = kotlinx.coroutines.test.runTest {
        val repository = FakeLibraryIndexRepository()
        var pages = 0
        val provider = object : MediaProvider by FakeLibraryProvider() {
            override suspend fun libraryTracksPage(request: app.naviamp.domain.provider.MediaPageRequest): app.naviamp.domain.provider.MediaPage<Track> {
                pages++
                val track = Track(TrackId("t$pages"), "Song", artistName = "Artist", albumTitle = null,
                    durationSeconds = 60, coverArtId = null, audioInfo = null, replayGain = null)
                return app.naviamp.domain.provider.MediaPage(listOf(track), request.offset, request.limit,
                    hasMore = pages == 1, nextContinuationToken = if (pages == 1) "next" else null)
            }
            override suspend fun album(albumId: AlbumId): AlbumDetails = error("Bulk sync must not fetch album tracks")
        }
        val result = syncLibraryIndex("source", provider, repository, 10, 10, includeAlbumTracks = true)
        assertEquals(2, pages)
        assertEquals(2, result.trackCount)
        assertEquals(2, repository.tracks.size)
    }
    @Test
    fun syncLibraryIndexPagesArtistsAndAlbums() = kotlinx.coroutines.test.runTest {
        val artists = listOf(artist("one"), artist("two"))
        val albums = listOf(album("one"), album("two"), album("three"))
        val repository = FakeLibraryIndexRepository()
        val progress = mutableListOf<LibrarySyncProgress>()

        val result = syncLibraryIndex(
            sourceId = "source",
            provider = FakeLibraryProvider(artists = artists, albums = albums, genres = listOf(Genre("Jazz"))),
            libraryIndexRepository = repository,
            artistLimit = 10,
            albumPageSize = 2,
            onProgress = progress::add,
        )

        assertTrue(repository.syncStarted)
        assertTrue(repository.syncCompleted)
        assertEquals(artists, repository.artists)
        assertEquals(albums, repository.albums)
        assertEquals(emptyList(), repository.tracks)
        assertEquals(listOf("Jazz"), repository.genres.map(Genre::name))
        assertEquals(LibrarySyncResult(artistCount = 2, albumCount = 3, trackCount = 0), result)
        assertEquals(
            listOf(
                LibrarySyncProgressPhase.LoadingArtists,
                LibrarySyncProgressPhase.IndexedArtists,
                LibrarySyncProgressPhase.LoadingAlbums,
                LibrarySyncProgressPhase.IndexedAlbums,
                LibrarySyncProgressPhase.LoadingAlbums,
                LibrarySyncProgressPhase.IndexedAlbums,
                LibrarySyncProgressPhase.IndexedLibrary,
            ),
            progress.map { it.phase },
        )
        assertEquals(artists, progress.first { it.phase == LibrarySyncProgressPhase.IndexedArtists }.artists)
    }

    @Test
    fun syncLibraryIndexCanIndexAlbumTracks() = kotlinx.coroutines.test.runTest {
        val albums = listOf(album("one"), album("two"))
        val tracks = mapOf(
            AlbumId("one") to listOf(track("one-a"), track("one-b")),
            AlbumId("two") to listOf(track("two-a")),
        )
        val repository = FakeLibraryIndexRepository()

        val result = syncLibraryIndex(
            sourceId = "source",
            provider = FakeLibraryProvider(albums = albums, tracksByAlbum = tracks),
            libraryIndexRepository = repository,
            artistLimit = 10,
            albumPageSize = 10,
            includeAlbumTracks = true,
        )

        assertEquals(LibrarySyncResult(artistCount = 0, albumCount = 2, trackCount = 3), result)
        assertEquals(tracks.values.flatten(), repository.tracks)
        assertEquals(2, repository.trackDetailAlbumWrites)
    }

    @Test
    fun syncLibraryIndexAndMarkScanCheckedStoresProviderScanSignature() = kotlinx.coroutines.test.runTest {
        val repository = FakeLibraryIndexRepository()

        syncLibraryIndexAndMarkScanChecked(
            sourceId = "source",
            provider = FakeLibraryProvider(scanSignature = "scan-1"),
            libraryIndexRepository = repository,
            artistLimit = 10,
            albumPageSize = 10,
        )

        assertEquals("scan-1", repository.checkedScanSignature)
    }

    private class FakeLibraryProvider(
        private val artists: List<Artist> = emptyList(),
        private val albums: List<Album> = emptyList(),
        private val tracksByAlbum: Map<AlbumId, List<Track>> = emptyMap(),
        private val scanSignature: String? = null,
        private val genres: List<Genre> = emptyList(),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("provider")
        override val displayName: String = "Provider"
        override val capabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun libraryScanStatus() =
            app.naviamp.domain.provider.LibraryScanStatus(
                scanning = false,
                count = null,
                lastScan = scanSignature,
                folderCount = null,
            )

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            emptyList()

        override suspend fun album(albumId: AlbumId): AlbumDetails {
            val album = albums.first { it.id == albumId }
            return AlbumDetails(album = album, tracks = tracksByAlbum[albumId].orEmpty())
        }

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            throw UnsupportedOperationException()

        override suspend fun artists(limit: Int): List<Artist> =
            artists.take(limit)

        override suspend fun albums(limit: Int, offset: Int): List<Album> =
            albums.drop(offset).take(limit)

        override suspend fun tracks(limit: Int): List<Track> =
            emptyList()

        override suspend fun genres(limit: Int): List<Genre> =
            genres.take(limit)

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            MediaSearchResults()

        override suspend fun streamUrl(request: StreamRequest): String =
            ""

        override fun coverArtUrl(coverArtId: String): String =
            coverArtId
    }

    private class FakeLibraryIndexRepository : LocalLibraryIndexRepository {
        var syncStarted = false
            private set
        var syncCompleted = false
            private set
        val artists = mutableListOf<Artist>()
        val albums = mutableListOf<Album>()
        val tracks = mutableListOf<Track>()
        var trackDetailAlbumWrites = 0
            private set
        var checkedScanSignature: String? = null
            private set
        var genres = emptyList<Genre>()
            private set

        override fun mediaSource(sourceId: String) =
            null

        override fun markLibraryScanChecked(sourceId: String, signature: String) {
            checkedScanSignature = signature
        }

        override fun markLibrarySyncStarted(sourceId: String) {
            syncStarted = true
        }

        override fun markLibrarySyncCompleted(sourceId: String) {
            syncCompleted = true
        }

        override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
            this.artists += artists
        }

        override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
            if (albums.size == 1) trackDetailAlbumWrites += 1
            this.albums += albums
        }

        var pruned: Set<TrackId>? = null
        override fun pruneLibraryTracks(sourceId: String, retainedIds: Set<TrackId>) {
            pruned = retainedIds.toSet()
            tracks.removeAll { it.id !in retainedIds }
        }

        override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
            this.tracks += tracks
        }

        override fun replaceLibraryGenreInventory(sourceId: String, genres: List<Genre>) {
            this.genres = genres
        }

        override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot =
            LibrarySnapshot()

        override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot =
            LibrarySnapshot()

        override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
            null

        override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
            emptyList()

        override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
            null

        override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
            emptyList()

        override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
            emptyList()

        override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> =
            emptyList()

        override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
            LibraryIndexStats(artistCount = artists.size.toLong(), albumCount = albums.size.toLong(), trackCount = tracks.size.toLong())

        override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
            emptyList()

        override fun clearLibraryData(sourceId: String?) = Unit

        override fun artistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
        ): List<ArtistPopularTrackMatch> =
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

    private fun artist(id: String): Artist =
        Artist(id = ArtistId(id), name = "Artist $id")

    private fun album(id: String): Album =
        Album(
            id = AlbumId(id),
            title = "Album $id",
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
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
}
