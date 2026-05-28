package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDetailFallbacksTest {
    @Test
    fun artistDetailsFromLibraryTracksReturnsNullWithoutTracksOrName() {
        assertNull(
            artistDetailsFromLibraryTracks(
                artistId = ArtistId("artist"),
                fallbackName = null,
                tracks = emptyList(),
            ),
        )
    }

    @Test
    fun artistDetailsFromLibraryTracksUsesFallbackNameWithoutTracks() {
        val detail = artistDetailsFromLibraryTracks(
            artistId = ArtistId("artist"),
            fallbackName = "Fallback Artist",
            tracks = emptyList(),
        )

        assertEquals(Artist(ArtistId("artist"), "Fallback Artist"), detail?.artist)
        assertEquals(emptyList(), detail?.albums)
    }

    @Test
    fun artistDetailsFromLibraryTracksBuildsSortedAlbumsFromTracks() {
        val detail = artistDetailsFromLibraryTracks(
            artistId = ArtistId("artist"),
            fallbackName = null,
            tracks = listOf(
                track("three", albumId = "album-c", albumTitle = "Zeta", releaseYear = null),
                track("one", albumId = "album-a", albumTitle = "Alpha", releaseYear = 2020),
                track("two", albumId = "album-b", albumTitle = "Beta", releaseYear = 2019),
                track("duplicate", albumId = "album-a", albumTitle = "Alpha", releaseYear = 2020),
            ),
        )

        assertEquals(Artist(ArtistId("artist"), "Artist"), detail?.artist)
        assertEquals(
            listOf(
                Album(
                    id = AlbumId("album-b"),
                    title = "Beta",
                    artistName = "Artist",
                    coverArtId = "cover-two",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = 2019,
                ),
                Album(
                    id = AlbumId("album-a"),
                    title = "Alpha",
                    artistName = "Artist",
                    coverArtId = "cover-one",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = 2020,
                ),
                Album(
                    id = AlbumId("album-c"),
                    title = "Zeta",
                    artistName = "Artist",
                    coverArtId = "cover-three",
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
            ),
            detail?.albums,
        )
    }

    @Test
    fun artistDetailStatusHelpersMatchSharedCopy() {
        assertEquals("Loading artist...", artistDetailLoadingStatus(null))
        assertEquals("Loading Artist...", artistDetailLoadingStatus("Artist"))
        assertEquals(
            "No albums found for Artist.",
            artistDetailLoadedStatus(
                app.naviamp.domain.ArtistDetails(
                    artist = Artist(ArtistId("artist"), "Artist"),
                    albums = emptyList(),
                    info = null,
                ),
            ),
        )
        assertEquals("Could not load artist.", artistDetailLoadErrorStatus(RuntimeException()))
        assertEquals("network failed", artistDetailLoadErrorStatus(RuntimeException("network failed")))
    }

    private fun track(
        id: String,
        albumId: String,
        albumTitle: String,
        releaseYear: Int?,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistId = ArtistId("artist"),
            artistName = "Artist",
            albumId = AlbumId(albumId),
            albumTitle = albumTitle,
            albumReleaseYear = releaseYear,
            durationSeconds = 120,
            coverArtId = "cover-$id",
            audioInfo = null,
            replayGain = null,
        )
}
