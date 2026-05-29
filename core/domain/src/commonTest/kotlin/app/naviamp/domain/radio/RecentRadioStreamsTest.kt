package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.RecentRadioKind
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentRadioStreamsTest {
    @Test
    fun recentRadioStreamsMoveSelectedStreamToFront() {
        val selected = genreRecentRadioStream(Genre("Shoegaze"))
        val recent = recentRadioStreamsWith(
            listOf(
                libraryRecentRadioStream(),
                selected.copy(label = "Old label"),
                decadeRecentRadioStream(1980, 1989),
            ),
            selected,
        )

        assertEquals(
            listOf("genre:Shoegaze", "library", "decade:1980:1989"),
            recent.map { it.id },
        )
        assertEquals("Shoegaze radio", recent.first().label)
    }

    @Test
    fun recentRadioStreamsAreLimited() {
        val streams = (1..14).map { genreRecentRadioStream(Genre("Genre $it")) }
        val recent = recentRadioStreamsWith(streams, libraryRecentRadioStream())

        assertEquals(MaxRecentRadioStreams, recent.size)
        assertEquals("library", recent.first().id)
        assertEquals("genre:Genre 11", recent.last().id)
    }

    @Test
    fun createsLibraryGenreAndDecadeStreams() {
        assertEquals(
            RecentRadioKind.Library,
            libraryRecentRadioStream().kind,
        )

        val genreStream = genreRecentRadioStream(Genre("Shoegaze"))
        assertEquals("genre:Shoegaze", genreStream.id)
        assertEquals("Shoegaze radio", genreStream.label)
        assertEquals(RecentRadioKind.Genre, genreStream.kind)
        assertEquals("Shoegaze", genreStream.genre)

        val decadeStream = decadeRecentRadioStream(1980, 1989)
        assertEquals("decade:1980:1989", decadeStream.id)
        assertEquals("1980-1989 radio", decadeStream.label)
        assertEquals(RecentRadioKind.Decade, decadeStream.kind)
        assertEquals(1980, decadeStream.fromYear)
        assertEquals(1989, decadeStream.toYear)
    }

    @Test
    fun createsEntityBackedStreams() {
        val artist = Artist(id = ArtistId("artist-1"), name = "New Order")
        val album = Album(
            id = AlbumId("album-1"),
            title = "Power, Corruption & Lies",
            artistName = "New Order",
            coverArtId = "cover-1",
            recentlyAddedAtIso8601 = null,
            releaseYear = 1983,
        )
        val track = Track(
            id = TrackId("track-1"),
            title = "Age of Consent",
            artistId = artist.id,
            artistName = artist.name,
            albumId = album.id,
            albumTitle = album.title,
            durationSeconds = 315,
            coverArtId = album.coverArtId,
            audioInfo = null,
            replayGain = null,
        )

        val artistStream = artistRecentRadioStream(artist)
        assertEquals("artist:artist-1", artistStream.id)
        assertEquals(RecentRadioKind.Artist, artistStream.kind)
        assertEquals("artist-1", artistStream.artist?.id)

        val albumStream = albumRecentRadioStream(album)
        assertEquals("album:album-1", albumStream.id)
        assertEquals(RecentRadioKind.Album, albumStream.kind)
        assertEquals("album-1", albumStream.album?.id)

        val randomAlbumStream = randomAlbumRecentRadioStream(album)
        assertEquals("random-album:album-1", randomAlbumStream.id)
        assertEquals(RecentRadioKind.RandomAlbum, randomAlbumStream.kind)
        assertEquals("album-1", randomAlbumStream.album?.id)

        val trackStream = trackRecentRadioStream(track)
        assertEquals("track:track-1", trackStream.id)
        assertEquals(RecentRadioKind.Track, trackStream.kind)
        assertEquals("track-1", trackStream.track?.id)

        val popularStream = popularTracksRecentRadioStream(track)
        assertEquals("popular:artist-1", popularStream.id)
        assertEquals("New Order popular tracks radio", popularStream.label)
        assertEquals(RecentRadioKind.Track, popularStream.kind)
        assertEquals("track-1", popularStream.track?.id)
    }
}
