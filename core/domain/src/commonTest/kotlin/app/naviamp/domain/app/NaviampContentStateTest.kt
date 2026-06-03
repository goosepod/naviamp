package app.naviamp.domain.app

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.provider.MediaSearchResults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampContentStateTest {
    @Test
    fun clearDetailsPreservesSearchState() {
        val artist = Artist(ArtistId("artist-1"), "New Order")
        val searchResults = MediaSearchResults(artists = listOf(artist))

        val cleared = NaviampContentState(
            searchQuery = "new order",
            searchResults = searchResults,
            artistDetail = ArtistDetails(artist = artist, albums = emptyList()),
        ).clearDetails()

        assertEquals("new order", cleared.searchQuery)
        assertEquals(searchResults, cleared.searchResults)
        assertNull(cleared.artistDetail)
    }
}
