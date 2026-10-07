package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampGpuVisualizerMountTest {
    @Test fun changingEffectsReplacesTheRememberedNativeViewAndDisposesTheOldMount() = runTest {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val mounts = mutableListOf<NaviampGpuVisualizerRegion>()
        val disposals = mutableListOf<NaviampGpuVisualizerRegion>()
        val presenter = object : NaviampGpuVisualizerPresenter {
            override fun create(shader: NaviampGpuVisualizerShader) = Region()
            @Composable override fun Content(region: NaviampGpuVisualizerRegion) {
                // Like AndroidView.factory, this native-view factory only runs at mount.
                val nativeViewOwner = remember { region }
                DisposableEffect(Unit) {
                    mounts += nativeViewOwner
                    onDispose { disposals += nativeViewOwner }
                }
            }
        }
        val regions: List<NaviampGpuVisualizerRegion> = List(3) { Region() }
        val selected = mutableStateOf<NaviampGpuVisualizerRegion>(regions.first())
        try {
            composition.setContent { NaviampGpuVisualizerContent(presenter, selected.value) }
            runCurrent()
            for ((index, region) in regions.drop(1).withIndex()) {
                selected.value = region
                Snapshot.sendApplyNotifications()
                runCurrent(); clock.sendFrame((index + 1L) * 16_000_000); runCurrent()
                assertEquals(regions.take(index + 2), mounts)
                assertEquals(regions.take(index + 1), disposals)
            }
        } finally {
            composition.dispose(); recomposer.close(); runner.join()
        }
        assertEquals(regions, disposals)
    }

    private class Region : NaviampGpuVisualizerRegion {
        override fun place(bounds: Rect, clip: Rect, cornerRadius: Float) = Unit
        override fun setVisible(visible: Boolean) = Unit
        override fun submit(frame: NaviampGpuVisualizerFrame) = NaviampGpuSubmission.Accepted
        override fun close() = Unit
    }
}
