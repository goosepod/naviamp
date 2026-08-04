package app.naviamp.app

import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampRecentRadioStreamControllerTest {
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
