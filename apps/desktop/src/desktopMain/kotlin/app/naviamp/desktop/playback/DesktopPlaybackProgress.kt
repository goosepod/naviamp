package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaybackSource
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.math.abs

fun shouldIgnoreProgressForPendingSeek(
    pendingSeekPositionSeconds: Double?,
    pendingSeekIssuedAtMillis: Long?,
    incomingPositionSeconds: Double?,
    nowMillis: Long,
    toleranceSeconds: Double = PendingSeekToleranceSeconds,
    staleWindowMillis: Long = PendingSeekStaleProgressWindowMillis,
): Boolean =
    pendingSeekPositionSeconds != null &&
        pendingSeekIssuedAtMillis != null &&
        incomingPositionSeconds != null &&
        abs(incomingPositionSeconds - pendingSeekPositionSeconds) > toleranceSeconds &&
        nowMillis - pendingSeekIssuedAtMillis < staleWindowMillis

fun shouldClearPendingSeek(
    pendingSeekPositionSeconds: Double?,
    pendingSeekIssuedAtMillis: Long?,
    incomingPositionSeconds: Double?,
    nowMillis: Long,
    toleranceSeconds: Double = PendingSeekToleranceSeconds,
    staleWindowMillis: Long = PendingSeekStaleProgressWindowMillis,
): Boolean =
    pendingSeekPositionSeconds != null &&
        (
            incomingPositionSeconds == null ||
                pendingSeekIssuedAtMillis == null ||
                abs(incomingPositionSeconds - pendingSeekPositionSeconds) <= toleranceSeconds ||
                nowMillis - pendingSeekIssuedAtMillis >= staleWindowMillis
            )

fun shouldUpdatePlaybackProgressUi(
    pendingSeekPositionSeconds: Double?,
    currentProgress: PlaybackProgress,
    mergedProgress: PlaybackProgress,
    nowMillis: Long,
    lastUiUpdateMillis: Long,
    positionThresholdSeconds: Double = PlaybackProgressUiUpdateThresholdSeconds,
    updateIntervalMillis: Long = PlaybackProgressUiUpdateIntervalMillis,
): Boolean {
    val currentPosition = currentProgress.positionSeconds
    val mergedPosition = mergedProgress.positionSeconds
    return pendingSeekPositionSeconds != null ||
        mergedProgress.durationSeconds != currentProgress.durationSeconds ||
        currentPosition == null ||
        mergedPosition == null ||
        abs(mergedPosition - currentPosition) >= positionThresholdSeconds ||
        nowMillis - lastUiUpdateMillis >= updateIntervalMillis
}

fun shouldSavePlaybackPosition(
    queue: PlaybackQueue,
    positionSeconds: Double?,
    lastSavedPositionSeconds: Double?,
    saveThresholdSeconds: Double = PlaybackPositionSaveThresholdSeconds,
): Boolean {
    val position = positionSeconds ?: return false
    if (queue.currentIndex !in queue.tracks.indices) return false
    val lastSaved = lastSavedPositionSeconds
    return lastSaved == null || abs(position - lastSaved) >= saveThresholdSeconds
}

fun canUsePreviousButton(
    queue: PlaybackQueue,
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double = PreviousRestartThresholdSeconds,
): Boolean =
    queue.hasPrevious() ||
        shouldRestartInsteadOfPrevious(
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )

fun canUseNextButton(
    queue: PlaybackQueue,
    repeatMode: RepeatMode,
): Boolean =
    queue.hasNext() ||
        (repeatMode == RepeatMode.Queue && queue.tracks.isNotEmpty())

fun shouldRestartInsteadOfPrevious(
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double = PreviousRestartThresholdSeconds,
): Boolean =
    previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
        (positionSeconds ?: 0.0) > restartThresholdSeconds

fun nextRepeatMode(mode: RepeatMode): RepeatMode =
    when (mode) {
        RepeatMode.Off -> RepeatMode.Queue
        RepeatMode.Queue -> RepeatMode.Track
        RepeatMode.Track -> RepeatMode.Off
    }

fun shouldReplayCurrentForSeek(
    streamQuality: StreamQuality,
    playbackSource: PlaybackSource,
): Boolean =
    streamQuality is StreamQuality.Transcoded &&
        (
            playbackSource == PlaybackSource.ProviderStream ||
                playbackSource == PlaybackSource.ProviderStreamCacheDisabled
            )
