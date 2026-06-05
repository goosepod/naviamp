package app.naviamp.domain.library

import app.naviamp.domain.cache.LibraryIndexStats
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
}
