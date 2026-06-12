package app.naviamp.domain.playback

sealed interface PreparedBassPlaybackPlan {
    data object ReusePrepared : PreparedBassPlaybackPlan
    data object NotSupported : PreparedBassPlaybackPlan

    data class PrepareMixer(
        val replayGainFactor: Float,
        val replayGainAdjustment: PlaybackReplayGainAdjustment,
        val isLocalFileUrl: Boolean,
    ) : PreparedBassPlaybackPlan

    data class PrepareDirect(
        val replayGainFactor: Float,
        val replayGainAdjustment: PlaybackReplayGainAdjustment,
        val isLocalFileUrl: Boolean,
    ) : PreparedBassPlaybackPlan
}

data class PreparedBassPlaybackAdoption(
    val shouldAdopt: Boolean,
    val preparedHandle: Int,
)

data class PreparedBassPlaybackStateUpdate(
    val preparedHandle: Int,
    val preparedRequest: PlaybackRequest?,
    val replayGainAdjustment: PlaybackReplayGainAdjustment?,
    val replayGainFactor: Float,
    val error: String?,
)

data class PreparedBassPlaybackAdoptionUpdate(
    val currentSourceHandle: Int,
    val replayGainAdjustment: PlaybackReplayGainAdjustment,
    val replayGainFactor: Float,
    val preparedReset: PreparedPlaybackMetadataReset,
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
    val replayGainAdjustment = playbackReplayGainAdjustment(request)
    val replayGainFactor = replayGainAdjustment.volumeFactor
    val isLocalFileUrl = isPlaybackLocalFileUrl(request.url)
    return when {
        canPrepareBassMixerSource(
            playbackHandle = playbackHandle,
            currentSourceHandle = currentSourceHandle,
            supportsMixer = supportsMixer,
        ) -> PreparedBassPlaybackPlan.PrepareMixer(
            replayGainFactor = replayGainFactor,
            replayGainAdjustment = replayGainAdjustment,
            isLocalFileUrl = isLocalFileUrl,
        )
        allowDirectFallback -> PreparedBassPlaybackPlan.PrepareDirect(
            replayGainFactor = replayGainFactor,
            replayGainAdjustment = replayGainAdjustment,
            isLocalFileUrl = isLocalFileUrl,
        )
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

fun preparedBassPlaybackSucceeded(
    preparedHandle: Int,
    request: PlaybackRequest,
    replayGainAdjustment: PlaybackReplayGainAdjustment,
): PreparedBassPlaybackStateUpdate =
    PreparedBassPlaybackStateUpdate(
        preparedHandle = preparedHandle,
        preparedRequest = request,
        replayGainAdjustment = replayGainAdjustment,
        replayGainFactor = replayGainAdjustment.volumeFactor,
        error = null,
    )

fun preparedBassPlaybackFailed(error: Throwable): PreparedBassPlaybackStateUpdate {
    val reset = failedPreparedPlaybackMetadata(error)
    return PreparedBassPlaybackStateUpdate(
        preparedHandle = 0,
        preparedRequest = reset.request,
        replayGainAdjustment = reset.replayGainAdjustment,
        replayGainFactor = reset.replayGainFactor,
        error = reset.error,
    )
}

fun preparedBassPlaybackAdopted(
    adoption: PreparedBassPlaybackAdoption,
    replayGainAdjustment: PlaybackReplayGainAdjustment,
): PreparedBassPlaybackAdoptionUpdate? =
    if (adoption.shouldAdopt) {
        PreparedBassPlaybackAdoptionUpdate(
            currentSourceHandle = adoption.preparedHandle,
            replayGainAdjustment = replayGainAdjustment,
            replayGainFactor = replayGainAdjustment.volumeFactor,
            preparedReset = clearPreparedPlaybackMetadata(),
        )
    } else {
        null
    }

fun preparedBassPlaybackAdopted(
    adoption: PreparedBassPlaybackAdoption,
    replayGainFactor: Float,
): PreparedBassPlaybackAdoptionUpdate? =
    preparedBassPlaybackAdopted(
        adoption = adoption,
        replayGainAdjustment = PlaybackReplayGainAdjustment.off().copy(
            volumeFactor = replayGainFactor,
        ),
    )
