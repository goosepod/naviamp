package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.radio.SeededRadioBuildResult
import app.naviamp.domain.radio.SeededRadioExpansionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NaviampSeededRadioExecutionControllerTest {
    @Test
    fun buildAndExpansionsRunInOneContinuationSession() = runTest {
        val continuation = NaviampRadioContinuationController()
        val controller = NaviampSeededRadioExecutionController(continuation)
        val seed = track("seed")
        val appended = mutableListOf<String>()
        val statuses = mutableListOf<String?>()
        val sessionId = controller.begin(seed.id)

        controller.execute(
            sessionId = sessionId,
            label = "Seed radio",
            loadInitial = { SeededRadioBuildResult.Ready(listOf(seed, track("one")), null) },
            loadExpansions = listOf(
                { SeededRadioExpansionResult.Ready(listOf(track("two"))) },
                { SeededRadioExpansionResult.Ready(listOf(track("three"))) },
            ),
            effects = NaviampSeededRadioExecutionEffects(
                appendFetchedTracks = { tracks -> appended += tracks.map { it.id.value } },
                queueSize = { 1 + appended.size },
                setStatus = statuses::add,
            ),
            completedStatus = "Playing Seed radio.",
        )

        assertEquals(listOf("one", "two", "three"), appended)
        assertEquals("Playing Seed radio.", statuses.last())
        assertTrue(continuation.state.active)
        assertFalse(continuation.state.refilling)
    }

    @Test
    fun staleRequestDoesNotApplyOrLoadExpansions() = runTest {
        val continuation = NaviampRadioContinuationController()
        val controller = NaviampSeededRadioExecutionController(continuation)
        val sessionId = controller.begin(TrackId("seed"))
        var expansionLoads = 0
        val appended = mutableListOf<Track>()

        controller.execute(
            sessionId = sessionId,
            label = "Seed radio",
            loadInitial = { SeededRadioBuildResult.Ready(listOf(track("seed"), track("one")), null) },
            loadExpansions = listOf({
                expansionLoads += 1
                SeededRadioExpansionResult.Ready(listOf(track("two")))
            }),
            effects = NaviampSeededRadioExecutionEffects(
                requestIsCurrent = { false },
                appendFetchedTracks = appended::addAll,
                queueSize = { appended.size },
            ),
        )

        assertTrue(appended.isEmpty())
        assertEquals(0, expansionLoads)
        assertFalse(continuation.state.refilling)
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = null,
            durationSeconds = null,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
