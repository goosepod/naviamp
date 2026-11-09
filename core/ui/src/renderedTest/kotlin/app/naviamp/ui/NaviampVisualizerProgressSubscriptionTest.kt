package app.naviamp.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import app.naviamp.domain.playback.PlaybackProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
// Composition uses the real platform Trace implementation; run on JVM and Android devices.
class NaviampVisualizerProgressSubscriptionTest {
    @Test fun nonLyricEffectsDoNotSubscribeAndSwitchingAwayReleasesThePlaybackClock() = runTest {
        var frameNanos = 0L
        lateinit var frameClock: BroadcastFrameClock
        frameClock = BroadcastFrameClock {
            // Deliver a new monotonic frame only after the recomposer requests it.
            launch { frameNanos += 16_000_000L; frameClock.sendFrame(frameNanos) }
        }
        val recomposer = Recomposer(coroutineContext + frameClock)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val progress = MutableStateFlow(PlaybackProgress(1.0, 600.0))
        val effect = mutableStateOf(NaviampVisualizer.AudioSphere)
        val nowPlaying = NowPlayingUi(title = "Fixture", subtitle = "Fixture", stateLabel = "Playing")
        var compositions = 0
        val applied = Channel<NaviampVisualizer>(Channel.UNLIMITED)
        suspend fun drainChanges() {
            runCurrent() // A StateFlow collector may first need to write Compose state.
            Snapshot.sendApplyNotifications()
            runCurrent()
            recomposer.awaitIdle()
            runCurrent()
        }
        suspend fun awaitApplied(visualizer: NaviampVisualizer) {
            drainChanges()
            // An unrelated global snapshot notifier may still be delivering our effect change.
            // Never charge that pending effect recomposition to the next progress mutation.
            while (applied.receive() != visualizer) Unit
            recomposer.awaitIdle()
            runCurrent()
        }
        try {
            composition.setContent {
                val selected = effect.value
                currentVisualizerLyricStage(selected, nowPlaying, progress)
                compositions++
                SideEffect { applied.trySend(selected) }
            }
            awaitApplied(NaviampVisualizer.AudioSphere)
            for (visualizer in NaviampVisualizer.entries.filter { it != NaviampVisualizer.LyricMirrorTunnel }) {
                effect.value = visualizer
                awaitApplied(visualizer)
                assertEquals(0, progress.subscriptionCount.value, visualizer.name)
                val before = compositions
                progress.value = PlaybackProgress(progress.value.positionSeconds!! + 1, 600.0)
                drainChanges()
                assertEquals(before, compositions, "${visualizer.name} must not recompose for progress")
            }
            effect.value = NaviampVisualizer.LyricMirrorTunnel
            awaitApplied(NaviampVisualizer.LyricMirrorTunnel)
            assertEquals(1, progress.subscriptionCount.value)
            val lyricBefore = compositions
            progress.value = PlaybackProgress(progress.value.positionSeconds!! + 1, 600.0)
            awaitApplied(NaviampVisualizer.LyricMirrorTunnel)
            assertTrue(compositions > lyricBefore, "The lyric effect must react to the playback clock")
            effect.value = NaviampVisualizer.OceanOfInk
            awaitApplied(NaviampVisualizer.OceanOfInk)
            assertEquals(0, progress.subscriptionCount.value)
            val released = compositions
            progress.value = PlaybackProgress(progress.value.positionSeconds!! + 1, 600.0)
            drainChanges()
            assertEquals(released, compositions, "Switching away must release progress recomposition")
        } finally {
            composition.dispose(); recomposer.close(); runner.join()
            applied.close()
        }
    }
}
