package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue

data class PreparedNextPlaybackRequest(
    val nextQueueIndex: Int,
    val track: Track,
    val request: PlaybackRequest,
)

suspend fun prepareNextPlaybackRequest(
    queue: PlaybackQueue,
    progress: PlaybackProgress,
    nextQueueIndex: Int?,
    alreadyPreparedNext: Boolean,
    gaplessEnabled: Boolean,
    supportsGapless: Boolean,
    crossfadeDurationSeconds: Int,
    supportsCrossfade: Boolean,
    gaplessPrepareWindowSeconds: Double,
    provider: MediaProvider,
    sourceId: String?,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    audioAssets: PlaybackAudioAssetRepository,
    replayGainMode: ReplayGainMode,
    supportsReplayGain: Boolean,
    replayGainForTrack: suspend (Track, StreamQuality) -> PlaybackReplayGain?,
): PreparedNextPlaybackRequest? {
    val plan = planPrepareNextQueuePlayback(
        queue = queue,
        progress = progress,
        nextQueueIndex = nextQueueIndex,
        alreadyPreparedNext = alreadyPreparedNext,
        gaplessEnabled = gaplessEnabled,
        supportsGapless = supportsGapless,
        crossfadeDurationSeconds = crossfadeDurationSeconds,
        supportsCrossfade = supportsCrossfade,
        gaplessPrepareWindowSeconds = gaplessPrepareWindowSeconds,
    ) ?: return null
    return preparedNextPlaybackRequest(
        plan = plan,
        provider = provider,
        sourceId = sourceId,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        audioAssets = audioAssets,
        replayGainMode = replayGainMode,
        supportsReplayGain = supportsReplayGain,
        replayGainForTrack = replayGainForTrack,
    )
}

suspend fun preparedNextPlaybackRequest(
    plan: PrepareNextQueuePlaybackPlan,
    provider: MediaProvider,
    sourceId: String?,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    audioAssets: PlaybackAudioAssetRepository,
    replayGainMode: ReplayGainMode,
    supportsReplayGain: Boolean,
    replayGainForTrack: suspend (Track, StreamQuality) -> PlaybackReplayGain?,
): PreparedNextPlaybackRequest {
    val track = plan.track
    val audioSourcePlan = resolvePlaybackAudioSource(
        sourceId = sourceId,
        track = track,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        audioAssets = audioAssets,
    )
    val streamUrl = audioSourcePlan.playbackStreamUrl(
        providerStreamUrl = { target -> provider.streamUrl(target.providerStreamRequest) },
    )
    return PreparedNextPlaybackRequest(
        nextQueueIndex = plan.nextQueueIndex,
        track = track,
        request = PlaybackRequest(
            url = streamUrl,
            mediaId = track.id.value,
            replayGainMode = if (supportsReplayGain) replayGainMode else ReplayGainMode.Off,
            replayGain = replayGainForTrack(track, quality),
        ),
    )
}
