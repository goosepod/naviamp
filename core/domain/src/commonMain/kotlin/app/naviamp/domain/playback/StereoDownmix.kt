package app.naviamp.domain.playback

import kotlin.math.abs

/** Output-major matrix consumed by the narrow native audio adapter. */
data class AudioMixingMatrix(
    val outputChannels: Int,
    val inputChannels: Int,
    val coefficients: List<Float>,
) {
    init {
        require(outputChannels > 0) { "An audio mixing matrix needs at least one output channel." }
        require(inputChannels > 0) { "An audio mixing matrix needs at least one input channel." }
        require(coefficients.size == outputChannels * inputChannels) {
            "Expected ${outputChannels * inputChannels} coefficients, got ${coefficients.size}."
        }
        require(coefficients.all(Float::isFinite)) { "Audio mixing coefficients must be finite." }
    }

    operator fun get(outputChannel: Int, inputChannel: Int): Float =
        coefficients[outputChannel * inputChannels + inputChannel]

    fun absoluteRowSum(outputChannel: Int): Float =
        (0 until inputChannels).sumOf { input -> abs(this[outputChannel, input]).toDouble() }.toFloat()
}

data class StereoDownmixPlan(
    val sourceChannels: Int,
    val sourceLayoutLabel: String,
    val outputChannels: Int,
    val matrix: AudioMixingMatrix?,
) {
    val isDownmixed: Boolean
        get() = matrix != null

    val diagnosticLabel: String
        get() = if (isDownmixed) {
            "Stereo downmix ($sourceLayoutLabel → stereo, peak-safe matrix)"
        } else {
            when (sourceChannels) {
                1 -> "Mono source expanded by stereo mixer"
                2 -> "Stereo passthrough"
                else -> "Channel layout unavailable"
            }
        }
}

/**
 * Plans a deterministic stereo fold-down using BASS' documented decoded-channel order.
 *
 * Center and paired surround channels enter at -3 dB, LFE and a single back-center channel at
 * -6 dB. Each output row is then normalized so fully correlated full-scale inputs cannot exceed
 * full scale before ReplayGain, EQ, or user volume are applied.
 */
fun planStereoDownmix(sourceChannels: Int): StereoDownmixPlan {
    val layout = stereoSourceLayoutLabel(sourceChannels)
    if (sourceChannels <= 2) {
        return StereoDownmixPlan(
            sourceChannels = sourceChannels,
            sourceLayoutLabel = layout,
            outputChannels = StereoOutputChannels,
            matrix = null,
        )
    }

    val left = FloatArray(sourceChannels)
    val right = FloatArray(sourceChannels)
    when (sourceChannels) {
        3 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[2] = CenterOrSurroundGain
        }
        4 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[3] = CenterOrSurroundGain
        }
        5 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[2] = CenterOrSurroundGain
            left[3] = CenterOrSurroundGain
            right[4] = CenterOrSurroundGain
        }
        6 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[2] = CenterOrSurroundGain
            left[3] = LfeOrSplitCenterGain
            right[3] = LfeOrSplitCenterGain
            left[4] = CenterOrSurroundGain
            right[5] = CenterOrSurroundGain
        }
        7 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[2] = CenterOrSurroundGain
            left[3] = LfeOrSplitCenterGain
            right[3] = LfeOrSplitCenterGain
            left[4] = LfeOrSplitCenterGain
            right[4] = LfeOrSplitCenterGain
            left[5] = CenterOrSurroundGain
            right[6] = CenterOrSurroundGain
        }
        8 -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            left[2] = CenterOrSurroundGain
            right[2] = CenterOrSurroundGain
            left[3] = LfeOrSplitCenterGain
            right[3] = LfeOrSplitCenterGain
            left[4] = CenterOrSurroundGain
            right[5] = CenterOrSurroundGain
            left[6] = CenterOrSurroundGain
            right[7] = CenterOrSurroundGain
        }
        else -> {
            left[0] = DirectChannelGain
            right[1] = DirectChannelGain
            for (input in 2 until sourceChannels) {
                left[input] = UnknownChannelGain
                right[input] = UnknownChannelGain
            }
        }
    }

    normalizePeakSafe(left)
    normalizePeakSafe(right)
    return StereoDownmixPlan(
        sourceChannels = sourceChannels,
        sourceLayoutLabel = layout,
        outputChannels = StereoOutputChannels,
        matrix = AudioMixingMatrix(
            outputChannels = StereoOutputChannels,
            inputChannels = sourceChannels,
            coefficients = left.toList() + right.toList(),
        ),
    )
}

fun stereoSourceLayoutLabel(channels: Int): String =
    when (channels) {
        1 -> "mono"
        2 -> "stereo"
        3 -> "3.0"
        4 -> "quad"
        5 -> "5.0"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        else -> if (channels > 0) "$channels-channel (unknown layout)" else "unknown"
    }

private fun normalizePeakSafe(row: FloatArray) {
    val sum = row.sumOf { abs(it).toDouble() }.toFloat()
    if (sum <= PeakSafeRowLimit) return
    val scale = PeakSafeRowLimit / sum
    row.indices.forEach { index -> row[index] *= scale }
}

const val StereoOutputChannels: Int = 2
private const val DirectChannelGain: Float = 1f
private const val CenterOrSurroundGain: Float = 0.70710677f
private const val LfeOrSplitCenterGain: Float = 0.5f
private const val UnknownChannelGain: Float = 0.5f
private const val PeakSafeRowLimit: Float = 1f
