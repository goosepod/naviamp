package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassCreatedPlayback

data class BassPlaybackCreationPlan(
    val useMixer: Boolean,
    val replayGainAdjustment: PlaybackReplayGainAdjustment,
    val isLocalFileUrl: Boolean,
) {
    val replayGainFactor: Float
        get() = replayGainAdjustment.volumeFactor
}

data class BassPlaybackActivationUpdate(
    val playbackHandle: Int,
    val sourceHandle: Int,
    val replayGainAdjustment: PlaybackReplayGainAdjustment,
) {
    val replayGainFactor: Float
        get() = replayGainAdjustment.volumeFactor
}

fun planBassPlaybackCreation(
    request: PlaybackRequest,
    supportsMixer: Boolean,
    requireMediaId: Boolean,
    requiresMixer: Boolean = true,
): BassPlaybackCreationPlan =
    BassPlaybackCreationPlan(
        useMixer = shouldUseBassMixerPlayback(
            request = request,
            supportsMixer = supportsMixer,
            requireMediaId = requireMediaId,
            requiresMixer = requiresMixer,
        ),
        replayGainAdjustment = playbackReplayGainAdjustment(request),
        isLocalFileUrl = isPlaybackLocalFileUrl(request.url),
    )

fun isPlaybackLocalFileUrl(url: String): Boolean =
    url.substringBefore(':', missingDelimiterValue = "")
        .equals("file", ignoreCase = true)

fun bassPlaybackActivated(
    playback: BassCreatedPlayback,
    replayGainAdjustment: PlaybackReplayGainAdjustment,
): BassPlaybackActivationUpdate =
    BassPlaybackActivationUpdate(
        playbackHandle = playback.playbackHandle,
        sourceHandle = playback.sourceHandle,
        replayGainAdjustment = replayGainAdjustment,
    )
