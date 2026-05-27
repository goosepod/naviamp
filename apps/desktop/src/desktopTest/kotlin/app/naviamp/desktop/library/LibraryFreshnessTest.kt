package app.naviamp.desktop

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
}
