package app.naviamp.desktop

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

internal data class DesktopSleepTimerSelection(
    val timer: SleepTimerState,
    val nowEpochMillis: Long,
    val status: String,
)

internal fun desktopSleepTimerSnapshot(
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

internal fun desktopSleepTimerSelection(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): DesktopSleepTimerSelection {
    val timer = sleepTimerStateForPlayback(
        request = request,
        nowEpochMillis = nowEpochMillis,
        nowPlaying = nowPlaying,
        playbackQueue = playbackQueue,
        playbackProgress = playbackProgress,
        playbackState = playbackState,
    )
    return DesktopSleepTimerSelection(
        timer = timer,
        nowEpochMillis = nowEpochMillis,
        status = sleepTimerDisplayLabel(timer, nowEpochMillis),
    )
}

@Composable
internal fun DesktopSleepTimerExpiryEffect(
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
