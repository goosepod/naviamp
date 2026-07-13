package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.math.pow

data class PrepareNextPlaybackPlan(
    val shouldPrepare: Boolean,
    val prepareWindowSeconds: Double? = null,
    val reason: PrepareNextPlaybackReason = PrepareNextPlaybackReason.NotNeeded,
)

data class PrepareNextQueuePlaybackPlan(
    val nextQueueIndex: Int,
    val track: Track,
    val prepareWindowSeconds: Double,
    val reason: PrepareNextPlaybackReason,
)

data class PlaybackReplayGainAdjustment(
    val mode: ReplayGainMode,
    val source: ReplayGainSource?,
    val gainDb: Double?,
    val peak: Double?,
    val volumeFactor: Float,
    val clippingPrevented: Boolean,
) {
    companion object {
        fun off(mode: ReplayGainMode = ReplayGainMode.Off): PlaybackReplayGainAdjustment =
            PlaybackReplayGainAdjustment(
                mode = mode,
                source = null,
                gainDb = null,
                peak = null,
                volumeFactor = 1f,
                clippingPrevented = false,
            )
    }
}

data class PreparedPlaybackMetadataReset(
    val request: PlaybackRequest? = null,
    val replayGainAdjustment: PlaybackReplayGainAdjustment? = null,
    val replayGainFactor: Float = 1f,
    val error: String? = null,
)

data class PreparedPlaybackAdoptionPlan(
    val shouldAdopt: Boolean,
)

data class PlaybackStreamStateReset(
    val stream: Int = 0,
    val currentSourceStream: Int = 0,
    val crossfadeActive: Boolean = false,
    val replayGainAdjustment: PlaybackReplayGainAdjustment = PlaybackReplayGainAdjustment.off(),
    val replayGainFactor: Float = 1f,
)

data class BassPlaybackCleanupReset(
    val stream: PlaybackStreamStateReset,
    val prepared: PreparedPlaybackMetadataReset,
)

data class PlaybackVolumeApplicationPlan(
    val outputVolumeFactor: Float,
    val sourceReplayGainFactor: Float?,
    val directVolumeFactor: Float,
)

data class BassPlaybackFeatureSupport(
    val supportsGapless: Boolean,
    val supportsCrossfade: Boolean,
)

data class PreparedMixerTransitionPlan(
    val crossfadeDurationSeconds: Int,
    val durationMillis: Int,
    val initialNextSourceVolume: Float,
    val finalNextSourceVolume: Float,
    val shouldFadeCurrentSource: Boolean,
) {
    val shouldCrossfade: Boolean
        get() = crossfadeDurationSeconds > 0
}

data class BassMixerCreationPlan(
    val frequency: Int,
    val channels: Int,
    val queueSources: Boolean,
)

enum class PrepareNextPlaybackReason {
    Crossfade,
    Gapless,
    NotNeeded,
}

fun normalizedCrossfadeDurationSeconds(seconds: Int): Int =
    seconds.coerceIn(0, MaxCrossfadeDurationSeconds)

fun crossfadeDurationMillis(seconds: Int): Int =
    normalizedCrossfadeDurationSeconds(seconds) * 1_000

fun shouldQueueMixerSources(crossfadeDurationSeconds: Int): Boolean =
    normalizedCrossfadeDurationSeconds(crossfadeDurationSeconds) <= 0

fun shouldRestoreCurrentSourceForSeek(
    preparedHandle: Int,
    crossfadeActive: Boolean,
): Boolean = preparedHandle != 0 && crossfadeActive

fun bassPlaybackFeatureSupport(supportsMixer: Boolean): BassPlaybackFeatureSupport =
    BassPlaybackFeatureSupport(
        supportsGapless = supportsMixer,
        supportsCrossfade = supportsMixer,
    )

fun planBassMixerCreation(
    sourceInfo: BassStreamInfo?,
    crossfadeDurationSeconds: Int,
): BassMixerCreationPlan =
    BassMixerCreationPlan(
        frequency = sourceInfo?.frequency?.takeIf { it > 0 } ?: DefaultBassMixerFrequency,
        channels = sourceInfo?.channels?.takeIf { it > 0 } ?: DefaultBassMixerChannels,
        queueSources = shouldQueueMixerSources(crossfadeDurationSeconds),
    )

fun playbackReplayGainAdjustment(request: PlaybackRequest): PlaybackReplayGainAdjustment {
    val mode = request.replayGainMode
    val replayGainSource = request.replayGain
    val replayGain = replayGainSource?.replayGain
    if (mode == ReplayGainMode.Off || replayGain == null) {
        if (request.replayGainPreampDb != 0f) {
            val gainDb = request.replayGainPreampDb.toDouble()
            return PlaybackReplayGainAdjustment(
                mode = mode,
                source = replayGainSource?.source,
                gainDb = gainDb,
                peak = null,
                volumeFactor = 10.0.pow(gainDb / 20.0).toFloat(),
                clippingPrevented = false,
            )
        }
        return PlaybackReplayGainAdjustment.off(mode)
    }
    val gainDb = when (mode) {
        ReplayGainMode.Off -> null
        ReplayGainMode.Track -> replayGain.trackGainDb
        ReplayGainMode.Album -> replayGain.albumGainDb ?: replayGain.trackGainDb
    } ?: return PlaybackReplayGainAdjustment.off(mode)
    val peak = when (mode) {
        ReplayGainMode.Off -> null
        ReplayGainMode.Track -> replayGain.trackPeak
        ReplayGainMode.Album -> replayGain.albumPeak ?: replayGain.trackPeak
    }
    val effectiveGainDb = gainDb + request.replayGainPreampDb.toDouble()
    val rawFactor = 10.0.pow(effectiveGainDb / 20.0)
    val clippedFactor = if (peak != null && peak > 0.0 && rawFactor * peak > 1.0) {
        1.0 / peak
    } else {
        rawFactor
    }
    return PlaybackReplayGainAdjustment(
        mode = mode,
        source = replayGainSource.source,
        gainDb = effectiveGainDb,
        peak = peak,
        volumeFactor = clippedFactor.coerceIn(0.0, MaxPlaybackVolumeFactor.toDouble()).toFloat(),
        clippingPrevented = clippedFactor < rawFactor,
    )
}

fun playbackReplayGainFactor(request: PlaybackRequest): Float =
    playbackReplayGainAdjustment(request).volumeFactor

fun shouldReusePreparedPlayback(
    preparedRequest: PlaybackRequest?,
    hasPreparedStream: Boolean,
    request: PlaybackRequest,
): Boolean =
    hasPreparedStream && preparedRequest == request

fun clearPreparedPlaybackMetadata(): PreparedPlaybackMetadataReset =
    PreparedPlaybackMetadataReset()

fun failedPreparedPlaybackMetadata(error: Throwable): PreparedPlaybackMetadataReset =
    PreparedPlaybackMetadataReset(
        error = error.message ?: "Could not prepare next BASS stream.",
    )

fun clearPlaybackStreamState(): PlaybackStreamStateReset =
    PlaybackStreamStateReset()

fun clearBassPlaybackCleanupState(): BassPlaybackCleanupReset =
    BassPlaybackCleanupReset(
        stream = clearPlaybackStreamState(),
        prepared = clearPreparedPlaybackMetadata(),
    )

fun playbackSourceHandle(
    playbackHandle: Int,
    sourceHandle: Int,
): Int =
    sourceHandle.takeIf { it != 0 } ?: playbackHandle

fun playbackUserVolumeFactor(
    volumePercent: Int,
    transientDuckFactor: Float = 1f,
): Float =
    (volumePercent.coerceIn(0, 100) / 100f) *
        transientDuckFactor.coerceIn(0f, MaxPlaybackVolumeFactor)

fun playbackStartSeekPosition(startPositionSeconds: Double?): Double? =
    startPositionSeconds?.takeIf { it > 0.0 }

fun shouldUseBassMixerPlayback(
    request: PlaybackRequest,
    supportsMixer: Boolean,
    requireMediaId: Boolean,
    requiresMixer: Boolean = true,
): Boolean =
    requiresMixer && supportsMixer && (!requireMediaId || request.mediaId != null)

fun canPrepareBassMixerSource(
    playbackHandle: Int,
    currentSourceHandle: Int,
    supportsMixer: Boolean,
): Boolean =
    supportsMixer && playbackHandle != 0 && currentSourceHandle != 0

fun playbackVolumeApplicationPlan(
    userVolumeFactor: Float,
    replayGainFactor: Float,
    hasSeparateSourceStream: Boolean,
): PlaybackVolumeApplicationPlan {
    val safeUserVolume = userVolumeFactor.coerceIn(0f, MaxPlaybackVolumeFactor)
    val safeReplayGain = replayGainFactor.coerceIn(0f, MaxPlaybackVolumeFactor)
    return PlaybackVolumeApplicationPlan(
        outputVolumeFactor = safeUserVolume,
        sourceReplayGainFactor = safeReplayGain.takeIf { hasSeparateSourceStream },
        directVolumeFactor = (safeUserVolume * safeReplayGain).coerceIn(0f, MaxPlaybackVolumeFactor),
    )
}

fun planPreparedMixerTransition(
    crossfadeDurationSeconds: Int,
    replayGainFactor: Float,
): PreparedMixerTransitionPlan {
    val normalizedDuration = normalizedCrossfadeDurationSeconds(crossfadeDurationSeconds)
    val safeReplayGain = replayGainFactor.coerceIn(0f, MaxPlaybackVolumeFactor)
    return PreparedMixerTransitionPlan(
        crossfadeDurationSeconds = normalizedDuration,
        durationMillis = crossfadeDurationMillis(normalizedDuration),
        initialNextSourceVolume = if (normalizedDuration > 0) 0f else safeReplayGain,
        finalNextSourceVolume = safeReplayGain,
        shouldFadeCurrentSource = normalizedDuration > 0,
    )
}

fun planPreparedPlaybackAdoption(
    hasActiveStream: Boolean,
    preparedRequest: PlaybackRequest?,
    hasPreparedStream: Boolean,
    supportsMixer: Boolean,
    request: PlaybackRequest,
): PreparedPlaybackAdoptionPlan =
    PreparedPlaybackAdoptionPlan(
        shouldAdopt = hasActiveStream &&
            supportsMixer &&
            shouldReusePreparedPlayback(preparedRequest, hasPreparedStream, request),
    )

fun planPrepareNextPlayback(
    progress: PlaybackProgress,
    nextQueueIndex: Int?,
    alreadyPreparedNext: Boolean,
    gaplessEnabled: Boolean,
    supportsGapless: Boolean,
    crossfadeDurationSeconds: Int,
    supportsCrossfade: Boolean,
    gaplessPrepareWindowSeconds: Double,
): PrepareNextPlaybackPlan {
    val canPrepareForCrossfade = normalizedCrossfadeDurationSeconds(crossfadeDurationSeconds) > 0 && supportsCrossfade
    val canPrepareForGapless = gaplessEnabled && supportsGapless
    if (!canPrepareForCrossfade && !canPrepareForGapless) return PrepareNextPlaybackPlan(shouldPrepare = false)
    if (nextQueueIndex == null) return PrepareNextPlaybackPlan(shouldPrepare = false)
    if (alreadyPreparedNext) return PrepareNextPlaybackPlan(shouldPrepare = false)
    val position = progress.playbackPlanningPositionSeconds ?: return PrepareNextPlaybackPlan(shouldPrepare = false)
    val duration = progress.durationSeconds ?: return PrepareNextPlaybackPlan(shouldPrepare = false)
    val safeCrossfadeDurationSeconds = normalizedCrossfadeDurationSeconds(crossfadeDurationSeconds)
    val canPrepareForCurrentTrackCrossfade = canPrepareForCrossfade && duration > safeCrossfadeDurationSeconds.toDouble()
    val canPrepareForCurrentTrackGapless = canPrepareForGapless && !canPrepareForCrossfade
    if (!canPrepareForCurrentTrackCrossfade && !canPrepareForCurrentTrackGapless) {
        return PrepareNextPlaybackPlan(shouldPrepare = false)
    }
    val reason = if (canPrepareForCurrentTrackCrossfade) {
        PrepareNextPlaybackReason.Crossfade
    } else {
        PrepareNextPlaybackReason.Gapless
    }
    val prepareWindowSeconds = if (reason == PrepareNextPlaybackReason.Crossfade) {
        safeCrossfadeDurationSeconds.toDouble()
    } else {
        gaplessPrepareWindowSeconds.coerceAtLeast(0.0)
    }
    return PrepareNextPlaybackPlan(
        shouldPrepare = duration - position <= prepareWindowSeconds,
        prepareWindowSeconds = prepareWindowSeconds,
        reason = reason,
    )
}

fun planPrepareNextQueuePlayback(
    queue: PlaybackQueue,
    progress: PlaybackProgress,
    nextQueueIndex: Int?,
    alreadyPreparedNext: Boolean,
    gaplessEnabled: Boolean,
    supportsGapless: Boolean,
    crossfadeDurationSeconds: Int,
    supportsCrossfade: Boolean,
    gaplessPrepareWindowSeconds: Double,
): PrepareNextQueuePlaybackPlan? {
    val index = nextQueueIndex ?: return null
    val transitionPlan = planPrepareNextPlayback(
        progress = progress,
        nextQueueIndex = index,
        alreadyPreparedNext = alreadyPreparedNext,
        gaplessEnabled = gaplessEnabled,
        supportsGapless = supportsGapless,
        crossfadeDurationSeconds = crossfadeDurationSeconds,
        supportsCrossfade = supportsCrossfade,
        gaplessPrepareWindowSeconds = gaplessPrepareWindowSeconds,
    )
    if (!transitionPlan.shouldPrepare) return null
    val track = queue.tracks.getOrNull(index) ?: return null
    return PrepareNextQueuePlaybackPlan(
        nextQueueIndex = index,
        track = track,
        prepareWindowSeconds = transitionPlan.prepareWindowSeconds ?: 0.0,
        reason = transitionPlan.reason,
    )
}

fun isPlaybackProgressAtEnd(
    progress: PlaybackProgress,
    toleranceSeconds: Double = FinishedPositionToleranceSeconds,
): Boolean {
    val position = progress.playbackPlanningPositionSeconds ?: return false
    val duration = progress.durationSeconds ?: return false
    return duration - position <= toleranceSeconds.coerceAtLeast(0.0)
}

fun hasPlaybackProgressOverrunDuration(
    progress: PlaybackProgress,
    toleranceSeconds: Double = FinishedPositionOverrunToleranceSeconds,
): Boolean {
    val position = progress.playbackPlanningPositionSeconds ?: return false
    val duration = progress.durationSeconds ?: return false
    return position - duration >= toleranceSeconds.coerceAtLeast(0.0)
}

fun visualizerBandsFromFft(
    fft: FloatArray,
    bandCount: Int = VisualizerBandCount,
    gain: Float = VisualizerGain,
): List<Float> {
    if (fft.isEmpty() || bandCount <= 0) return emptyList()
    val usable = fft.drop(1)
    if (usable.isEmpty()) return emptyList()
    val bucketSize = (usable.size / bandCount).coerceAtLeast(1)
    return (0 until bandCount).map { bucket ->
        val start = bucket * bucketSize
        if (start >= usable.size) {
            0f
        } else {
            val end = minOf(start + bucketSize, usable.size)
            val peak = usable.subList(start, end).maxOrNull() ?: 0f
            (peak * gain).coerceIn(0f, 1f)
        }
    }
}

fun playbackVisualizerFrameFromFft(
    fft: FloatArray,
    timestampMillis: Long = 0L,
): PlaybackVisualizerFrame? {
    val bands = visualizerBandsFromFft(fft)
    return if (bands.isEmpty()) {
        null
    } else {
        PlaybackVisualizerFrame(
            bands = bands,
            timestampMillis = timestampMillis,
        )
    }
}

fun playbackStateForBassActiveState(activeState: Int): PlaybackState? =
    when (activeState) {
        BassActiveState.Playing -> PlaybackState.Playing
        BassActiveState.Stalled -> PlaybackState.Loading
        BassActiveState.Paused -> PlaybackState.Paused
        else -> null
    }

fun shouldFinishPlaybackForBassState(
    activeState: Int,
    progress: PlaybackProgress,
    currentSourceActiveState: Int? = null,
): Boolean {
    if (hasPlaybackProgressOverrunDuration(progress)) return true
    if (!isPlaybackProgressAtEnd(progress)) return false
    return activeState == BassActiveState.Stopped ||
        currentSourceActiveState == BassActiveState.Stopped
}

fun shouldContinueBassPlaybackPolling(activeState: Int): Boolean =
    activeState != BassActiveState.Stopped

const val MaxCrossfadeDurationSeconds = 12
const val DefaultBassMixerFrequency = 44_100
const val DefaultBassMixerChannels = 2
const val MaxPlaybackVolumeFactor = 4f
const val FinishedPositionToleranceSeconds = 0.75
const val FinishedPositionOverrunToleranceSeconds = 0.5
const val VisualizerBandCount = 32
const val VisualizerGain = 12f
