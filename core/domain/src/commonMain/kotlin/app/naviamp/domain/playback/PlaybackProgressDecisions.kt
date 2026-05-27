package app.naviamp.domain.playback

import kotlin.math.abs

const val DefaultPendingSeekToleranceSeconds = 2.0
const val DefaultPendingSeekStaleProgressWindowMillis = 1_500L

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
