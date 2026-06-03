package app.naviamp.domain.playback

import kotlin.math.PI
import kotlin.math.cos
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
