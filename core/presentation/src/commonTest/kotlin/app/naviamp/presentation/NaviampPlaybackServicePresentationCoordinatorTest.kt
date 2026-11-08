package app.naviamp.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampPlaybackServicePresentationCoordinatorTest {
    private val track = NaviampExternalMediaItem("track", "Track", "Artist")
    private val playing = NaviampExternalPlaybackSnapshot(NaviampExternalPlaybackState.Playing, track)

    @Test
    fun acceptedStartIsAcknowledgedAfterLoadingFailureStopOrMissingMetadata() {
        for (snapshot in listOf(
            playing.copy(state = NaviampExternalPlaybackState.Loading),
            playing.copy(state = NaviampExternalPlaybackState.Paused),
            playing.copy(state = NaviampExternalPlaybackState.Idle),
            NaviampExternalPlaybackSnapshot(),
        )) {
            val events = mutableListOf<String>()
            val coordinator = coordinator(events)
            coordinator.startRequested(snapshot)
            coordinator.publish(snapshot, notificationChanged = true)
            coordinator.release()
            assertEquals(
                if (snapshot.shouldRetainPlaybackService) listOf("start", "update", "remove")
                else listOf("start", "remove"),
                events,
            )
        }
    }

    @Test
    fun ordinaryIdlePublicationDoesNotCreateOrReleaseTheSurface() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events)
        coordinator.publish(NaviampExternalPlaybackSnapshot(), notificationChanged = true)
        coordinator.publish(playing, notificationChanged = true)
        coordinator.publish(playing.copy(positionMillis = 500L), notificationChanged = false)
        coordinator.publish(playing.copy(state = NaviampExternalPlaybackState.Idle), notificationChanged = true)
        assertEquals(listOf("start"), events)
        coordinator.release()
        coordinator.release()
        assertEquals(listOf("start", "remove"), events)
    }

    @Test
    fun everyAcceptedStartIsAcknowledgedIncludingAfterRelease() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events)
        repeat(2) { coordinator.startRequested(playing) }
        coordinator.release()
        coordinator.startRequested(NaviampExternalPlaybackSnapshot())
        assertEquals(listOf("start", "start", "remove", "start"), events)
    }

    @Test
    fun failedNativeEstablishmentDoesNotMarkTheSurfaceEstablished() {
        var attempts = 0
        val events = mutableListOf<String>()
        val coordinator = NaviampPlaybackServicePresentationCoordinator(
            establish = { if (++attempts == 1) error("native failure") else events.add("start") },
            update = { events.add("update") },
            remove = { events.add("remove") },
        )
        assertFailsWith<IllegalStateException> { coordinator.startRequested(playing) }
        coordinator.release()
        coordinator.publish(playing, notificationChanged = false)
        assertEquals(2, attempts)
        assertEquals(listOf("start"), events)
    }

    @Test
    fun delayedStartAfterFailureStillAcknowledgesAndReleasesAfterStableIdle() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events)
        val snapshots = MutableSharedFlow<NaviampExternalPlaybackSnapshot>()
        var pendingStart = false
        backgroundScope.launch {
            snapshots.playbackServiceRetentionDecisions().collect {
                if (it) pendingStart = true else coordinator.release()
            }
        }
        runCurrent()
        snapshots.emit(playing)
        runCurrent()
        assertEquals(true, pendingStart)
        snapshots.emit(NaviampExternalPlaybackSnapshot())
        runCurrent()
        coordinator.startRequested(NaviampExternalPlaybackSnapshot())
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(listOf("start"), events)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf("start", "remove"), events)
    }

    private fun coordinator(events: MutableList<String>) = NaviampPlaybackServicePresentationCoordinator(
        establish = { events.add("start") },
        update = { events.add("update") },
        remove = { events.add("remove") },
    )
}
