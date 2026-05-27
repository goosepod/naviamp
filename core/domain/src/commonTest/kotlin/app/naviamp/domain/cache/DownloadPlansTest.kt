package app.naviamp.domain.cache

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadPlansTest {
    @Test
    fun blocksDownloadsWithoutProviderOrSource() {
        assertEquals(
            DownloadBlockReason.MissingConnection,
            planDownloadTracks(listOf(track("one")), hasProvider = false, hasSource = true).blockedReason,
        )
        assertEquals(
            DownloadBlockReason.MissingConnection,
            planDownloadTracks(listOf(track("one")), hasProvider = true, hasSource = false).blockedReason,
        )
    }

    @Test
    fun blocksEmptyDownloads() {
        val plan = planDownloadTracks(emptyList(), hasProvider = true, hasSource = true)

        assertFalse(plan.isReady)
        assertEquals(DownloadBlockReason.EmptyTracks, plan.blockedReason)
    }

    @Test
    fun blocksMobileDownloadsWhenDisabled() {
        val plan = planDownloadTracks(
            tracks = listOf(track("one")),
            hasProvider = true,
            hasSource = true,
            isActiveNetworkMobileData = true,
            allowMobileDownloads = false,
        )

        assertEquals(DownloadBlockReason.MobileDataDisabled, plan.blockedReason)
    }

    @Test
    fun returnsReadyTracksAndCanDeduplicate() {
        val tracks = listOf(track("one"), track("one"), track("two"))

        val plan = planDownloadTracks(
            tracks = tracks,
            hasProvider = true,
            hasSource = true,
            deduplicateTracks = true,
        )

        assertTrue(plan.isReady)
        assertNull(plan.blockedReason)
        assertEquals(listOf(TrackId("one"), TrackId("two")), plan.tracks.map { it.id })
    }

    @Test
    fun downloadStatusMessagesMatchSharedUiCopy() {
        assertEquals("Connect to Navidrome before downloading.", downloadConnectionRequiredStatus())
        assertEquals("Album did not return any tracks.", emptyDownloadStatus("Album"))
        assertEquals("Downloads over mobile data are disabled.", downloadMobileDataDisabledStatus())
        assertEquals("Downloading Album...", downloadStartingStatus("Album"))
        assertEquals("Downloading Album (2/4)...", downloadProgressStatus("Album", index = 1, total = 4))
        assertEquals("Downloaded Album.", downloadCompletedStatus("Album"))
        assertEquals("Downloaded Album (4 tracks).", downloadCompletedStatus("Album", completed = 4))
        assertEquals("network failed", downloadErrorStatus("Album", IllegalStateException("network failed")))
        assertEquals("Could not download Album.", downloadErrorStatus("Album", RuntimeException()))
        assertEquals("Removed One.", downloadedTrackRemovedStatus("One"))
        assertEquals("Could not remove download.", downloadRemoveErrorStatus(RuntimeException()))
    }

    @Test
    fun blockedStatusMapsReasonToMessage() {
        assertEquals(
            "Album did not return any tracks.",
            downloadBlockedStatus(DownloadBlockReason.EmptyTracks, "Album"),
        )
    }

    @Test
    fun downloadTracksForPlaybackReturnsNullForInvalidSelection() {
        assertNull(downloadTracksForPlayback(emptyList<Track>(), index = 0) { it })
        assertNull(downloadTracksForPlayback(listOf(track("one")), index = 1) { it })
    }

    @Test
    fun downloadTracksForPlaybackReturnsTrackListForValidSelection() {
        val tracks = listOf(track("one"), track("two"))

        assertEquals(tracks, downloadTracksForPlayback(tracks, index = 1) { it })
    }

    private fun track(id: String, title: String = id): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
