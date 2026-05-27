package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRadioRequestsTest {
    @Test
    fun createsLibraryGenreAndDecadeRadioRequests() {
        val library = libraryRadioRequest()
        assertEquals("Library radio", library.label)
        assertEquals(RecentRadioKind.Library, library.recentRadioStream.kind)

        val genre = genreRadioRequest(Genre("Shoegaze"))
        assertEquals("Shoegaze radio", genre.label)
        assertEquals("genre:Shoegaze", genre.recentRadioStream.id)
        assertEquals(RecentRadioKind.Genre, genre.recentRadioStream.kind)

        val decade = decadeRadioRequest(1980, 1989)
        assertEquals("1980-1989 radio", decade.label)
        assertEquals("decade:1980:1989", decade.recentRadioStream.id)
        assertEquals(RecentRadioKind.Decade, decade.recentRadioStream.kind)
    }

    @Test
    fun createsTrackAndPopularSeededRadioRequests() {
        val track = track("track-1", title = "Age of Consent", artistName = "New Order")
        val trackRequest = trackRadioRequest(track)
        assertEquals("Age of Consent radio", trackRequest.label)
        assertEquals(track, trackRequest.seedTrack)
        assertEquals("track:track-1", trackRequest.recentRadioStream.id)

        assertNull(popularTracksRadioRequest(emptyList(), seedLimit = 5))

        val tracks = listOf(
            track("one", artistName = "Slowdive"),
            track("two", artistName = "Slowdive"),
        )
        val popularRequest = assertNotNull(popularTracksRadioRequest(tracks, seedLimit = 5))
        assertTrue(popularRequest.seedTrack in tracks)
        assertEquals("${popularRequest.seedTrack.artistName} popular tracks radio", popularRequest.label)
        assertEquals(RecentRadioKind.Track, popularRequest.recentRadioStream.kind)
    }

    private fun track(
        id: String,
        title: String = "Track $id",
        artistName: String = "Artist",
    ): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = artistName,
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
