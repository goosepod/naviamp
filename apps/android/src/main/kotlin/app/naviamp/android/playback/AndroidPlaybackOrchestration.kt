package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.playback.DefaultPendingSeekStaleProgressWindowMillis
import app.naviamp.domain.playback.DefaultPendingSeekToleranceSeconds
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.mergeMissingWith
import app.naviamp.domain.playback.shouldClearPendingSeek
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek

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
        if (sessionToken != playbackSessionToken) return
        if (progress.positionSeconds == null && progress.durationSeconds == null) {
            if (pendingRestoreStartPositionSeconds != null) return
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
            AndroidPlaybackNotificationControls.positionMillis = null
            AndroidPlaybackNotificationControls.durationMillis = null
            AndroidPlaybackForegroundService.updateProgress(context, null, null)
            return
        }
        val pendingSeek = pendingSeekPositionSeconds
        val pendingSeekIssuedAt = pendingSeekIssuedAtMillis
        val progressPosition = progress.positionSeconds
        val nowMillis = System.currentTimeMillis()
        if (
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = pendingSeek,
                pendingSeekIssuedAtMillis = pendingSeekIssuedAt,
                incomingPositionSeconds = progressPosition,
                nowMillis = nowMillis,
                toleranceSeconds = DefaultPendingSeekToleranceSeconds,
                staleWindowMillis = DefaultPendingSeekStaleProgressWindowMillis,
            )
        ) {
            return
        }
        var pendingRestoreStart = pendingRestoreStartPositionSeconds
        if (
            pendingRestoreStart != null &&
            (pendingSeekIssuedAt == null || nowMillis - pendingSeekIssuedAt >= DefaultPendingSeekStaleProgressWindowMillis)
        ) {
            pendingRestoreStartPositionSeconds = null
            pendingRestoreStart = null
        }
        if (
            pendingRestoreStart != null &&
            progressPosition != null &&
            progressPosition < pendingRestoreStart - DefaultPendingSeekToleranceSeconds
        ) {
            return
        }
        if (
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = pendingSeek,
                pendingSeekIssuedAtMillis = pendingSeekIssuedAt,
                incomingPositionSeconds = progressPosition,
                nowMillis = nowMillis,
                toleranceSeconds = DefaultPendingSeekToleranceSeconds,
                staleWindowMillis = DefaultPendingSeekStaleProgressWindowMillis,
            )
        ) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        if (
            pendingRestoreStart != null &&
            progressPosition != null &&
            progressPosition >= pendingRestoreStart - DefaultPendingSeekToleranceSeconds
        ) {
            pendingRestoreStartPositionSeconds = null
        }
        playbackProgress = progress.mergeMissingWith(playbackProgress)
        maybeReportPlayed(playbackProgress)
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds
            ?.secondsToMillis()
            ?: nowPlaying?.durationSeconds?.toDouble()?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        if (nowMillis - lastAndroidAutoProgressPublishAtMillis >= AndroidAutoProgressPublishIntervalMillis) {
            lastAndroidAutoProgressPublishAtMillis = nowMillis
            AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        }
        prepareNextIfNeeded(sessionToken, playbackProgress)
    }
}
