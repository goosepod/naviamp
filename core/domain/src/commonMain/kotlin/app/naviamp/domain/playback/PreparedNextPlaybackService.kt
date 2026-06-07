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

data class PreparedNextPlaybackWork(
    val markPreparedNextIndex: Int,
    val plan: PrepareNextQueuePlaybackPlan,
)

data class PreparedNextPlaybackSettings(
    val gaplessEnabled: Boolean,
    val supportsGapless: Boolean,
    val crossfadeDurationSeconds: Int,
    val supportsCrossfade: Boolean,
    val gaplessPrepareWindowSeconds: Double,
)

class PreparedNextPlaybackCoordinator(
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val quality: () -> StreamQuality?,
    private val audioCachingEnabled: () -> Boolean,
    private val audioAssets: PlaybackAudioAssetRepository,
    private val replayGainMode: () -> ReplayGainMode,
    private val supportsReplayGain: () -> Boolean,
    private val replayGainForTrack: suspend (Track, StreamQuality) -> PlaybackReplayGain?,
) {
    fun plan(
        queue: PlaybackQueue,
        progress: PlaybackProgress,
        nextQueueIndex: Int?,
        preparedNextIndex: Int?,
        settings: PreparedNextPlaybackSettings,
    ): PrepareNextQueuePlaybackPlan? =
        planPreparedNextQueuePlayback(
            queue = queue,
            progress = progress,
            nextQueueIndex = nextQueueIndex,
            preparedNextIndex = preparedNextIndex,
            settings = settings,
        )

    fun work(
        queue: PlaybackQueue,
        progress: PlaybackProgress,
        nextQueueIndex: Int?,
        preparedNextIndex: Int?,
        settings: PreparedNextPlaybackSettings,
    ): PreparedNextPlaybackWork? =
        preparedNextPlaybackWork(
            queue = queue,
            progress = progress,
            nextQueueIndex = nextQueueIndex,
            preparedNextIndex = preparedNextIndex,
            settings = settings,
        )

    suspend fun request(work: PreparedNextPlaybackWork): PreparedNextPlaybackRequest? =
        request(work.plan)

    suspend fun request(plan: PrepareNextQueuePlaybackPlan): PreparedNextPlaybackRequest? {
        val currentProvider = provider() ?: return null
        val currentQuality = quality() ?: return null
        return preparedNextPlaybackRequest(
            plan = plan,
            provider = currentProvider,
            sourceId = sourceId(),
            quality = currentQuality,
            audioCachingEnabled = audioCachingEnabled(),
            audioAssets = audioAssets,
            replayGainMode = replayGainMode(),
            supportsReplayGain = supportsReplayGain(),
            replayGainForTrack = replayGainForTrack,
        )
    }
}

fun preparedNextPlaybackWork(
    queue: PlaybackQueue,
    progress: PlaybackProgress,
    nextQueueIndex: Int?,
    preparedNextIndex: Int?,
    settings: PreparedNextPlaybackSettings,
): PreparedNextPlaybackWork? {
    val plan = planPreparedNextQueuePlayback(
        queue = queue,
        progress = progress,
        nextQueueIndex = nextQueueIndex,
        preparedNextIndex = preparedNextIndex,
        settings = settings,
    ) ?: return null
    return PreparedNextPlaybackWork(
        markPreparedNextIndex = plan.nextQueueIndex,
        plan = plan,
    )
}

fun planPreparedNextQueuePlayback(
    queue: PlaybackQueue,
    progress: PlaybackProgress,
    nextQueueIndex: Int?,
    preparedNextIndex: Int?,
    settings: PreparedNextPlaybackSettings,
): PrepareNextQueuePlaybackPlan? =
    planPrepareNextQueuePlayback(
        queue = queue,
        progress = progress,
        nextQueueIndex = nextQueueIndex,
        alreadyPreparedNext = preparedNextIndex == nextQueueIndex,
        gaplessEnabled = settings.gaplessEnabled,
        supportsGapless = settings.supportsGapless,
        crossfadeDurationSeconds = settings.crossfadeDurationSeconds,
        supportsCrossfade = settings.supportsCrossfade,
        gaplessPrepareWindowSeconds = settings.gaplessPrepareWindowSeconds,
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
