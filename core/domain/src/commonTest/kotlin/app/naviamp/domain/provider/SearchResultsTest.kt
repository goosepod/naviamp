package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.search.offlineTrackSearchResults
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
    fun searchResultsStatusReportsEmptyMatchesAndErrors() {
        assertEquals("No matches found.", searchResultsStatus(MediaSearchResults()))
        assertEquals(
            "Found 1 matches.",
            searchResultsStatus(MediaSearchResults(tracks = listOf(track("one")))),
        )
        assertEquals("Search failed.", searchErrorStatus(IllegalStateException()))
    }

    @Test
    fun searchResultsUpdateWrapsSuccessAndFailure() = kotlinx.coroutines.test.runTest {
        val track = track("one")

        assertEquals(
            SearchResultsUpdate(
                results = MediaSearchResults(tracks = listOf(track)),
                status = "Found 1 matches.",
            ),
            searchResultsUpdate("query") { _, _ -> MediaSearchResults(tracks = listOf(track)) },
        )
        assertEquals(
            SearchResultsUpdate(
                results = MediaSearchResults(),
                status = "Boom",
            ),
            searchResultsUpdate("query") { _, _ -> error("Boom") },
        )
    }

    @Test
    fun searchSessionControllerHandlesBlankAndDisconnectedQueries() = kotlinx.coroutines.test.runTest {
        var results = MediaSearchResults(tracks = listOf(track("old")))
        var status: String? = "old"
        var searching = true
        val controller = SearchSessionController<Any>(
            provider = { null },
            setResults = { results = it },
            setStatus = { status = it },
            setSearching = { searching = it },
        ) { _, _, _ -> error("Search should not run") }

        controller.load("  ")

        assertEquals(MediaSearchResults(), results)
        assertEquals(SearchDisconnectedStatus, status)
        assertEquals(false, searching)
    }

    @Test
    fun searchSessionControllerAppliesSearchResults() = kotlinx.coroutines.test.runTest {
        val provider = Any()
        val track = track("one")
        var results = MediaSearchResults()
        var status: String? = null
        var searching = false
        val controller = SearchSessionController(
            provider = { provider },
            setResults = { results = it },
            setStatus = { status = it },
            setSearching = { searching = it },
        ) { _, query, limit ->
            assertEquals("query", query)
            assertEquals(SearchResultLimit, limit)
            MediaSearchResults(tracks = listOf(track))
        }

        controller.load(" query ")

        assertEquals(MediaSearchResults(tracks = listOf(track)), results)
        assertEquals("Found 1 matches.", status)
        assertEquals(false, searching)
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

    @Test
    fun offlineTrackSearchMatchesDownloadedTrackMetadata() {
        val titleMatch = track("title", title = "The Big Song")
        val artistMatch = track("artist", artistName = "The Selecter")
        val albumMatch = track("album", albumTitle = "Night Drive")
        val missed = track("missed", title = "Other")

        assertEquals(
            listOf(titleMatch),
            offlineTrackSearchResults(listOf(titleMatch, missed), "big").tracks,
        )
        assertEquals(
            listOf(artistMatch),
            offlineTrackSearchResults(listOf(artistMatch, missed), "selecter").tracks,
        )
        assertEquals(
            listOf(albumMatch),
            offlineTrackSearchResults(listOf(albumMatch, missed), "night").tracks,
        )
    }

    private fun track(
        id: String,
        title: String = "Track $id",
        artistName: String = "Artist",
        albumTitle: String = "Album",
    ): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = artistName,
            albumTitle = albumTitle,
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
