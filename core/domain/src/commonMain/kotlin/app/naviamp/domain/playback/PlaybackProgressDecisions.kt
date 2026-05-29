package app.naviamp.domain.playback

import app.naviamp.domain.queue.PlaybackQueue
import kotlin.math.abs

const val DefaultPendingSeekToleranceSeconds = 2.0
const val DefaultPendingSeekStaleProgressWindowMillis = 1_500L

fun PlaybackProgress.mergeMissingWith(previous: PlaybackProgress): PlaybackProgress =
    PlaybackProgress(
        positionSeconds = positionSeconds ?: previous.positionSeconds,
        durationSeconds = durationSeconds ?: previous.durationSeconds,
    )

fun shouldUpdatePlaybackProgressUi(
    pendingSeekPositionSeconds: Double?,
    currentProgress: PlaybackProgress,
    mergedProgress: PlaybackProgress,
    nowMillis: Long,
    lastUiUpdateMillis: Long,
    positionThresholdSeconds: Double,
    updateIntervalMillis: Long,
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
    saveThresholdSeconds: Double,
): Boolean {
    val position = positionSeconds ?: return false
    if (queue.currentIndex !in queue.tracks.indices) return false
    val lastSaved = lastSavedPositionSeconds
    return lastSaved == null || abs(position - lastSaved) >= saveThresholdSeconds
}

fun shouldIgnoreProgressForPendingSeek(
    pendingSeekPositionSeconds: Double?,
    pendingSeekIssuedAtMillis: Long?,
    incomingPositionSeconds: Double?,
    nowMillis: Long,
    toleranceSeconds: Double = DefaultPendingSeekToleranceSeconds,
    staleWindowMillis: Long = DefaultPendingSeekStaleProgressWindowMillis,
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
    toleranceSeconds: Double = DefaultPendingSeekToleranceSeconds,
    staleWindowMillis: Long = DefaultPendingSeekStaleProgressWindowMillis,
): Boolean =
    pendingSeekPositionSeconds != null &&
        (
            incomingPositionSeconds == null ||
                pendingSeekIssuedAtMillis == null ||
                hasPendingSeekReachedTarget(
                    pendingSeekPositionSeconds = pendingSeekPositionSeconds,
                    incomingPositionSeconds = incomingPositionSeconds,
                    toleranceSeconds = toleranceSeconds,
                ) ||
                nowMillis - pendingSeekIssuedAtMillis >= staleWindowMillis
            )

fun hasPendingSeekReachedTarget(
    pendingSeekPositionSeconds: Double?,
    incomingPositionSeconds: Double?,
    toleranceSeconds: Double = DefaultPendingSeekToleranceSeconds,
): Boolean =
    pendingSeekPositionSeconds != null &&
        incomingPositionSeconds != null &&
        abs(incomingPositionSeconds - pendingSeekPositionSeconds) <= toleranceSeconds
