package app.naviamp.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VisualizerFrameClockTest {
    @Test
    fun acceptsFirstDisplayFrameAndPreservesConfiguredCadence() = runTest {
        val clock = TestFrameClock()
        val frames = mutableListOf<Long>()
        val job = launch(clock) { runVisualizerFrameClock(true, 33L, frames::add) }
        listOf(100L, 116L, 133L, 149L, 166L).forEach { clock.timestamps.trySend(it) }
        runCurrent()
        assertEquals(listOf(100L, 133L, 166L), frames)
        job.cancelAndJoin()
    }

    @Test
    fun inactiveClockResetsWithoutRequestingDisplayFrames() = runTest {
        val clock = TestFrameClock()
        val frames = mutableListOf<Long>()
        withContext(clock) { runVisualizerFrameClock(false, 16L, frames::add) }
        assertEquals(listOf(0L), frames)
        assertEquals(0, clock.requests)
    }

    @Test
    fun cancellationStopsFrameRequestsAndRestartAcceptsItsFirstFrame() = runTest {
        val clock = TestFrameClock()
        val frames = mutableListOf<Long>()
        val first = launch(clock) { runVisualizerFrameClock(true, 50L, frames::add) }
        clock.timestamps.trySend(100L)
        runCurrent()
        first.cancelAndJoin()
        val requests = clock.requests
        runCurrent()
        assertEquals(requests, clock.requests)
        val restarted = launch(clock) { runVisualizerFrameClock(true, 50L, frames::add) }
        clock.timestamps.trySend(101L)
        runCurrent()
        assertEquals(listOf(100L, 101L), frames)
        restarted.cancelAndJoin()
    }

    private class TestFrameClock : MonotonicFrameClock {
        val timestamps = Channel<Long>(Channel.UNLIMITED)
        var requests = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            requests++
            return onFrame(timestamps.receive() * 1_000_000L)
        }
    }
}
