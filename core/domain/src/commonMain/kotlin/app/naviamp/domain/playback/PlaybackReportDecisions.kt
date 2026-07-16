package app.naviamp.domain.playback

const val DefaultPlayReportDurationFraction = 0.5
const val DefaultPlayReportMaxThresholdSeconds = 240.0

fun playReportThresholdSeconds(
    durationSeconds: Double?,
    durationFraction: Double = DefaultPlayReportDurationFraction,
    maxThresholdSeconds: Double = DefaultPlayReportMaxThresholdSeconds,
): Double =
    if (durationFraction <= 0.0) {
        0.0
    } else {
        durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { minOf(it * durationFraction, maxThresholdSeconds) }
            ?: maxThresholdSeconds
    }

fun canReportPlaybackTrack(
    supportsPlayReporting: Boolean,
    isInternetRadioTrack: Boolean,
): Boolean =
    supportsPlayReporting && !isInternetRadioTrack

fun shouldReportNowPlaying(
    supportsPlayReporting: Boolean,
    isInternetRadioTrack: Boolean,
    playbackState: PlaybackState,
): Boolean =
    canReportPlaybackTrack(
        supportsPlayReporting = supportsPlayReporting,
        isInternetRadioTrack = isInternetRadioTrack,
    ) && playbackState == PlaybackState.Playing

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
    if (!canReportPlaybackTrack(supportsPlayReporting, isInternetRadioTrack)) return false
    if (submittedSessionId == activeSessionId) return false
    val position = positionSeconds ?: return false
    return position >= playReportThresholdSeconds(
        durationSeconds = durationSeconds,
        durationFraction = durationFraction,
        maxThresholdSeconds = maxThresholdSeconds,
    )
}
