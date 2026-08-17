package app.naviamp.app

import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.radio.MaxRecentRadioStreams
import app.naviamp.domain.Genre
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampRecentRadioStreamControllerTest {
    @Test
    fun currentTrimsPersistedStationsToTheFiftyMostRecentlyPlayed() {
        var stored = (1..55).map { genreRecentRadioStream(Genre("Genre $it")) }
        val controller = NaviampRecentRadioStreamController(
            load = { stored },
            save = { stored = it },
        )

        val current = controller.current()

        assertEquals(MaxRecentRadioStreams, current.size)
        assertEquals("genre:Genre 1", current.first().id)
        assertEquals("genre:Genre 50", current.last().id)
        assertEquals(current, stored)
    }

    @Test
    fun `remember deduplicates persists and publishes the updated order`() {
        var stored = listOf(
            RecentRadioStream("genre:Jazz", "Jazz radio", RecentRadioKind.Genre, genre = "Jazz"),
            libraryRecentRadioStream(),
        )
        var changeCount = 0
        val controller = NaviampRecentRadioStreamController(
            load = { stored },
            save = { streams -> stored = streams },
            onChanged = { changeCount++ },
        )

        val updated = controller.remember(libraryRecentRadioStream())

        assertEquals(listOf("library", "genre:Jazz"), updated.map { it.id })
        assertEquals(updated, stored)
        assertEquals(1, changeCount)
    }

    @Test
    fun `clear removes persisted recents and publishes the change`() {
        var stored = listOf(libraryRecentRadioStream())
        var changeCount = 0
        val controller = NaviampRecentRadioStreamController(
            load = { stored },
            save = { streams -> stored = streams },
            onChanged = { changeCount++ },
        )

        controller.clear()

        assertTrue(stored.isEmpty())
        assertEquals(1, changeCount)
    }
}
