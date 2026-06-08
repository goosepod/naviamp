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
import app.naviamp.domain.cache.MediaSourceRepository
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
        assertEquals("Connect to Navidrome to import your library.", libraryConnectionRequiredStatus())
        assertEquals("Starting library import...", librarySyncStartingStatus())
        assertEquals("Library refreshed.", librarySyncCompletedStatus())
        assertEquals("Could not import library.", librarySyncErrorStatus(IllegalStateException()))
        assertEquals("Nope", librarySyncErrorStatus(IllegalStateException("Nope")))
    }

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
                lastSyncStartedAtEpochMillis = null,
                lastSyncCompletedAtEpochMillis = null,
                lastLibraryScanSignature = previousSignature,
            )

        override fun deleteMediaSource(sourceId: String) = Unit
    }
}
