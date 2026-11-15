package app.naviamp.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals

/** Shared scenario; Android executes it on-device because Compose calls the real android.os.Trace. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.assertVisualizerClockDrawIsolation() {
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + clock)
    val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
    val composition = Composition(EmptyVisualizerApplier(), recomposer)
    var parentCompositions = 0
    var drawClock: LongState? = null
    try {
        composition.setContent {
            parentCompositions++
            drawClock = rememberVisualizerFrameClock(true, 16L)
        }
        runCurrent()
        // Settle composition and first-frame work before measuring steady motion. The startup
        // composition count is separate from whether subsequent draw-clock updates recompose it.
        clock.sendFrame(100L * 1_000_000L)
        runCurrent()
        val settledCompositions = parentCompositions
        assertEquals(100L, drawClock?.longValue)
        listOf(116L, 132L, 148L).forEach { timestamp ->
            clock.sendFrame(timestamp * 1_000_000L)
            runCurrent()
            assertEquals(settledCompositions, parentCompositions,
                "Advancing the draw clock recomposed its settled parent at $timestamp ms")
        }
        assertEquals(148L, drawClock?.longValue)
    } finally {
        composition.dispose()
        recomposer.close()
        runner.cancelAndJoin()
    }
}

private class EmptyVisualizerApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun onClear() = Unit
}
