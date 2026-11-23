package app.naviamp.domain.cache

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflinePlaylistsTest {
    private fun track(id: String) = Track(TrackId(id), "Track $id", artistName = "Artist", albumTitle = null,
        durationSeconds = null, coverArtId = null, audioInfo = null, replayGain = null)

    @Test fun availabilityKeepsEveryRowInOrderIncludingRepeatsAndCountsOnlyPlayableRows() {
        val tracks = listOf(track("a"), track("b"), track("a"), track("c"))

        val availability = offlinePlaylistAvailability(tracks, playableTrackIds = setOf("a", "c"))

        assertEquals(listOf("a", "b", "a", "c"), availability.entries.map { it.track.id.value })
        assertEquals(listOf(0, 1, 2, 3), availability.entries.map { it.position })
        assertEquals(listOf(true, false, true, true), availability.entries.map { it.available })
        assertEquals(3, availability.availableCount)
        assertEquals(4, availability.totalCount)
        assertEquals(listOf("a", "a", "c"), availability.playableTracks.map { it.id.value })
        assertTrue(availability.canPlay)
    }

    @Test fun zeroAvailabilityKeepsThePlaylistVisibleButNotPlayable() {
        val availability = offlinePlaylistAvailability(listOf(track("a"), track("b")), emptySet())

        assertEquals(0, availability.availableCount)
        assertEquals(2, availability.totalCount)
        assertFalse(availability.canPlay)
        assertEquals(0, offlinePlaylistAvailableCount(listOf("a", "b"), emptySet()))
    }

    @Test fun summaryCountUsesTheSameRulesAsTheDetailView() {
        assertEquals(3, offlinePlaylistAvailableCount(listOf("a", "b", "a", "c"), setOf("a", "c")))
    }

    @Test fun refreshIsNeededOnlyWhenMembershipOrderOrDetailsChanged() {
        val playlist = Playlist("p", "Road trip", trackCount = 2)
        val saved = OfflinePlaylistSnapshot("source", playlist, listOf(track("a"), track("b")))

        assertFalse(saved.needsRefresh(playlist, listOf(track("a"), track("b"))))
        assertTrue(saved.needsRefresh(playlist, listOf(track("b"), track("a"))))
        assertTrue(saved.needsRefresh(playlist, listOf(track("a"), track("b"), track("a"))))
        assertTrue(saved.needsRefresh(playlist.copy(name = "Renamed"), listOf(track("a"), track("b"))))
    }

    @Test fun snapshotPlaylistReflectsSavedMembershipAndIsReadOnly() {
        val playlist = Playlist("p", "Road trip", trackCount = 99, canEdit = true, canManage = true)
        val saved = OfflinePlaylistSnapshot("source", playlist, listOf(track("a"), track("a")))

        val offline = saved.offlinePlaylist()

        assertEquals(2, offline.trackCount)
        assertFalse(offline.canEdit)
        assertFalse(offline.canManage)
    }
}
