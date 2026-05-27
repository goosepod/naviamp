package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
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

    @Test
    fun createsEntitySeededRadioRequests() {
        val artist = Artist(id = ArtistId("artist-1"), name = "New Order")
        val album = Album(
            id = AlbumId("album-1"),
            title = "Power, Corruption & Lies",
            artistName = "New Order",
            coverArtId = "cover-1",
            recentlyAddedAtIso8601 = null,
            releaseYear = 1983,
        )
        val seedTrack = track("track-1", title = "Age of Consent", artistName = "New Order")

        val randomAlbum = randomAlbumSeededRadioRequest(album, seedTrack)
        assertEquals("Power, Corruption & Lies radio", randomAlbum.label)
        assertEquals(seedTrack, randomAlbum.seedTrack)
        assertEquals("random-album:album-1", randomAlbum.recentRadioStream.id)
        assertEquals(RecentRadioKind.RandomAlbum, randomAlbum.recentRadioStream.kind)

        val artistRadio = artistSeededRadioRequest(artist, seedTrack)
        assertEquals("New Order radio", artistRadio.label)
        assertEquals(seedTrack, artistRadio.seedTrack)
        assertEquals("artist:artist-1", artistRadio.recentRadioStream.id)
        assertEquals(RecentRadioKind.Artist, artistRadio.recentRadioStream.kind)

        val albumRadio = albumSeededRadioRequest(album, seedTrack)
        assertEquals("Power, Corruption & Lies radio", albumRadio.label)
        assertEquals(seedTrack, albumRadio.seedTrack)
        assertEquals("album:album-1", albumRadio.recentRadioStream.id)
        assertEquals(RecentRadioKind.Album, albumRadio.recentRadioStream.kind)
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
