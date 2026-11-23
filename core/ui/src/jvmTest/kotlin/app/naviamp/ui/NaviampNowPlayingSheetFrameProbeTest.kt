package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Counts composition, measure, placement and draw work in the browse layer and the player for
 * individual frames of a drag, a tap-open and a close, so first-frame bursts are visible on the JVM.
 */
@OptIn(ExperimentalTestApi::class)
class NaviampNowPlayingSheetFrameProbeTest {
    private class Counters {
        var compositions = 0
        var measures = 0
        var placements = 0
        var draws = 0
        fun snapshot() = listOf(compositions, measures, placements, draws)
    }

    private class Probe {
        val browse = Counters()
        val sheet = Counters()
        val open = mutableStateOf(true)
    }

    private fun Modifier.counted(counters: Counters) = layout { measurable, constraints ->
        counters.measures++
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            counters.placements++
            placeable.place(0, 0)
        }
    }.drawBehind { counters.draws++ }

    @Composable
    private fun Rows(counters: Counters, count: Int) {
        Column(Modifier.fillMaxSize()) {
            repeat(count) {
                SideEffect { counters.compositions++ }
                Box(Modifier.fillMaxWidth().height(20.dp).counted(counters))
            }
        }
    }

    private fun probeTest(block: ComposeUiTest.(Probe) -> Unit) = runDesktopComposeUiTest(400, 800) {
        val probe = Probe()
        setContent {
            val state = rememberNaviampNowPlayingSheetState(probe.open.value)
            NaviampNowPlayingSheetLayers(
                state = state,
                onClose = { probe.open.value = false },
                base = { Rows(probe.browse, 40) },
                sheet = { Box(Modifier.fillMaxSize().testTag("probe-sheet")) { Rows(probe.sheet, 20) } },
            )
        }
        waitForIdle()
        block(probe)
    }

    private fun List<Int>.minus(other: List<Int>) = zip(other) { a, b -> a - b }

    /** Runs [action], then advances one frame at a time, returning browse and player work per frame. */
    private fun ComposeUiTest.frames(probe: Probe, count: Int, action: () -> Unit): List<Pair<List<Int>, List<Int>>> {
        mainClock.autoAdvance = false
        var browse = probe.browse.snapshot()
        var sheet = probe.sheet.snapshot()
        action()
        val result = List(count) {
            mainClock.advanceTimeByFrame()
            val nextBrowse = probe.browse.snapshot()
            val nextSheet = probe.sheet.snapshot()
            (nextBrowse.minus(browse) to nextSheet.minus(sheet)).also {
                browse = nextBrowse
                sheet = nextSheet
            }
        }
        mainClock.autoAdvance = true
        return result
    }

    private fun report(label: String, frames: List<Pair<List<Int>, List<Int>>>): List<Pair<List<Int>, List<Int>>> {
        println("PROBE $label [compositions, measures, placements, draws]")
        frames.forEachIndexed { index, (browse, sheet) -> println("PROBE   frame $index browse=$browse sheet=$sheet") }
        return frames
    }

    private val idle = listOf(0, 0, 0, 0)

    private fun assertBrowseIdle(frames: List<Pair<List<Int>, List<Int>>>) =
        frames.forEach { (browse, _) -> assertEquals(idle, browse) }

    @Test
    fun dragFrames() = probeTest { probe ->
        val sheet = onNodeWithTag("probe-sheet")
        val moves = listOf(
            report("drag touch-down", frames(probe, 2) { sheet.performTouchInput { down(Offset(centerX, 100f)) } }),
            report("drag first move", frames(probe, 2) { sheet.performTouchInput { moveBy(Offset(0f, 40f)) } }),
            report("drag second move", frames(probe, 2) { sheet.performTouchInput { moveBy(Offset(0f, 40f)) } }),
            report("drag third move", frames(probe, 2) { sheet.performTouchInput { moveBy(Offset(0f, 40f)) } }),
        ).flatten()
        // Revealing the covered browse layer and moving the player only change layer properties.
        assertBrowseIdle(moves)
        moves.forEach { (_, player) -> assertEquals(idle, player) }
        sheet.performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun closeAndOpenCommandFrames() = probeTest { probe ->
        val close = report("close command", frames(probe, 4) { probe.open.value = false })
        assertBrowseIdle(close)
        close.forEach { (_, player) -> assertEquals(idle, player) }
        waitForIdle()
        // Opening from the mini player composes the player once; the browse layer does no work.
        assertBrowseIdle(report("open command", frames(probe, 4) { probe.open.value = true }))
        waitForIdle()
        report("open settled", frames(probe, 3) {}).forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(idle, player)
        }
    }
}
