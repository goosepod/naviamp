package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.cache.LibrarySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun nextLibraryLimitOnlyGrowsWhenVisibleRowsReachCurrentLimit() {
        assertEquals(
            50,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(artists = artists(49)),
                tab = LibraryTab.Artists,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
        assertEquals(
            100,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(artists = artists(50)),
                tab = LibraryTab.Artists,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
        assertEquals(
            100,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(albums = albums(50)),
                tab = LibraryTab.Albums,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
    }

    @Test
    fun libraryLimitForOffsetRoundsUpToContainingPage() {
        assertEquals(50, libraryLimitForOffset(offset = 0, pageSize = 50))
        assertEquals(50, libraryLimitForOffset(offset = 49, pageSize = 50))
        assertEquals(100, libraryLimitForOffset(offset = 50, pageSize = 50))
    }

    private fun artists(count: Int): List<Artist> =
        (0 until count).map { index ->
            Artist(
                id = ArtistId("artist-$index"),
                name = "Artist $index",
            )
        }

    private fun albums(count: Int): List<Album> =
        (0 until count).map { index ->
            Album(
                id = AlbumId("album-$index"),
                title = "Album $index",
                artistName = "Artist",
                coverArtId = null,
                recentlyAddedAtIso8601 = null,
                releaseYear = null,
            )
        }
}
