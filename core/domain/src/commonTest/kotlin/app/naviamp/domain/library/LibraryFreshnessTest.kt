package app.naviamp.domain.library

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.LibraryScanStatus
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.source.SavedMediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryFreshnessTest {
    @Test
    fun marksFirstSeenSignatureChecked() {
        val update = LibraryFreshness(
            signature = "scan-1",
            previousSignature = null,
            scanning = false,
        ).evaluateLibraryFreshness(currentStatus = null)

        assertEquals("scan-1", update.signatureToMarkChecked)
        assertEquals(null, update.status)
        assertEquals(false, update.clearStatus)
    }

    @Test
    fun reportsChangedLibraryWhenSignatureChanges() {
        val update = LibraryFreshness(
            signature = "scan-2",
            previousSignature = "scan-1",
            scanning = false,
        ).evaluateLibraryFreshness(currentStatus = null)

        assertEquals(null, update.signatureToMarkChecked)
        assertEquals("Library changed on server. Refresh library to import updates.", update.status)
        assertEquals(false, update.clearStatus)
    }

    @Test
    fun reportsScanningWhenChangedSignatureIsStillScanning() {
        val update = LibraryFreshness(
            signature = "scan-2",
            previousSignature = "scan-1",
            scanning = true,
        ).evaluateLibraryFreshness(currentStatus = null)

        assertEquals("Navidrome is scanning. Refresh library after the scan finishes.", update.status)
    }

    @Test
    fun clearsStaleChangedStatusWhenSignatureMatchesAgain() {
        val update = LibraryFreshness(
            signature = "scan-1",
            previousSignature = "scan-1",
            scanning = false,
        ).evaluateLibraryFreshness(currentStatus = "Library changed on server. Refresh library to import updates.")

        assertEquals(null, update.signatureToMarkChecked)
        assertEquals(null, update.status)
        assertEquals(true, update.clearStatus)
    }

    @Test
    fun librarySyncHelpersShareCommonStatusAndAutoSyncRules() {
        assertTrue(shouldAutoSyncLibrary(LibraryIndexStats(artistCount = 0, albumCount = 0, trackCount = 0)))
        assertFalse(shouldAutoSyncLibrary(LibraryIndexStats(artistCount = 1, albumCount = 0, trackCount = 0)))
        assertTrue(
            shouldAutoSyncLibrary(
                LibraryIndexStats(artistCount = 1, albumCount = 0, trackCount = 0),
                source = savedSource(lastSyncStartedAt = null, lastSyncCompletedAt = null),
            ),
        )
        assertTrue(
            shouldAutoSyncLibrary(
                LibraryIndexStats(artistCount = 1, albumCount = 0, trackCount = 0),
                source = savedSource(lastSyncStartedAt = 20, lastSyncCompletedAt = 10),
            ),
        )
        assertFalse(
            shouldAutoSyncLibrary(
                LibraryIndexStats(artistCount = 1, albumCount = 0, trackCount = 0),
                source = savedSource(lastSyncStartedAt = 10, lastSyncCompletedAt = 20),
            ),
        )
        assertEquals("Connect to Navidrome to import your library.", libraryConnectionRequiredStatus())
        assertEquals("Starting library import...", librarySyncStartingStatus())
        assertEquals("Library refreshed.", librarySyncCompletedStatus())
        assertEquals("Could not import library.", librarySyncErrorStatus(IllegalStateException()))
        assertEquals("Nope", librarySyncErrorStatus(IllegalStateException("Nope")))
    }

    private fun savedSource(
        lastSyncStartedAt: Long?,
        lastSyncCompletedAt: Long?,
    ): SavedMediaSource =
        SavedMediaSource(
            id = "source",
            providerId = "provider",
            cacheNamespace = "provider:server:user",
            displayName = "Provider",
            baseUrl = "https://example.test",
            username = "user",
            token = "token",
            salt = "salt",
            createdAtEpochMillis = 0,
            lastConnectedAtEpochMillis = null,
            lastSyncStartedAtEpochMillis = lastSyncStartedAt,
            lastSyncCompletedAtEpochMillis = lastSyncCompletedAt,
        )

    @Test
    fun libraryFreshnessUpdateFetchesProviderAndSourceState() = kotlinx.coroutines.test.runTest {
        val update = libraryFreshnessUpdate(
            sourceId = "source",
            provider = FakeFreshnessProvider(signature = "scan-2", scanning = true),
            mediaSourceRepository = FakeMediaSourceRepository(previousSignature = "scan-1"),
            currentStatus = null,
        )

        assertEquals(null, update.signatureToMarkChecked)
        assertEquals("Navidrome is scanning. Refresh library after the scan finishes.", update.status)
        assertEquals(false, update.clearStatus)
    }

    @Test
    fun librarySyncStartPlanHandlesMissingSyncingSkippedAndStartStates() {
        val provider = FakeFreshnessProvider(signature = null, scanning = false)
        assertEquals(
            LibrarySyncStartPlan.MissingConnection,
            librarySyncStartPlan(provider = null, sourceId = "source", syncing = false, force = false) { true },
        )
        assertEquals(
            LibrarySyncStartPlan.AlreadySyncing,
            librarySyncStartPlan(provider = provider, sourceId = "source", syncing = true, force = false) { true },
        )
        assertEquals(
            LibrarySyncStartPlan.SkipAutoSync,
            librarySyncStartPlan(provider = provider, sourceId = "source", syncing = false, force = false) { false },
        )
        assertEquals(
            LibrarySyncStartPlan.Start(sourceId = "source", provider = provider),
            librarySyncStartPlan(provider = provider, sourceId = "source", syncing = false, force = true) { false },
        )
    }

    @Test
    fun librarySyncCoordinatorRunsSyncAndFinalizesState() = kotlinx.coroutines.test.runTest {
        var syncing = false
        var status: String? = null
        var completed = false
        val progress = mutableListOf<String>()
        val provider = FakeFreshnessProvider(signature = null, scanning = false)
        val coordinator = LibrarySyncCoordinator(
            provider = { provider },
            sourceId = { "source" },
            syncing = { syncing },
            setSyncing = { syncing = it },
            status = { status },
            setStatus = { status = it },
            libraryIndexRepository = FakeLibraryIndexRepository(hasUsableIndex = false),
            mediaSourceRepository = FakeMediaSourceRepository(previousSignature = null),
        )

        coordinator.startSync(
            force = false,
            sync = { sourceId, activeProvider, setProgressStatus ->
                assertEquals("source", sourceId)
                assertEquals(provider, activeProvider)
                setProgressStatus("Progress")
                progress += status.orEmpty()
            },
            onCompleted = { completed = true },
        )

        assertEquals(false, syncing)
        assertEquals(null, status)
        assertEquals(true, completed)
        assertEquals(listOf("Progress"), progress)
    }

    @Test
    fun librarySyncCoordinatorSkipsAutoSyncAndReportsFailures() = kotlinx.coroutines.test.runTest {
        var syncing = false
        var status: String? = "Existing"
        var failedStatus: String? = null
        val coordinator = LibrarySyncCoordinator(
            provider = { FakeFreshnessProvider(signature = null, scanning = false) },
            sourceId = { "source" },
            syncing = { syncing },
            setSyncing = { syncing = it },
            status = { status },
            setStatus = { status = it },
            libraryIndexRepository = FakeLibraryIndexRepository(hasUsableIndex = true),
            mediaSourceRepository = FakeMediaSourceRepository(
                previousSignature = null,
                lastSyncStartedAt = 10,
                lastSyncCompletedAt = 20,
            ),
        )

        coordinator.startSync(force = false, sync = { _, _, _ -> error("should not run") })

        assertEquals(null, status)
        coordinator.startSync(
            force = true,
            sync = { _, _, _ -> throw IllegalStateException("Nope") },
            onFailed = { failedStatus = it },
        )

        assertEquals(false, syncing)
        assertEquals("Nope", status)
        assertEquals("Nope", failedStatus)
    }

    @Test
    fun librarySyncCoordinatorAppliesFreshnessUpdates() = kotlinx.coroutines.test.runTest {
        var status: String? = null
        val indexRepository = FakeLibraryIndexRepository(hasUsableIndex = true)
        val coordinator = LibrarySyncCoordinator(
            provider = { FakeFreshnessProvider(signature = "scan-2", scanning = false) },
            sourceId = { "source" },
            syncing = { false },
            setSyncing = {},
            status = { status },
            setStatus = { status = it },
            libraryIndexRepository = indexRepository,
            mediaSourceRepository = FakeMediaSourceRepository(previousSignature = "scan-1"),
        )

        coordinator.checkFreshness()

        assertEquals("Library changed on server. Refresh library to import updates.", status)
        assertEquals(null, indexRepository.checkedScanSignature)
    }

    private class FakeFreshnessProvider(
        private val signature: String?,
        private val scanning: Boolean,
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

        override suspend fun libraryScanStatus(): LibraryScanStatus =
            LibraryScanStatus(scanning = scanning, count = null, lastScan = signature, folderCount = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            emptyList()

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            throw UnsupportedOperationException()

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            throw UnsupportedOperationException()

        override suspend fun artists(limit: Int): List<Artist> =
            emptyList()

        override suspend fun tracks(limit: Int): List<Track> =
            emptyList()

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            MediaSearchResults()

        override suspend fun streamUrl(request: StreamRequest): String =
            ""

        override fun coverArtUrl(coverArtId: String): String =
            coverArtId
    }

    private class FakeMediaSourceRepository(
        private val previousSignature: String?,
        private val lastSyncStartedAt: Long? = null,
        private val lastSyncCompletedAt: Long? = null,
    ) : MediaSourceRepository {
        override fun latestMediaSource(): SavedMediaSource? =
            null

        override fun mediaSources(): List<SavedMediaSource> =
            emptyList()

        override fun mediaSource(sourceId: String): SavedMediaSource =
            SavedMediaSource(
                id = sourceId,
                providerId = "provider",
                cacheNamespace = "provider",
                displayName = "Provider",
                baseUrl = "https://example.test",
                username = "user",
                token = "token",
                salt = "salt",
                createdAtEpochMillis = 0,
                lastConnectedAtEpochMillis = null,
                lastSyncStartedAtEpochMillis = lastSyncStartedAt,
                lastSyncCompletedAtEpochMillis = lastSyncCompletedAt,
                lastLibraryScanSignature = previousSignature,
            )

        override fun deleteMediaSource(sourceId: String) = Unit
    }

    private class FakeLibraryIndexRepository(
        private val hasUsableIndex: Boolean,
    ) : LocalLibraryIndexRepository {
        var checkedScanSignature: String? = null

        override fun mediaSource(sourceId: String): SavedMediaSource? = null

        override fun markLibraryScanChecked(sourceId: String, signature: String) {
            checkedScanSignature = signature
        }

        override fun markLibrarySyncStarted(sourceId: String) = Unit
        override fun markLibrarySyncCompleted(sourceId: String) = Unit
        override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) = Unit
        override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) = Unit
        override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) = Unit
        override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot = LibrarySnapshot()
        override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot =
            LibrarySnapshot()
        override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? = null
        override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> = emptyList()
        override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? = null
        override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> = emptyList()
        override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
            emptyList()
        override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> = emptyList()
        override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
            LibraryIndexStats(
                artistCount = if (hasUsableIndex) 1 else 0,
                albumCount = 0,
                trackCount = 0,
            )
        override fun libraryAlbumYears(sourceId: String): List<app.naviamp.domain.cache.LibraryAlbumYear> = emptyList()
        override fun clearLibraryData(sourceId: String?) = Unit
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
}
