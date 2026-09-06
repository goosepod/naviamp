package app.naviamp.domain.playback

import kotlin.math.abs

/** Parameters shared by native equalizer implementations; frequencies are never retuned. */
data class EqualizerBandPlan(val frequencyHz: Float, val gainDb: Float, val bandwidthOctaves: Float = 1.5f)

fun planEqualizer(bandsDb: List<Float>, sampleRateHz: Int): List<EqualizerBandPlan> {
    require(sampleRateHz > 0)
    return EqualizerBandFrequencies.mapIndexedNotNull { index, frequency ->
        val requested = bandsDb.getOrNull(index) ?: 0f
        val gain = if (requested.isFinite()) requested.coerceIn(MinEqualizerGainDb, MaxEqualizerGainDb) else 0f
        if (abs(gain) < 0.05f || frequency >= sampleRateHz / 2f) null
        else EqualizerBandPlan(frequency.toFloat(), gain)
    }
}

/** ABI-neutral triples: center frequency, bandwidth in octaves, gain in dB. */
fun List<EqualizerBandPlan>.toNativeEqualizerParameters(): FloatArray =
    flatMap { listOf(it.frequencyHz, it.bandwidthOctaves, it.gainDb) }.toFloatArray()
