package app.naviamp.domain.playback

const val DefaultPlayReportDurationFraction = 0.5
const val DefaultPlayReportMaxThresholdSeconds = 240.0

fun playReportThresholdSeconds(
    durationSeconds: Double?,
    durationFraction: Double = DefaultPlayReportDurationFraction,
    maxThresholdSeconds: Double = DefaultPlayReportMaxThresholdSeconds,
): Double =
    durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { minOf(it * durationFraction, maxThresholdSeconds) }
        ?: maxThresholdSeconds

fun <SessionId> shouldSubmitPlayReport(
    supportsPlayReporting: Boolean,
    isInternetRadioTrack: Boolean,
    activeSessionId: SessionId,
    submittedSessionId: SessionId?,
    positionSeconds: Double?,
    durationSeconds: Double?,
    durationFraction: Double = DefaultPlayReportDurationFraction,
    maxThresholdSeconds: Double = DefaultPlayReportMaxThresholdSeconds,
): Boolean {
    if (!supportsPlayReporting) return false
    if (isInternetRadioTrack) return false
    if (submittedSessionId == activeSessionId) return false
    val position = positionSeconds ?: return false
    return position >= playReportThresholdSeconds(
        durationSeconds = durationSeconds,
        durationFraction = durationFraction,
        maxThresholdSeconds = maxThresholdSeconds,
    )
}
