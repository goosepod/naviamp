package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedReconciliationPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampDownloadJobControllerTest {
    @Test
    fun preflightRequiresConnectionAndHonorsMobileDataSetting() {
        assertEquals(
            "Connect to Navidrome before downloading.",
            naviampDownloadPreflightStatus(
                providerAvailable = false,
                sourceId = null,
                isActiveNetworkMobileData = false,
                allowMobileDownloads = true,
            ),
        )
        assertEquals(
            "Downloads over mobile data are disabled.",
            naviampDownloadPreflightStatus(
                providerAvailable = true,
                sourceId = "source",
                isActiveNetworkMobileData = true,
                allowMobileDownloads = false,
            ),
        )
        assertNull(
            naviampDownloadPreflightStatus(
                providerAvailable = true,
                sourceId = "source",
                isActiveNetworkMobileData = false,
                allowMobileDownloads = false,
            ),
        )
    }

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

    @Test
    fun mapsKeepDownloadedReconciliationToStatusDownloadAndRefreshEffects() {
        val policy = KeepDownloadedCollectionPolicy(
            sourceId = "source",
            kind = KeepDownloadedCollectionKind.Playlist,
            collectionId = "playlist",
            name = "Road trip",
        )

        assertEquals(
            NaviampKeepDownloadedReconciliationApplication(
                tracksToDownload = emptyList(),
                downloadLabel = null,
                status = "Road trip is up to date.",
                refreshDownloads = true,
            ),
            keepDownloadedReconciliationApplication(
                policy,
                KeepDownloadedReconciliationPlan(
                    nextTrackIds = emptySet(),
                    tracksToDownload = emptyList(),
                    trackIdsToRemove = setOf("removed"),
                ),
            ),
        )
        assertEquals(
            "Keeping Road trip downloaded",
            keepDownloadedReconciliationApplication(
                policy,
                KeepDownloadedReconciliationPlan(
                    nextTrackIds = setOf("one"),
                    tracksToDownload = listOf(track("one")),
                    trackIdsToRemove = emptySet(),
                ),
            ).downloadLabel,
        )
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
