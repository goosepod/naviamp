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

data class PlaybackProgressUpdatePlan(
    val ignore: Boolean = false,
    val resetToUnknown: Boolean = false,
    val progress: PlaybackProgress? = null,
    val clearPendingSeek: Boolean = false,
    val clearPendingRestoreStart: Boolean = false,
    val shouldReportPlayed: Boolean = false,
    val shouldPublishExternalProgress: Boolean = false,
    val shouldPrepareNext: Boolean = false,
)

fun planPlaybackProgressUpdate(
    sessionToken: Long,
    activeSessionToken: Long,
    incomingProgress: PlaybackProgress,
    currentProgress: PlaybackProgress,
    pendingSeekPositionSeconds: Double?,
    pendingSeekIssuedAtMillis: Long?,
    pendingRestoreStartPositionSeconds: Double?,
    nowMillis: Long,
    lastExternalProgressPublishAtMillis: Long,
    externalProgressPublishIntervalMillis: Long,
    toleranceSeconds: Double = DefaultPendingSeekToleranceSeconds,
    staleWindowMillis: Long = DefaultPendingSeekStaleProgressWindowMillis,
): PlaybackProgressUpdatePlan {
    if (sessionToken != activeSessionToken) return PlaybackProgressUpdatePlan(ignore = true)
    val progressPosition = incomingProgress.positionSeconds
    if (progressPosition == null && incomingProgress.durationSeconds == null) {
        return if (pendingRestoreStartPositionSeconds != null) {
            PlaybackProgressUpdatePlan(ignore = true)
        } else {
            PlaybackProgressUpdatePlan(
                resetToUnknown = true,
                clearPendingSeek = true,
                shouldPublishExternalProgress = true,
            )
        }
    }
    if (
        shouldIgnoreProgressForPendingSeek(
            pendingSeekPositionSeconds = pendingSeekPositionSeconds,
            pendingSeekIssuedAtMillis = pendingSeekIssuedAtMillis,
            incomingPositionSeconds = progressPosition,
            nowMillis = nowMillis,
            toleranceSeconds = toleranceSeconds,
            staleWindowMillis = staleWindowMillis,
        )
    ) {
        return PlaybackProgressUpdatePlan(ignore = true)
    }

    val pendingRestoreStartIsStale = pendingRestoreStartPositionSeconds != null &&
        (pendingSeekIssuedAtMillis == null || nowMillis - pendingSeekIssuedAtMillis >= staleWindowMillis)
    val activePendingRestoreStart = pendingRestoreStartPositionSeconds.takeUnless { pendingRestoreStartIsStale }
    if (
        activePendingRestoreStart != null &&
        progressPosition != null &&
        progressPosition < activePendingRestoreStart - toleranceSeconds
    ) {
        return PlaybackProgressUpdatePlan(ignore = true)
    }

    val clearPendingSeek = shouldClearPendingSeek(
        pendingSeekPositionSeconds = pendingSeekPositionSeconds,
        pendingSeekIssuedAtMillis = pendingSeekIssuedAtMillis,
        incomingPositionSeconds = progressPosition,
        nowMillis = nowMillis,
        toleranceSeconds = toleranceSeconds,
        staleWindowMillis = staleWindowMillis,
    )
    val clearPendingRestoreStart = pendingRestoreStartIsStale ||
        (
            activePendingRestoreStart != null &&
                progressPosition != null &&
                progressPosition >= activePendingRestoreStart - toleranceSeconds
            )
    val mergedProgress = incomingProgress.mergeMissingWith(currentProgress)
    return PlaybackProgressUpdatePlan(
        progress = mergedProgress,
        clearPendingSeek = clearPendingSeek,
        clearPendingRestoreStart = clearPendingRestoreStart,
        shouldReportPlayed = true,
        shouldPublishExternalProgress = nowMillis - lastExternalProgressPublishAtMillis >= externalProgressPublishIntervalMillis,
        shouldPrepareNext = true,
    )
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
