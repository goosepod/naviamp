package app.naviamp.app

import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.radio.MaxRecentRadioStreams
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
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

    @Test
    fun `generated queues are stored as distinct source scoped sessions`() {
        var stored = emptyList<RecentRadioStream>()
        val controller = NaviampRecentRadioStreamController(
            load = { stored },
            save = { stored = it },
            currentSourceId = { "server-a" },
            nowEpochMillis = { 123L },
        )
        val tracks = listOf(
            Track(
                id = TrackId("track-1"),
                title = "One",
                artistName = "Artist",
                albumTitle = null,
                durationSeconds = null,
                coverArtId = null,
                audioInfo = null,
                replayGain = null,
            ),
        )

        controller.remember(libraryRecentRadioStream(), tracks)
        controller.remember(libraryRecentRadioStream(), tracks)

        assertEquals(2, stored.size)
        assertEquals(listOf("library:session:123:2", "library:session:123"), stored.map { it.id })
        assertTrue(stored.all { it.sourceId == "server-a" && it.startedAtEpochMillis == 123L })
        assertEquals(tracks, stored.first().sessionTracks.map { it.toTrack() })
    }

    @Test
    fun `current includes legacy entries but filters sessions from other sources`() {
        var stored = listOf(
            libraryRecentRadioStream(),
            libraryRecentRadioStream().copy(id = "a", sourceId = "server-a"),
            libraryRecentRadioStream().copy(id = "b", sourceId = "server-b"),
        )
        val controller = NaviampRecentRadioStreamController(
            load = { stored },
            save = { stored = it },
            currentSourceId = { "server-a" },
        )

        assertEquals(listOf("library", "a"), controller.current().map { it.id })
    }
}
