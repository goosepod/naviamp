package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampDownloadStatusTest {
    @Test
    fun maintenanceStatusesHandleZeroOneAndManyDownloads() {
        assertEquals("Downloads are up to date.", downloadsRefreshStatus(0))
        assertEquals("Removed 1 missing download.", downloadsRefreshStatus(1))
        assertEquals("Removed 3 missing downloads.", downloadsRefreshStatus(3))
        assertEquals("Deleted 1 download.", downloadsDeletedStatus(1))
        assertEquals("Deleted 4 downloads.", downloadsDeletedStatus(4))
    }

    @Test
    fun keepDownloadedStatusesUseTheCollectionNameAndPreferRealErrors() {
        assertEquals(
            "Road trip will no longer be kept downloaded. Existing files were kept.",
            keepDownloadedDisabledStatus("Road trip"),
        )
        assertEquals("Road trip is up to date.", keepDownloadedUpToDateStatus("Road trip"))
        assertEquals(
            "Could not keep Road trip downloaded.",
            keepDownloadedErrorStatus("Road trip", IllegalStateException()),
        )
        assertEquals(
            "Server unavailable",
            keepDownloadedRefreshErrorStatus("Road trip", IllegalStateException("Server unavailable")),
        )
    }
}
