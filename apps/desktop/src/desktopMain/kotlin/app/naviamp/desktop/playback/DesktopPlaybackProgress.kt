package app.naviamp.desktop

import app.naviamp.domain.playback.PlaybackProgress
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
