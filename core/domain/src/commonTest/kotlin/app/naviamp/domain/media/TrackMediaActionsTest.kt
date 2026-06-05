package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackMediaActionsTest {
    @Test
    fun trackPlaybackSelectionRejectsEmptyOrOutOfRangeSelections() {
        assertNull(trackPlaybackSelection(emptyList(), index = 0))
        assertNull(trackPlaybackSelection(listOf(track("one")), index = 1))
    }

    @Test
    fun trackPlaybackSelectionKeepsRequestedIndexUnlessShuffling() {
        val tracks = listOf(track("one"), track("two"), track("three"))

        assertEquals(TrackPlaybackSelection(tracks = tracks, index = 1), trackPlaybackSelection(tracks, index = 1))
        assertEquals(0, trackPlaybackSelection(tracks, index = 1, shuffle = true)?.index)
        assertEquals(tracks.toSet(), trackPlaybackSelection(tracks, index = 1, shuffle = true)?.tracks?.toSet())
    }

    @Test
    fun updatedTrackReplacesMatchingEntriesAcrossSharedStateShapes() {
        val original = track("one", rating = 1)
        val updated = original.copy(userRating = 5)
        val other = track("two")
        val albumDetails = AlbumDetails(album = album(), tracks = listOf(original, other))
        val searchResults = MediaSearchResults(tracks = listOf(original, other))
        val queue = PlaybackQueue(tracks = listOf(original, other), currentIndex = 0)

        assertEquals(updated, original.withUpdatedTrack(updated))
        assertEquals(listOf(updated, other), listOf(original, other).withUpdatedTrack(updated))
        assertEquals(listOf(updated, other), albumDetails.withUpdatedTrack(updated).tracks)
        assertEquals(listOf(updated, other), searchResults.withUpdatedTrack(updated).tracks)
        assertEquals(listOf(updated, other), queue.withUpdatedTrack(updated).tracks)
    }

    private fun album(): Album =
        Album(
            id = AlbumId("album"),
            title = "Album",
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
        )

    private fun track(
        id: String,
        rating: Int? = null,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
            userRating = rating,
        )
}
