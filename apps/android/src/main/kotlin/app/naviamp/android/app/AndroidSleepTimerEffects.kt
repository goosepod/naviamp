package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.SleepTimerPlaybackSnapshot
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.shouldExpireSleepTimer
import app.naviamp.domain.playback.sleepTimerDisplayLabel
import app.naviamp.domain.playback.sleepTimerPlaybackSnapshot
import app.naviamp.domain.playback.sleepTimerStateForPlayback
import app.naviamp.domain.queue.PlaybackQueue
import kotlinx.coroutines.delay

internal data class AndroidSleepTimerSelection(
    val timer: SleepTimerState,
    val nowEpochMillis: Long,
    val status: String,
)

internal fun androidSleepTimerSnapshot(
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): SleepTimerPlaybackSnapshot =
    sleepTimerPlaybackSnapshot(
        nowPlaying = nowPlaying,
        playbackQueue = playbackQueue,
        playbackProgress = playbackProgress,
        playbackState = playbackState,
    )

internal fun androidSleepTimerSelection(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): AndroidSleepTimerSelection {
    val timer = sleepTimerStateForPlayback(
        request = request,
        nowEpochMillis = nowEpochMillis,
        nowPlaying = nowPlaying,
        playbackQueue = playbackQueue,
        playbackProgress = playbackProgress,
        playbackState = playbackState,
    )
    return AndroidSleepTimerSelection(
        timer = timer,
        nowEpochMillis = nowEpochMillis,
        status = sleepTimerDisplayLabel(timer, nowEpochMillis),
    )
}

internal class AndroidSleepTimerController(
    private val state: AndroidAppState,
    private val playbackEngine: AndroidPlaybackEngine,
) {
    fun snapshot(): SleepTimerPlaybackSnapshot =
        androidSleepTimerSnapshot(
            nowPlaying = state.nowPlaying,
            playbackQueue = state.playbackQueue,
            playbackProgress = state.playbackProgress,
            playbackState = state.playbackState,
        )

    fun select(request: SleepTimerRequest) {
        val nowMillis = System.currentTimeMillis()
        val selection = androidSleepTimerSelection(
            request = request,
            nowEpochMillis = nowMillis,
            nowPlaying = state.nowPlaying,
            playbackQueue = state.playbackQueue,
            playbackProgress = state.playbackProgress,
            playbackState = state.playbackState,
        )
        state.sleepTimer = selection.timer
        state.sleepTimerNowEpochMillis = selection.nowEpochMillis
        state.status = selection.status
    }

    fun cancel() {
        state.sleepTimer = null
        state.status = "Sleep timer canceled."
    }

    fun tick(nowMillis: Long) {
        state.sleepTimerNowEpochMillis = nowMillis
    }

    fun expire() {
        playbackEngine.stop()
        state.sleepTimer = null
        state.status = "Sleep timer stopped playback."
    }
}

@Composable
internal fun AndroidSleepTimerExpiryEffect(
    sleepTimer: SleepTimerState?,
    snapshot: SleepTimerPlaybackSnapshot,
    onTick: (Long) -> Unit,
    onExpired: () -> Unit,
) {
    LaunchedEffect(sleepTimer, snapshot) {
        while (sleepTimer != null) {
            val nowMillis = System.currentTimeMillis()
            onTick(nowMillis)
            if (shouldExpireSleepTimer(sleepTimer, nowMillis, snapshot)) {
                onExpired()
                break
            }
            delay(500L)
        }
    }
}
