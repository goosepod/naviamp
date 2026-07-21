package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.media.albumDetailLoadErrorStatus
import app.naviamp.domain.media.artistDetailLoadErrorStatus
import app.naviamp.domain.media.trackAlbum
import app.naviamp.domain.media.trackArtist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopMediaDetailsTest {
    @Test
    fun trackArtistAndAlbumReturnNullWhenIdsAreMissing() {
        val track = track()

        assertNull(trackArtist(track))
        assertNull(trackAlbum(track))
    }

    @Test
    fun trackArtistAndAlbumMapTrackMetadata() {
        val track = track(
            artistId = ArtistId("artist"),
            albumId = AlbumId("album"),
            albumTitle = "Album",
            releaseYear = 2024,
        )

        assertEquals(Artist(ArtistId("artist"), "Artist"), trackArtist(track))
        assertEquals(
            Album(
                id = AlbumId("album"),
                title = "Album",
                artistName = "Artist",
                coverArtId = "cover",
                recentlyAddedAtIso8601 = null,
                releaseYear = 2024,
            ),
            trackAlbum(track),
        )
    }

    @Test
    fun detailStatusHelpersUseFallbackMessages() {
        assertEquals("Could not load album.", albumDetailLoadErrorStatus(Exception()))
        assertEquals("Could not load artist.", artistDetailLoadErrorStatus(Exception()))
    }

    private fun track(
        artistId: ArtistId? = null,
        albumId: AlbumId? = null,
        albumTitle: String? = null,
        releaseYear: Int? = null,
    ): Track =
        Track(
            id = TrackId("track"),
            title = "Track",
            artistId = artistId,
            artistName = "Artist",
            albumId = albumId,
            albumTitle = albumTitle,
            albumReleaseYear = releaseYear,
            durationSeconds = 120,
            coverArtId = "cover",
            audioInfo = null,
            replayGain = null,
        )

}
