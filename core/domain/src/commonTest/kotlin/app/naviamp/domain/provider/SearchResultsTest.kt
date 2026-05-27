package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchResultsTest {
    @Test
    fun normalizedSearchQueryTrimsBlankQueriesAway() {
        assertEquals(null, normalizedSearchQuery("   "))
        assertEquals("new order", normalizedSearchQuery("  new order  "))
    }

    @Test
    fun totalCountIncludesEveryResultType() {
        val results = MediaSearchResults(
            artists = listOf(Artist(ArtistId("artist"), "Artist")),
            albums = listOf(
                Album(
                    id = AlbumId("album"),
                    title = "Album",
                    artistName = "Artist",
                    coverArtId = null,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = null,
                ),
            ),
            tracks = listOf(track("one"), track("two")),
        )

        assertEquals(4, results.totalCount())
    }

    @Test
    fun allKnownTracksPrefersAlbumDetailTracksWhenPresent() {
        val searchTrack = track("search")
        val albumTrack = track("album")

        assertEquals(
            listOf(albumTrack),
            allKnownTracks(
                searchResults = MediaSearchResults(tracks = listOf(searchTrack)),
                albumDetail = AlbumDetails(
                    album = Album(
                        id = AlbumId("album"),
                        title = "Album",
                        artistName = "Artist",
                        coverArtId = null,
                        recentlyAddedAtIso8601 = null,
                        releaseYear = null,
                    ),
                    tracks = listOf(albumTrack),
                ),
            ),
        )
    }

    @Test
    fun allKnownTracksUsesSearchTracksWithoutAlbumDetail() {
        val searchTrack = track("search")

        assertEquals(
            listOf(searchTrack),
            allKnownTracks(
                searchResults = MediaSearchResults(tracks = listOf(searchTrack)),
                albumDetail = null,
            ),
        )
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
