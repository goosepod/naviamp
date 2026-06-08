package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
