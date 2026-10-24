package app.naviamp.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import app.naviamp.domain.playback.PlaybackProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVisualizerProgressSubscriptionTest {
    @Test fun nonLyricEffectsDoNotSubscribeAndSwitchingAwayReleasesThePlaybackClock() = runTest {
        val frameClock = BroadcastFrameClock()
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
        try {
            composition.setContent {
                currentVisualizerLyricStage(effect.value, nowPlaying, progress)
                compositions++
            }
            runCurrent()
            for (visualizer in NaviampVisualizer.entries.filter { it != NaviampVisualizer.LyricMirrorTunnel }) {
                effect.value = visualizer
                Snapshot.sendApplyNotifications()
                runCurrent(); frameClock.sendFrame(16_000_000L); runCurrent()
                assertEquals(0, progress.subscriptionCount.value, visualizer.name)
                val before = compositions
                progress.value = PlaybackProgress(progress.value.positionSeconds!! + 1, 600.0)
                Snapshot.sendApplyNotifications()
                runCurrent(); frameClock.sendFrame(32_000_000L); runCurrent()
                assertEquals(before, compositions, "${visualizer.name} must not recompose for progress")
            }
            effect.value = NaviampVisualizer.LyricMirrorTunnel
            Snapshot.sendApplyNotifications()
            runCurrent(); frameClock.sendFrame(48_000_000L); runCurrent()
            assertEquals(1, progress.subscriptionCount.value)
            effect.value = NaviampVisualizer.OceanOfInk
            Snapshot.sendApplyNotifications()
            runCurrent(); frameClock.sendFrame(64_000_000L); runCurrent()
            assertEquals(0, progress.subscriptionCount.value)
        } finally {
            composition.dispose(); recomposer.close(); runner.join()
        }
    }
}
