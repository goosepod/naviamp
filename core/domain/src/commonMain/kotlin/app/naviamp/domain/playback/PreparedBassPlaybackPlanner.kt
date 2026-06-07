package app.naviamp.domain.playback

sealed interface PreparedBassPlaybackPlan {
    data object ReusePrepared : PreparedBassPlaybackPlan
    data object NotSupported : PreparedBassPlaybackPlan

    data class PrepareMixer(
        val replayGainFactor: Float,
    ) : PreparedBassPlaybackPlan

    data class PrepareDirect(
        val replayGainFactor: Float,
    ) : PreparedBassPlaybackPlan
}

data class PreparedBassPlaybackAdoption(
    val shouldAdopt: Boolean,
    val preparedHandle: Int,
)

fun planPreparedBassPlayback(
    playbackHandle: Int,
    currentSourceHandle: Int,
    preparedRequest: PlaybackRequest?,
    preparedHandle: Int,
    supportsMixer: Boolean,
    request: PlaybackRequest,
    allowDirectFallback: Boolean,
): PreparedBassPlaybackPlan {
    if (shouldReusePreparedPlayback(preparedRequest, preparedHandle != 0, request)) {
        return PreparedBassPlaybackPlan.ReusePrepared
    }
    val replayGainFactor = playbackReplayGainFactor(request)
    return when {
        canPrepareBassMixerSource(
            playbackHandle = playbackHandle,
            currentSourceHandle = currentSourceHandle,
            supportsMixer = supportsMixer,
        ) -> PreparedBassPlaybackPlan.PrepareMixer(replayGainFactor)
        allowDirectFallback -> PreparedBassPlaybackPlan.PrepareDirect(replayGainFactor)
        else -> PreparedBassPlaybackPlan.NotSupported
    }
}

fun planPreparedBassPlaybackAdoption(
    playbackHandle: Int,
    preparedRequest: PlaybackRequest?,
    preparedHandle: Int,
    supportsMixer: Boolean,
    request: PlaybackRequest,
): PreparedBassPlaybackAdoption {
    val plan = planPreparedPlaybackAdoption(
        hasActiveStream = playbackHandle != 0,
        preparedRequest = preparedRequest,
        hasPreparedStream = preparedHandle != 0,
        supportsMixer = supportsMixer,
        request = request,
    )
    return PreparedBassPlaybackAdoption(
        shouldAdopt = plan.shouldAdopt,
        preparedHandle = preparedHandle,
    )
}
