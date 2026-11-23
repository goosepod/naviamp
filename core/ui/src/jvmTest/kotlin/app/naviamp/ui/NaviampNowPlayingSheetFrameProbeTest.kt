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
 * individual frames of a drag, a release, a tap-open and a close, so frame bursts are visible on
 * the JVM. Release probes give the browse layer the app state that carries the open command, as
 * the shared shell does, so committing that command recomposes it.
 */
@OptIn(ExperimentalTestApi::class)
class NaviampNowPlayingSheetFrameProbeTest {
    private class Counters {
        var compositions = 0
        var measures = 0
        var placements = 0
        var draws = 0
        var lastAppState: Any? = null
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
    private fun Rows(counters: Counters, count: Int, appState: Any? = null) {
        Column(Modifier.fillMaxSize()) {
            repeat(count) {
                // Reading appState keeps the compiler from skipping rows when only it changes.
                SideEffect { counters.compositions++; counters.lastAppState = appState }
                Box(Modifier.fillMaxWidth().height(20.dp).counted(counters))
            }
        }
    }

    private fun probeTest(
        initiallyOpen: Boolean = true,
        browseReceivesAppState: Boolean = false,
        block: ComposeUiTest.(Probe) -> Unit,
    ) = runDesktopComposeUiTest(400, 800) {
        val probe = Probe().apply { open.value = initiallyOpen }
        setContent {
            val open = probe.open.value
            val state = rememberNaviampNowPlayingSheetState(open)
            NaviampNowPlayingSheetLayers(
                state = state,
                onClose = { probe.open.value = false },
                base = {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().height(60.dp)
                                .naviampNowPlayingOpenGesture(state) { probe.open.value = true }
                                .testTag("probe-mini"),
                        )
                        Rows(probe.browse, 40, appState = open.takeIf { browseReceivesAppState })
                    }
                },
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
    fun releaseFrames() = probeTest(browseReceivesAppState = true) { probe ->
        val sheet = onNodeWithTag("probe-sheet")
        sheet.performTouchInput {
            down(Offset(centerX, 100f))
            repeat(9) { moveBy(Offset(0f, 50f), delayMillis = 32) }
        }
        waitForIdle()
        val settle = report("release past the threshold", frames(probe, 40) { sheet.performTouchInput { up() } })
        // Lifting the finger only starts the settle animation; the close command is committed
        // once the player has left the screen.
        settle.take(5).forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(idle, player)
        }
        waitForIdle()
        assertEquals(false, probe.open.value)
    }

    @Test
    fun springBackReleaseFrames() = probeTest(browseReceivesAppState = true) { probe ->
        val sheet = onNodeWithTag("probe-sheet")
        sheet.performTouchInput {
            down(Offset(centerX, 100f))
            repeat(3) { moveBy(Offset(0f, 30f), delayMillis = 200) }
        }
        waitForIdle()
        val settle = report("release below the threshold", frames(probe, 30) { sheet.performTouchInput { up() } })
        settle.forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(idle, player)
        }
        assertEquals(true, probe.open.value)
    }

    @Test
    fun miniPlayerDragBuildsThePlayerBeforeItMoves() = probeTest(initiallyOpen = false, browseReceivesAppState = true) { probe ->
        val mini = onNodeWithTag("probe-mini")
        val down = report("mini player touch-down", frames(probe, 3) { mini.performTouchInput { down(center) } })
        val firstMove = report("mini player first move", frames(probe, 2) { mini.performTouchInput { moveBy(Offset(0f, -40f)) } })
        assertEquals(20, down.sumOf { (_, player) -> player[0] })
        firstMove.forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(0, player[0])
            assertEquals(0, player[1])
        }
        val release = report("mini player release", frames(probe, 5) {
            mini.performTouchInput { repeat(8) { moveBy(Offset(0f, -50f), delayMillis = 32) }; up() }
        })
        release.forEach { (browse, _) -> assertEquals(idle, browse) }
        waitForIdle()
        assertEquals(true, probe.open.value)
    }

    /**
     * An injected or very fast swipe crosses touch slop before the first frame after touch-down,
     * so building the player and its first movement share that frame. The build still happens
     * once and later frames stay idle.
     */
    @Test
    fun miniPlayerSwipeCrossingSlopBeforeTheFirstFrame() = probeTest(initiallyOpen = false) { probe ->
        val mini = onNodeWithTag("probe-mini")
        val frames = report("mini player down and slop in one frame", frames(probe, 4) {
            mini.performTouchInput { down(center); moveBy(Offset(0f, -40f)) }
        })
        assertEquals(listOf(20, 20, 20, 20), frames.first().second)
        frames.drop(1).forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(idle, player)
        }
        mini.performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun miniPlayerTouchWithoutDragDoesNotKeepThePlayer() = probeTest(initiallyOpen = false) { probe ->
        onNodeWithTag("probe-mini").performTouchInput { down(center); up() }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag("probe-sheet").assertDoesNotExist()
        report("minimized idle", frames(probe, 3) {}).forEach { (browse, player) ->
            assertEquals(idle, browse)
            assertEquals(idle, player)
        }
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
