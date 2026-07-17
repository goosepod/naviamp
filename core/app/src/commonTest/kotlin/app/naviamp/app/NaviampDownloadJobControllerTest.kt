package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampDownloadJobControllerTest {
    @Test
    fun ownsStableIdsUpdatesCancellationAndReplacementRetryIntent() {
        var jobs = emptyList<DownloadJob>()
        var cancelled = false
        val controller = NaviampDownloadJobController({ jobs }, { jobs = it })

        val job = assertNotNull(controller.create("Album", listOf(track("one")), replaceExisting = true))
        assertEquals("download-000000000001", job.id)
        assertEquals(listOf(job), controller.currentJobs)
        controller.registerCancellation(job.id) { cancelled = true }
        controller.update(job.id, DownloadJobUpdate.TrackCompleted("one"))
        assertTrue(controller.cancel(job.id))
        assertTrue(cancelled)

        val retry = assertNotNull(controller.retry(job.id))
        assertEquals("Album", retry.label)
        assertTrue(retry.replaceExisting)
        assertNull(controller.create("Empty", emptyList(), replaceExisting = false))
    }

    private fun track(id: String) = Track(
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
