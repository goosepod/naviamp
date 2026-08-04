package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BassPlaybackExecutionCoordinatorTest {
    @Test
    fun attachedCallbacksReceiveExecutionUpdates() {
        val coordinator = BassPlaybackExecutionCoordinator()
        val states = mutableListOf<PlaybackState>()
        val progress = mutableListOf<PlaybackProgress>()
        coordinator.attach(
            request = request("one"),
            onStateChanged = states::add,
            onProgressChanged = progress::add,
            onMetadataChanged = {},
        )

        coordinator.publishState(PlaybackState.Playing)
        coordinator.publishProgress(PlaybackProgress(positionSeconds = 2.0, durationSeconds = 10.0))

        assertEquals(1, states.size)
        assertTrue(states.single() == PlaybackState.Playing)
        assertEquals(2.0, progress.single().positionSeconds ?: -1.0)
        assertEquals("one", coordinator.currentRequest?.mediaId)
    }

    @Test
    fun newGenerationInvalidatesOlderExecution() {
        val coordinator = BassPlaybackExecutionCoordinator()
        val first = coordinator.nextPlaybackId()
        val second = coordinator.nextPlaybackId()

        assertFalse(coordinator.isCurrent(first))
        assertTrue(coordinator.isCurrent(second))
    }

    @Test
    fun replacementInvalidatesOutgoingExecutionBeforeItsCancellationCleanupRuns() {
        val coordinator = BassPlaybackExecutionCoordinator()
        val outgoing = coordinator.nextPlaybackId()
        var outgoingWasCurrentDuringCancellation = true

        val replacement = coordinator.replaceCurrentExecution {
            outgoingWasCurrentDuringCancellation = coordinator.isCurrent(outgoing)
        }

        assertFalse(outgoingWasCurrentDuringCancellation)
        assertTrue(coordinator.isCurrent(replacement))
    }

    @Test
    fun clearInvalidatesExecutionAndReleasesRequestAndCallbacks() {
        val coordinator = BassPlaybackExecutionCoordinator()
        coordinator.attach(request("one"), {}, {}, {})
        val playbackId = coordinator.nextPlaybackId()

        coordinator.clear()

        assertFalse(coordinator.isCurrent(playbackId))
        assertNull(coordinator.currentRequest)
        assertNull(coordinator.callbacks)
    }

    private fun request(id: String) = PlaybackRequest(
        url = "https://example.test/$id",
        mediaId = id,
    )
}
