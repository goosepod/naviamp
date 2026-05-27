package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesktopRecentRadioActionsTest {
    @Test
    fun resolvesSimpleRecentRadioActions() {
        assertEquals(
            DesktopRecentRadioAction.PlayLibrary,
            recentRadioAction(libraryRecentRadioStream()),
        )
        assertEquals(
            DesktopRecentRadioAction.PlayRandomAlbum,
            recentRadioAction(
                RecentRadioStream(
                    id = "random-album",
                    label = "Random album radio",
                    kind = RecentRadioKind.RandomAlbum,
                ),
            ),
        )

        val genre = assertIs<DesktopRecentRadioAction.PlayGenre>(
            recentRadioAction(genreRecentRadioStream(Genre("Post-punk"))),
        )
        assertEquals("Post-punk", genre.genre.name)

        val decade = assertIs<DesktopRecentRadioAction.PlayDecade>(
            recentRadioAction(decadeRecentRadioStream(1980, 1989)),
        )
        assertEquals(1980, decade.fromYear)
        assertEquals(1989, decade.toYear)
    }

    @Test
    fun resolvesEntityBackedRecentRadioActions() {
        val artistAction = assertIs<DesktopRecentRadioAction.PlayArtist>(
            recentRadioAction(artistRecentRadioStream(artist())),
        )
        assertEquals("artist-1", artistAction.artist.id.value)

        val albumAction = assertIs<DesktopRecentRadioAction.PlayAlbum>(
            recentRadioAction(albumRecentRadioStream(album())),
        )
        assertEquals("album-1", albumAction.album.id.value)

        val randomAlbumAction = assertIs<DesktopRecentRadioAction.PlayAlbum>(
            recentRadioAction(randomAlbumRecentRadioStream(album())),
        )
        assertEquals("album-1", randomAlbumAction.album.id.value)

        val trackAction = assertIs<DesktopRecentRadioAction.PlayTrack>(
            recentRadioAction(trackRecentRadioStream(track())),
        )
        assertEquals("track-1", trackAction.track.id.value)
    }

    @Test
    fun incompleteRecentRadioStreamsDoNotResolve() {
        assertNull(
            recentRadioAction(
                RecentRadioStream(
                    id = "genre-missing",
                    label = "Missing genre",
                    kind = RecentRadioKind.Genre,
                ),
            ),
        )
        assertNull(
            recentRadioAction(
                RecentRadioStream(
                    id = "decade-missing",
                    label = "Missing decade",
                    kind = RecentRadioKind.Decade,
                    fromYear = 1980,
                ),
            ),
        )
    }

    private fun artist(): Artist =
        Artist(id = ArtistId("artist-1"), name = "New Order")

    private fun album(): Album =
        Album(
            id = AlbumId("album-1"),
            title = "Power, Corruption & Lies",
            artistName = "New Order",
            coverArtId = "cover-1",
            recentlyAddedAtIso8601 = null,
            releaseYear = 1983,
        )

    private fun track(): Track =
        Track(
            id = TrackId("track-1"),
            title = "Age of Consent",
            artistId = ArtistId("artist-1"),
            artistName = "New Order",
            albumId = AlbumId("album-1"),
            albumTitle = "Power, Corruption & Lies",
            durationSeconds = 315,
            coverArtId = "cover-1",
            audioInfo = null,
            replayGain = null,
        )
}
