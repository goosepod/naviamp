package app.naviamp.domain.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class PlaybackFadeEnvelopePoint(
    val positionBytes: Long,
    val volume: Float,
)

data class PrepareNextPlaybackPlan(
    val shouldPrepare: Boolean,
    val prepareWindowSeconds: Double? = null,
    val reason: PrepareNextPlaybackReason = PrepareNextPlaybackReason.NotNeeded,
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

data class PlaybackVolumeApplicationPlan(
    val outputVolumeFactor: Float,
    val sourceReplayGainFactor: Float?,
    val directVolumeFactor: Float,
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

fun playbackReplayGainAdjustment(request: PlaybackRequest): PlaybackReplayGainAdjustment {
    val mode = request.replayGainMode
    val replayGainSource = request.replayGain
    val replayGain = replayGainSource?.replayGain
    if (mode == ReplayGainMode.Off || replayGain == null) {
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
    val rawFactor = 10.0.pow(gainDb / 20.0)
    val clippedFactor = if (peak != null && peak > 0.0 && rawFactor * peak > 1.0) {
        1.0 / peak
    } else {
        rawFactor
    }
    return PlaybackReplayGainAdjustment(
        mode = mode,
        source = replayGainSource.source,
        gainDb = gainDb,
        peak = peak,
        volumeFactor = clippedFactor.coerceIn(0.0, MaxPlaybackVolumeFactor.toDouble()).toFloat(),
        clippingPrevented = clippedFactor < rawFactor,
    )
}

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
    val position = progress.positionSeconds ?: return PrepareNextPlaybackPlan(shouldPrepare = false)
    val duration = progress.durationSeconds ?: return PrepareNextPlaybackPlan(shouldPrepare = false)
    val reason = if (canPrepareForCrossfade) {
        PrepareNextPlaybackReason.Crossfade
    } else {
        PrepareNextPlaybackReason.Gapless
    }
    val prepareWindowSeconds = if (reason == PrepareNextPlaybackReason.Crossfade) {
        normalizedCrossfadeDurationSeconds(crossfadeDurationSeconds).toDouble()
    } else {
        gaplessPrepareWindowSeconds.coerceAtLeast(0.0)
    }
    return PrepareNextPlaybackPlan(
        shouldPrepare = duration - position <= prepareWindowSeconds,
        prepareWindowSeconds = prepareWindowSeconds,
        reason = reason,
    )
}

fun equalPowerFadeEnvelope(
    startBytes: Long,
    durationBytes: Long,
    fadeIn: Boolean,
    scale: Float = 1f,
    steps: Int = EqualPowerEnvelopeSteps,
): List<PlaybackFadeEnvelopePoint> {
    val safeScale = scale.coerceIn(0f, MaxPlaybackVolumeFactor)
    if (durationBytes <= 0L || steps <= 0) {
        return listOf(
            PlaybackFadeEnvelopePoint(
                positionBytes = startBytes.coerceAtLeast(0L),
                volume = if (fadeIn) safeScale else 0f,
            ),
        )
    }
    val safeStartBytes = startBytes.coerceAtLeast(0L)
    return (0..steps).map { step ->
        val t = step.toDouble() / steps.toDouble()
        val value = if (fadeIn) {
            sin(t * PI / 2.0)
        } else {
            cos(t * PI / 2.0)
        }.toFloat()
        PlaybackFadeEnvelopePoint(
            positionBytes = safeStartBytes + (durationBytes * t).toLong(),
            volume = value * safeScale,
        )
    }
}

const val MaxCrossfadeDurationSeconds = 12
const val EqualPowerEnvelopeSteps = 8
const val MaxPlaybackVolumeFactor = 4f
