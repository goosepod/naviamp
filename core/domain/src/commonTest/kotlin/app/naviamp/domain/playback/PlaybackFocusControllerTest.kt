package app.naviamp.domain.playback

import kotlin.test.*

class PlaybackFocusControllerTest {
    @Test fun repeatedTransientLossStillResumesOnce() {
        val f = Fixture()
        f.start()
        f.change(PlaybackFocusChange.TransientLoss)
        f.change(PlaybackFocusChange.TransientLoss)
        assertEquals(1, f.pauses)
        assertFalse(f.wake.isHeld)
        f.change(PlaybackFocusChange.Gain)
        f.change(PlaybackFocusChange.Gain)
        assertEquals(1, f.resumes)
        assertTrue(f.wake.isHeld)
    }

    @Test fun explicitPauseAndTerminalStatesCancelAutomaticResume() {
        for (terminal in listOf(null, PlaybackState.Stopped, PlaybackState.Finished, PlaybackState.Error("offline"))) {
            val f = Fixture()
            f.start()
            f.change(PlaybackFocusChange.TransientLoss)
            if (terminal == null) f.controller.userPausedOrStopped() else f.controller.onPlaybackState(terminal)
            f.change(PlaybackFocusChange.Gain)
            assertEquals(0, f.resumes)
            assertFalse(f.wake.isHeld)
        }
    }

    @Test fun permanentLossDoesNotResumeAndDuckingRestoresGain() {
        val f = Fixture()
        f.start()
        f.change(PlaybackFocusChange.Duck)
        f.change(PlaybackFocusChange.Gain)
        assertEquals(listOf(0.25f, 1f), f.factors)
        assertEquals(0, f.pauses)
        f.change(PlaybackFocusChange.Duck)
        f.change(PlaybackFocusChange.Loss)
        f.change(PlaybackFocusChange.Gain)
        assertEquals(1, f.pauses)
        assertEquals(0, f.resumes)
        assertEquals(listOf(0.25f, 1f, 0.25f, 1f), f.factors)
        assertTrue(f.focus.abandoned > 0)
    }

    @Test fun deniedFocusAndAlreadyPausedPlaybackNeverAutoResume() {
        val f = Fixture()
        f.focus.granted = false
        assertFalse(f.controller.requestPlayback())
        assertFalse(f.wake.isHeld)
        f.controller.onPlaybackState(PlaybackState.Paused)
        f.change(PlaybackFocusChange.TransientLoss)
        f.change(PlaybackFocusChange.Gain)
        assertEquals(0, f.resumes)
    }

    @Test fun renewsFiniteLeaseWhilePlaying() {
        val f = Fixture()
        f.start()
        f.wake.now = 299_999
        f.controller.onProgress()
        assertEquals(1, f.wake.acquisitions)
        f.wake.now = 300_000
        f.controller.onProgress()
        assertEquals(2, f.wake.acquisitions)
        assertEquals(900_000L, f.wake.timeout)
        f.controller.userPausedOrStopped()
        f.wake.now = 900_000
        f.controller.onProgress()
        assertEquals(2, f.wake.acquisitions)
    }

    @Test fun progressReacquiresExpiredLeaseWithoutAnotherPlayingState() {
        val f = Fixture()
        f.start()
        // Model native timeout after progress delivery was suspended past the lease deadline.
        f.wake.now = 900_001
        f.wake.isHeld = false
        f.controller.onProgress()
        assertTrue(f.wake.isHeld)
        assertEquals(2, f.wake.acquisitions)
        f.wake.now += 299_999
        f.controller.onProgress()
        assertEquals(2, f.wake.acquisitions)
        f.wake.now += 1
        f.controller.onProgress()
        assertEquals(3, f.wake.acquisitions)
    }

    @Test fun progressRetriesAnUnsuccessfulNativeAcquisition() {
        val f = Fixture()
        f.wake.granted = false
        f.start()
        assertFalse(f.wake.isHeld)
        f.wake.granted = true
        f.controller.onProgress()
        assertTrue(f.wake.isHeld)
        assertEquals(2, f.wake.acquisitions)
    }

    @Test fun lateProgressCannotReacquireAfterExplicitPauseOrStop() {
        val f = Fixture()
        f.start()
        // The user action precedes the asynchronous engine state publication.
        f.controller.userPausedOrStopped()
        f.controller.onProgress()
        assertFalse(f.wake.isHeld)
        assertEquals(1, f.wake.acquisitions)
        f.change(PlaybackFocusChange.TransientLoss)
        f.change(PlaybackFocusChange.Gain)
        assertEquals(0, f.resumes)
    }

    @Test fun nonPlayingProgressDoesNotAcquireOrRenewLease() {
        for (state in listOf(PlaybackState.Idle, PlaybackState.Loading, PlaybackState.Paused,
            PlaybackState.Stopped, PlaybackState.Finished, PlaybackState.Error("offline"))) {
            val f = Fixture()
            f.start()
            f.controller.onPlaybackState(state)
            f.wake.now = 300_000
            f.controller.onProgress()
            assertEquals(1, f.wake.acquisitions, "$state must not renew")
            f.wake.isHeld = false
            f.controller.onProgress()
            assertFalse(f.wake.isHeld, "$state must not reacquire")
        }
    }

    private class Fixture {
        val focus = Focus()
        val wake = Wake()
        var pauses = 0
        var resumes = 0
        val factors = mutableListOf<Float>()
        val controller: PlaybackFocusController = PlaybackFocusController(focus, wake,
            pause = { paused() }, resume = { resumed() }, outputVolumeFactor = factors::add)
        private fun paused() { pauses++; controller.onPlaybackState(PlaybackState.Paused) }
        private fun resumed() { resumes++; controller.onPlaybackState(PlaybackState.Playing) }
        fun start() { assertTrue(controller.requestPlayback()); controller.onPlaybackState(PlaybackState.Playing) }
        fun change(change: PlaybackFocusChange) = focus.listener(change)
    }
    private class Focus : PlaybackFocusEffect {
        var granted = true
        var abandoned = 0
        lateinit var listener: (PlaybackFocusChange) -> Unit
        override fun request(onChange: (PlaybackFocusChange) -> Unit): Boolean { listener = onChange; return granted }
        override fun abandon() { abandoned++ }
    }
    private class Wake : PlaybackWakeLockEffect {
        override var isHeld = false
        var granted = true
        var now = 0L
        var timeout = 0L
        var acquisitions = 0
        override fun nowMillis() = now
        override fun acquire(timeoutMillis: Long) { isHeld = granted; timeout = timeoutMillis; acquisitions++ }
        override fun release() { isHeld = false }
    }
}
