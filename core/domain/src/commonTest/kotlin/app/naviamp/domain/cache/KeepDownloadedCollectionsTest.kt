package app.naviamp.domain.cache

import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class KeepDownloadedCollectionsTest {
    @Test
    fun reconciliationDownloadsOnlyMissingTracks() {
        val plan = planKeepDownloadedReconciliation(
            tracks = listOf(track("one"), track("two"), track("two")),
            previousTrackIds = setOf("one"),
            downloadedTrackIds = setOf("one"),
            managedTrackIds = emptySet(),
            trackIdsRequiredByOtherPolicies = emptySet(),
            removeUnneededFiles = false,
        )

        assertEquals(listOf("two"), plan.tracksToDownload.map { it.id.value })
        assertEquals(setOf("one", "two"), plan.nextTrackIds)
        assertEquals(emptySet(), plan.trackIdsToRemove)
    }

    @Test
    fun cleanupRemovesOnlyManagedTracksNoLongerRequiredElsewhere() {
        val plan = planKeepDownloadedReconciliation(
            tracks = listOf(track("kept")),
            previousTrackIds = setOf("kept", "shared", "managed", "manual"),
            downloadedTrackIds = setOf("kept", "shared", "managed", "manual"),
            managedTrackIds = setOf("shared", "managed"),
            trackIdsRequiredByOtherPolicies = setOf("shared"),
            removeUnneededFiles = true,
        )

        assertEquals(setOf("managed"), plan.trackIdsToRemove)
    }

    @Test
    fun cleanupPreservesManualDownloadsEvenWhenAnAutomaticCollectionPreviouslyManagedThem() {
        val plan = planKeepDownloadedReconciliation(
            tracks = emptyList(),
            previousTrackIds = setOf("legacy", "manual", "other-policy", "unneeded"),
            downloadedTrackIds = setOf("legacy", "manual", "other-policy", "unneeded"),
            managedTrackIds = setOf("legacy", "manual", "other-policy", "unneeded"),
            trackIdsRequiredByOtherPolicies = setOf("other-policy"),
            manuallyRetainedTrackIds = setOf("legacy", "manual"),
            removeUnneededFiles = true,
        )

        assertEquals(setOf("unneeded"), plan.trackIdsToRemove)
    }

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = id,
        artistId = ArtistId("artist"),
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}
