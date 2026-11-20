package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis

/** The shared display-clock gate; FFT acquisition keeps its independent upstream cadence. */
internal suspend fun runVisualizerFrameClock(
    active: Boolean,
    targetFrameIntervalMillis: Long,
    onFrame: (Long) -> Unit,
) {
    if (!active) {
        onFrame(0L)
        return
    }
    var lastRenderedFrameMillis = 0L
    while (true) {
        withFrameMillis { nextFrameMillis ->
            if (lastRenderedFrameMillis == 0L ||
                nextFrameMillis - lastRenderedFrameMillis >= targetFrameIntervalMillis
            ) {
                lastRenderedFrameMillis = nextFrameMillis
                onFrame(nextFrameMillis)
            }
        }
    }
}

/** Return state, so native draw callbacks read the clock without recomposing their parent surface. */
@Composable
internal fun rememberVisualizerFrameClock(
    active: Boolean,
    targetFrameIntervalMillis: Long,
    vararg restartKeys: Any?,
): LongState {
    val frameMillis = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, targetFrameIntervalMillis, *restartKeys) {
        runVisualizerFrameClock(active, targetFrameIntervalMillis) { frameMillis.longValue = it }
    }
    return frameMillis
}
