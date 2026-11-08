package app.naviamp.ui

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadioTrackArtworkMatchTest {
    private fun track(artist: String, title: String, cover: String? = "cover-$title") = Track(
        id = TrackId(title),
        title = title,
        artistName = artist,
        albumTitle = null,
        durationSeconds = null,
        coverArtId = cover,
        audioInfo = null,
        replayGain = null,
    )

    @Test
    fun picksTheAnnouncedSongEvenWhenItIsNotTheFirstResult() {
        val results = listOf(
            track("Chappell Roan", "Good Luck, Babe!"),
            track("Chappell Roan", "HOT TO GO!"),
            track("Chappell Roan", "casual", cover = null),
            track("Chappell Roan", "Casual"),
        )

        assertEquals("cover-Casual", radioTrackArtworkMatch(results, "Chappell Roan - Casual")?.coverArtId)
        assertEquals("cover-Casual", radioTrackArtworkMatch(results, "Casual")?.coverArtId)
    }

    @Test
    fun keepsTheStationArtworkWhenNoResultIsTheAnnouncedSong() {
        val results = listOf(track("Chappell Roan", "Good Luck, Babe!"))

        assertNull(radioTrackArtworkMatch(results, "Chappell Roan - Casual"))
        assertNull(radioTrackArtworkMatch(results, null))
    }
}
