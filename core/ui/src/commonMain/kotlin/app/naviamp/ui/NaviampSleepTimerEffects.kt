package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.playback.SleepTimerPlaybackSnapshot
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.shouldExpireSleepTimer
import kotlinx.coroutines.delay

@Composable
fun NaviampSleepTimerExpiryEffect(
    sleepTimer: SleepTimerState?,
    snapshot: SleepTimerPlaybackSnapshot,
    onTick: (Long) -> Unit,
    onExpired: () -> Unit,
) {
    LaunchedEffect(sleepTimer, snapshot) {
        while (sleepTimer != null) {
            val nowMillis = currentTimeMillis()
            onTick(nowMillis)
            if (shouldExpireSleepTimer(sleepTimer, nowMillis, snapshot)) {
                onExpired()
                break
            }
            delay(500L)
        }
    }
}

internal expect fun currentTimeMillis(): Long
