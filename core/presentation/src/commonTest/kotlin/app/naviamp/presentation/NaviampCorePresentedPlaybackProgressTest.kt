package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.ui.NowPlayingUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCorePresentedPlaybackProgressTest {
    private fun remote(positionSeconds: Double, playing: Boolean, durationSeconds: Double? = 180.0, id: String = "track") =
        NowPlayingUi(
            id = id,
            title = "Remote",
            subtitle = "Artist",
            stateLabel = if (playing) "Playing on TV" else "Paused on TV",
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            isPlaying = playing,
            isPaused = !playing,
        )

    private class Harness(
        val local: MutableStateFlow<PlaybackProgress>,
        val remote: MutableStateFlow<NowPlayingUi?>,
        val presented: kotlinx.coroutines.flow.StateFlow<PlaybackProgress>,
    )

    private fun TestScope.harness(local: PlaybackProgress = PlaybackProgress(3.0, 200.0)): Harness {
        val localFlow = MutableStateFlow(local)
        val remoteFlow = MutableStateFlow<NowPlayingUi?>(null)
        val presented = naviampPresentedPlaybackProgress(
            scope = backgroundScope,
            local = localFlow,
            remote = remoteFlow.remoteProgressReports(),
            timeSource = testScheduler.timeSource,
        )
        backgroundScope.launch { presented.collect {} }
        runCurrent()
        return Harness(localFlow, remoteFlow, presented)
    }

    @Test
    fun playingRemoteTargetAdvancesFromItsReportedPosition() = runTest {
        val h = harness()
        h.remote.value = remote(positionSeconds = 42.0, playing = true)
        runCurrent()
        assertEquals(PlaybackProgress(42.0, 180.0), h.presented.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(43.0, h.presented.value.positionSeconds)
        advanceTimeBy(9_000); runCurrent()
        assertEquals(52.0, h.presented.value.positionSeconds)
        assertEquals(180.0, h.presented.value.durationSeconds)
    }

    @Test
    fun aScreenOpenedAfterTheReportExtrapolatesFromReceiptNotFromSubscription() = runTest {
        val localFlow = MutableStateFlow(PlaybackProgress(3.0, 200.0))
        val remoteFlow = MutableStateFlow<NowPlayingUi?>(null)
        val presented = naviampPresentedPlaybackProgress(
            scope = backgroundScope,
            local = localFlow,
            remote = remoteFlow.remoteProgressReports(),
            timeSource = testScheduler.timeSource,
        )
        runCurrent()
        // The target reports while no Now Playing screen is collecting progress.
        remoteFlow.value = remote(positionSeconds = 0.0, playing = true)
        runCurrent()
        advanceTimeBy(23_000); runCurrent()

        val published = mutableListOf<Double?>()
        backgroundScope.launch { presented.collect { published += it.positionSeconds } }
        runCurrent()

        assertEquals(23.0, published.last())
    }

    @Test
    fun playingRemoteTargetPublishesOnlyWhenTheDisplayedSecondChanges() = runTest {
        val h = harness()
        val published = mutableListOf<Double?>()
        backgroundScope.launch { h.presented.collect { published += it.positionSeconds } }
        h.remote.value = remote(positionSeconds = 10.4, playing = true)
        runCurrent()
        advanceTimeBy(3_000); runCurrent()
        // 10.4 on receipt, then each whole-second boundary rather than a high-frequency poll.
        assertEquals(listOf<Double?>(3.0, 10.4, 11.0, 12.0, 13.0), published)
    }

    @Test
    fun pausedRemoteTargetStaysAtItsReportedPosition() = runTest {
        val h = harness()
        h.remote.value = remote(positionSeconds = 42.0, playing = false)
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(PlaybackProgress(42.0, 180.0), h.presented.value)
    }

    @Test
    fun newRemoteReportReanchorsAndPauseFreezesTheTimeline() = runTest {
        val h = harness()
        h.remote.value = remote(positionSeconds = 0.0, playing = true)
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(5.0, h.presented.value.positionSeconds)
        // A seek or later snapshot reports a fresh position.
        h.remote.value = remote(positionSeconds = 90.0, playing = true)
        runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(92.0, h.presented.value.positionSeconds)
        h.remote.value = remote(positionSeconds = 92.5, playing = false)
        runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(92.5, h.presented.value.positionSeconds)
    }

    @Test
    fun unrelatedRemoteChangesDoNotRewindTheExtrapolatedPosition() = runTest {
        val h = harness()
        h.remote.value = remote(positionSeconds = 20.0, playing = true)
        runCurrent()
        advanceTimeBy(4_000); runCurrent()
        h.remote.value = h.remote.value!!.copy(favoriteActive = true, title = "Renamed")
        runCurrent()
        assertEquals(24.0, h.presented.value.positionSeconds)
    }

    @Test
    fun remoteTimelineStopsAtTheReportedDuration() = runTest {
        val h = harness()
        h.remote.value = remote(positionSeconds = 178.0, playing = true, durationSeconds = 180.0)
        runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(PlaybackProgress(180.0, 180.0), h.presented.value)
    }

    @Test
    fun detachingFromTheRemoteTargetReturnsToLocalProgress() = runTest {
        val h = harness(local = PlaybackProgress(7.0, 120.0))
        assertEquals(PlaybackProgress(7.0, 120.0), h.presented.value)
        h.remote.value = remote(positionSeconds = 42.0, playing = true)
        runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(44.0, h.presented.value.positionSeconds)
        h.remote.value = null
        runCurrent()
        assertEquals(PlaybackProgress(7.0, 120.0), h.presented.value)
        h.local.value = PlaybackProgress(8.0, 120.0)
        runCurrent()
        assertEquals(PlaybackProgress(8.0, 120.0), h.presented.value)
        // The remote ticker is gone: virtual time no longer changes presented progress.
        advanceTimeBy(5_000); runCurrent()
        assertEquals(PlaybackProgress(8.0, 120.0), h.presented.value)
    }

    @Test
    fun corePresentsTheAttachedRemoteTargetPositionAndReturnsToLocalOnDetach() = runTest {
        val core = NaviampCore.create(backgroundScope, fakeCoreServices())
        backgroundScope.launch { core.presentedPlaybackProgress.collect {} }
        runCurrent()
        val local = core.playbackProgress.value
        assertEquals(local, core.presentedPlaybackProgress.value)
        core.stateStore.updateShell { shell ->
            shell.copy(connect = shell.connect.copy(remoteNowPlaying = remote(positionSeconds = 61.0, playing = false)))
        }
        runCurrent()
        assertEquals(PlaybackProgress(61.0, 180.0), core.presentedPlaybackProgress.value)
        assertEquals(local, core.playbackProgress.value, "Local engine progress is unchanged")
        core.stateStore.updateShell { shell -> shell.copy(connect = shell.connect.copy(remoteNowPlaying = null)) }
        runCurrent()
        assertEquals(local, core.presentedPlaybackProgress.value)
        core.shutdown()
    }

    @Test
    fun localProgressIsPresentedWhileNoRemoteTargetIsAttached() = runTest {
        val h = harness(local = PlaybackProgress(1.0, 60.0))
        h.local.value = PlaybackProgress(2.0, 60.0)
        runCurrent()
        assertEquals(PlaybackProgress(2.0, 60.0), h.presented.value)
    }
}
