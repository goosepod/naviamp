package app.naviamp.domain.playback

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
