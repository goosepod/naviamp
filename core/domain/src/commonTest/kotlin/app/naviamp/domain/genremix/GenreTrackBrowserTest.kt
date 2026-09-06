package app.naviamp.domain.genremix

import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaPage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GenreTrackBrowserTest {
    @Test fun overlappingSourceTagsPageWithoutDuplicateTracks() = runTest {
        val calls = mutableListOf<String>()
        val browser = GenreTrackBrowser(listOf(Genre("R&B"), Genre("Soul"), Genre("r&b"))) { name, request ->
            calls += "$name:${request.offset}:${request.continuationToken}"
            val ids = if (name == "R&B") {
                if (request.offset == 0) listOf("a", "b") else listOf("c")
            } else listOf("b", "d")
            MediaPage(ids.map(::track), request.offset, request.limit,
                hasMore = name == "R&B" && request.offset == 0,
                nextContinuationToken = "folder:2")
        }
        repeat(3) { browser.loadNext() }
        assertEquals(listOf("a", "b", "c", "d"), browser.tracks.map { it.id.value })
        assertEquals(listOf("R&B:0:null", "R&B:2:folder:2", "Soul:0:null"), calls)
        assertFalse(browser.hasMore)
        browser.loadNext()
        assertEquals(3, calls.size)
    }

    @Test fun failedPageCanBeRetriedWithoutSkippingOrLosingTracks() = runTest {
        var fail = true
        val offsets = mutableListOf<Int>()
        val browser = GenreTrackBrowser(listOf(Genre("Ambient"))) { _, request ->
            offsets += request.offset
            if (fail) error("offline")
            MediaPage(listOf(track("one")), request.offset, request.limit, hasMore = false)
        }
        assertFailsWith<IllegalStateException> { browser.loadNext() }
        assertTrue(browser.hasMore)
        assertTrue(browser.tracks.isEmpty())
        fail = false
        browser.loadNext()
        assertEquals(listOf(0, 0), offsets)
        assertEquals(listOf("one"), browser.tracks.map { it.id.value })
    }

    private fun track(id: String) = Track(id = TrackId(id), title = id, artistName = "Artist",
        albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
}
