package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassPlaybackSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BassPlaybackPollingTest {
    @Test
    fun androidServicePolicyEmitsDuplicateProgressAndFinishesOnSourceEnd() {
        val progress = PlaybackProgress(positionSeconds = 9.5, durationSeconds = 10.0)
        val update = planBassPlaybackPollingUpdate(
            snapshot = snapshot(
                activeState = BassActiveState.Playing,
                sourceActiveState = BassActiveState.Stopped,
                progress = progress,
            ),
            previous = BassPlaybackPollingState(
                lastProgress = progress,
                lastActiveState = BassActiveState.Playing,
            ),
            policy = BassPlaybackPollingPolicy.AndroidService,
        )

        assertEquals(100L, BassPlaybackPollingPolicy.AndroidService.pollIntervalMillis)
        assertEquals(progress, update.progress)
        assertTrue(update.finished)
        assertFalse(update.shouldContinue)
        assertFalse(BassPlaybackPollingPolicy.AndroidService.finishWhenPollingStops)
    }

    @Test
    fun desktopPolicySkipsDuplicateProgressAndFinishesWhenPollingStops() {
        val progress = PlaybackProgress(positionSeconds = 3.0, durationSeconds = 10.0)
        val update = planBassPlaybackPollingUpdate(
            snapshot = snapshot(activeState = BassActiveState.Playing, progress = progress),
            previous = BassPlaybackPollingState(
                lastProgress = progress,
                lastActiveState = BassActiveState.Playing,
            ),
            policy = BassPlaybackPollingPolicy.DesktopEngine,
        )

        assertEquals(250L, BassPlaybackPollingPolicy.DesktopEngine.pollIntervalMillis)
        assertNull(update.progress)
        assertFalse(update.finished)
        assertTrue(update.shouldContinue)
        assertTrue(BassPlaybackPollingPolicy.DesktopEngine.finishWhenPollingStops)
    }

    @Test
    fun emitsChangedActiveStateProgressAndMetadata() {
        val metadata = PlaybackStreamMetadata(title = "Live title")
        val update = planBassPlaybackPollingUpdate(
            snapshot = snapshot(
                activeState = BassActiveState.Playing,
                progress = PlaybackProgress(positionSeconds = 3.0, durationSeconds = 10.0),
                metadata = metadata,
            ),
            previous = BassPlaybackPollingState(),
            emitDuplicateProgress = false,
            finishOnSourceEnd = false,
        )

        assertTrue(update.activeStateChanged)
        assertEquals(PlaybackState.Playing, update.playbackState)
        assertEquals(PlaybackProgress(positionSeconds = 3.0, durationSeconds = 10.0), update.progress)
        assertEquals(metadata, update.metadata)
        assertTrue(update.shouldContinue)
        assertFalse(update.finished)
    }

    @Test
    fun skipsDuplicateProgressUnlessRequested() {
        val progress = PlaybackProgress(positionSeconds = 3.0, durationSeconds = 10.0)
        val previous = BassPlaybackPollingState(
            lastProgress = progress,
            lastActiveState = BassActiveState.Playing,
        )

        val skipped = planBassPlaybackPollingUpdate(
            snapshot = snapshot(activeState = BassActiveState.Playing, progress = progress),
            previous = previous,
            emitDuplicateProgress = false,
            finishOnSourceEnd = false,
        )
        val emitted = planBassPlaybackPollingUpdate(
            snapshot = snapshot(activeState = BassActiveState.Playing, progress = progress),
            previous = previous,
            emitDuplicateProgress = true,
            finishOnSourceEnd = false,
        )

        assertNull(skipped.progress)
        assertEquals(progress, emitted.progress)
    }

    @Test
    fun finishesOnSourceEndOnlyWhenEnabled() {
        val snapshot = snapshot(
            activeState = BassActiveState.Playing,
            sourceActiveState = BassActiveState.Stopped,
            progress = PlaybackProgress(positionSeconds = 9.5, durationSeconds = 10.0),
        )

        val ignored = planBassPlaybackPollingUpdate(
            snapshot = snapshot,
            previous = BassPlaybackPollingState(lastActiveState = BassActiveState.Playing),
            emitDuplicateProgress = false,
            finishOnSourceEnd = false,
        )
        val finished = planBassPlaybackPollingUpdate(
            snapshot = snapshot,
            previous = BassPlaybackPollingState(lastActiveState = BassActiveState.Playing),
            emitDuplicateProgress = false,
            finishOnSourceEnd = true,
        )

        assertFalse(ignored.finished)
        assertTrue(ignored.shouldContinue)
        assertTrue(finished.finished)
        assertEquals(PlaybackState.Finished, finished.playbackState)
        assertFalse(finished.shouldContinue)
    }

    @Test
    fun stopsPollingWhenPlaybackOutputStops() {
        val update = planBassPlaybackPollingUpdate(
            snapshot = snapshot(activeState = BassActiveState.Stopped),
            previous = BassPlaybackPollingState(lastActiveState = BassActiveState.Playing),
            emitDuplicateProgress = false,
            finishOnSourceEnd = false,
        )

        assertFalse(update.shouldContinue)
        assertNull(update.playbackState)
    }

    private fun snapshot(
        activeState: Int,
        sourceActiveState: Int? = null,
        progress: PlaybackProgress = PlaybackProgress.Unknown,
        metadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
    ): BassPlaybackSnapshot =
        BassPlaybackSnapshot(
            activeState = activeState,
            sourceActiveState = sourceActiveState,
            progress = progress,
            metadata = metadata,
        )
}
