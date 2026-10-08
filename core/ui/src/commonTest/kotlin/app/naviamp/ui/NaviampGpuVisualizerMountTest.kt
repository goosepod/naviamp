package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
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
        val demands = mutableListOf<Pair<Any, Boolean>>()
        val demand: (Any, Boolean) -> Unit = { owner, enabled -> demands += owner to enabled }
        val visible = MutableStateFlow(true)
        val value = mutableIntStateOf(1)
        val rendered = mutableListOf<Int>()
        val raster = NaviampRasterContent({ value.intValue }, { rendered += it; emptyList() })
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
            composition.setContent {
                CompositionLocalProvider(LocalNaviampWindowVisibility provides visible) {
                    NaviampVisualizerFrameDemand(true, demand)
                    NaviampObserveRasterContent(raster, true) {}
                    NaviampGpuVisualizerContent(presenter, selected.value)
                }
            }
            runCurrent()
            for ((index, region) in regions.drop(1).withIndex()) {
                selected.value = region
                Snapshot.sendApplyNotifications()
                runCurrent(); clock.sendFrame((index + 1L) * 16_000_000); runCurrent()
                assertEquals(regions.take(index + 2), mounts)
                assertEquals(regions.take(index + 1), disposals)
            }
            // Hide/restore keeps the owner identity and releases sampling before disposal.
            for (enabled in listOf(false, true)) {
                visible.value = enabled
                runCurrent() // No UI frame or recomposition after the OS visibility notification.
                if (!enabled) {
                    value.intValue = 2
                    Snapshot.sendApplyNotifications(); runCurrent()
                    assertEquals(listOf(1), rendered)
                    value.intValue = 3
                    Snapshot.sendApplyNotifications(); runCurrent()
                    assertEquals(listOf(1), rendered)
                }
            }
        } finally {
            composition.dispose(); recomposer.close(); runner.join()
        }
        assertEquals(listOf(1, 3), rendered)
        assertEquals(listOf(true, false, true, false), demands.map { it.second })
        assertEquals(1, demands.map { it.first }.distinct().size)
        assertEquals(regions, disposals)
    }

    private class Region : NaviampGpuVisualizerRegion {
        override fun place(bounds: Rect, clip: Rect, cornerRadius: Float) = Unit
        override fun setVisible(visible: Boolean) = Unit
        override fun submit(frame: NaviampGpuVisualizerFrame) = NaviampGpuSubmission.Accepted
        override fun close() = Unit
    }
}
