package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.planPlaybackProgressUpdate

fun beginAndroidPlaybackSession(
    state: AndroidAppState,
    playbackQueueController: PlaybackQueueController,
    resetProgress: Boolean = true,
): Long {
    with(state) {
        playbackSessionToken += 1
        submittedPlayReportSessionToken = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        playbackQueueController.clearPreparedNext()
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        if (resetProgress) {
            playbackProgress = PlaybackProgress.Unknown
        }
        return playbackSessionToken
    }
}

fun handleAndroidPlaybackProgressChanged(
    context: Context,
    state: AndroidAppState,
    sessionToken: Long,
    progress: PlaybackProgress,
    maybeReportPlayed: (PlaybackProgress) -> Unit,
    prepareNextIfNeeded: (Long, PlaybackProgress) -> Unit,
) {
    with(state) {
        val nowMillis = System.currentTimeMillis()
        val plan = planPlaybackProgressUpdate(
            sessionToken = sessionToken,
            activeSessionToken = playbackSessionToken,
            incomingProgress = progress,
            currentProgress = playbackProgress,
            pendingSeekPositionSeconds = pendingSeekPositionSeconds,
            pendingSeekIssuedAtMillis = pendingSeekIssuedAtMillis,
            pendingRestoreStartPositionSeconds = pendingRestoreStartPositionSeconds,
            nowMillis = nowMillis,
            lastExternalProgressPublishAtMillis = lastAndroidAutoProgressPublishAtMillis,
            externalProgressPublishIntervalMillis = AndroidAutoProgressPublishIntervalMillis,
        )
        if (plan.ignore) return
        if (plan.resetToUnknown) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
            AndroidPlaybackNotificationControls.positionMillis = null
            AndroidPlaybackNotificationControls.durationMillis = null
            AndroidPlaybackForegroundService.updateProgress(context, null, null)
            return
        }
        if (plan.clearPendingSeek) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        if (plan.clearPendingRestoreStart) {
            pendingRestoreStartPositionSeconds = null
        }
        playbackProgress = plan.progress ?: return
        if (plan.shouldReportPlayed) maybeReportPlayed(playbackProgress)
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds
            ?.secondsToMillis()
            ?: nowPlaying?.durationSeconds?.toDouble()?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        if (plan.shouldPublishExternalProgress) {
            lastAndroidAutoProgressPublishAtMillis = nowMillis
            AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        }
        if (plan.shouldPrepareNext) prepareNextIfNeeded(sessionToken, playbackProgress)
    }
}
