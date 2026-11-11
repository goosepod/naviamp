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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.radio.RecentRadioGenerationOptions
import kotlin.test.assertFailsWith
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException

class NaviampRecentRadioStreamControllerTest {
    @Test
    fun scopedCreationDoesNotReuseAnUnscopedLegacyUiIdOrIdentity() = runTest {
        val tracks = listOf(SavedTrack("one", "One", artistName = "Artist"))
        var stored = listOf(libraryRecentRadioStream().copy(sessionTracks = tracks))
        val owner = NaviampRecentRadioStreamController({ stored }, { stored = it }, currentSourceId = { "account-a" })
        val visible = owner.remember(libraryRecentRadioStream(), tracks.map { it.toTrack() })
        assertEquals(2, visible.size)
        assertEquals(2, visible.map { it.id }.toSet().size)
        assertEquals(setOf(null, "account-a"), visible.map { it.sourceId }.toSet())
    }

    @Test
    fun cancelledStartDoesNotPersistPhantomHistory() = runTest {
        var stored = emptyList<RecentRadioStream>()
        val owner = NaviampRecentRadioStreamController({ stored }, { stored = it })
        launch {
            coroutineContext.cancel()
            assertFailsWith<CancellationException> {
                owner.remember(libraryRecentRadioStream(), listOf(SavedTrack("one", "One", artistName = "Artist").toTrack()))
            }
        }.join()
        assertTrue(stored.isEmpty())
    }

    @Test
    fun concurrentEquivalentStartsReuseIdentityAndRestartRetainsIt() = runTest {
        var stored = emptyList<RecentRadioStream>()
        var now = 1L
        fun controller() = NaviampRecentRadioStreamController({ stored }, { stored = it },
            currentSourceId = { "account-a" }, nowEpochMillis = { now })
        val owner = controller()
        val tracks = listOf(SavedTrack("one", "One", artistName = "Artist").toTrack())
        (1..40).map { launch(Dispatchers.Default) {
            owner.remember(libraryRecentRadioStream(), tracks)
            owner.remember(genreRecentRadioStream(Genre("Jazz")), tracks)
        } }.joinAll()
        assertEquals(2, stored.size)
        val id = stored.first { it.kind == RecentRadioKind.Library }.id
        now = 2L
        controller().remember(libraryRecentRadioStream(), tracks)
        assertEquals(2, stored.size)
        assertEquals(id, stored.first().id)
        assertEquals(2L, stored.first().startedAtEpochMillis)
    }

    @Test
    fun oldDuplicatesReconcileOnceAndFailedOrEmptyWritesKeepHistory() = runTest {
        val queue = listOf(SavedTrack("one", "One", artistName = "Artist"))
        val first = libraryRecentRadioStream().copy(id = "library:session:2", startedAtEpochMillis = 2, sessionTracks = queue)
        var stored = listOf(first, first.copy(id = "library:session:1", startedAtEpochMillis = 1))
        var writes = 0
        var fail = false
        val owner = NaviampRecentRadioStreamController({ stored }, {
            if (fail) error("storage unavailable")
            writes++
            stored = it
        })
        assertEquals(listOf(first), owner.current())
        owner.current()
        assertEquals(1, writes)
        owner.remember(genreRecentRadioStream(Genre("Jazz")), emptyList())
        assertEquals(listOf(first), stored)
        fail = true
        assertFailsWith<IllegalStateException> { owner.remember(genreRecentRadioStream(Genre("Jazz")), queue.map { it.toTrack() }) }
        assertEquals(listOf(first), stored)
    }

    @Test
    fun differentOptionsReuseTheirOwnIdentityWithoutIdCollisions() = runTest {
        var stored = emptyList<RecentRadioStream>()
        val owner = NaviampRecentRadioStreamController({ stored }, { stored = it })
        val tracks = listOf(SavedTrack("one", "One", artistName = "Artist").toTrack())
        val a = libraryRecentRadioStream().copy(generationOptions = RecentRadioGenerationOptions())
        val b = a.copy(generationOptions = RecentRadioGenerationOptions(minimumTracks = 20))
        owner.remember(a, tracks)
        val firstId = stored.single().id
        owner.remember(b, tracks)
        assertEquals(2, stored.map { it.id }.toSet().size)
        owner.remember(a, tracks)
        assertEquals(2, stored.size)
        assertEquals(firstId, stored.first().id)
    }

    @Test
    fun currentTrimsPersistedStationsToTheFiftyMostRecentlyPlayed() = runTest {
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
    fun `remember deduplicates persists and publishes the updated order`() = runTest {
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
    fun `clear removes persisted recents and publishes the change`() = runTest {
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
    fun `generated queues reuse a source scoped station`() = runTest {
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

        assertEquals(1, stored.size)
        assertEquals(listOf("library"), stored.map { it.id })
        assertTrue(stored.all { it.sourceId == "server-a" && it.startedAtEpochMillis == 123L })
        assertEquals(tracks, stored.first().sessionTracks.map { it.toTrack() })
    }

    @Test
    fun `current includes legacy entries but filters sessions from other sources`() = runTest {
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
