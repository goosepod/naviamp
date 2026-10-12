package app.naviamp.domain.radio

import app.naviamp.domain.settings.PlaybackSettings

/** Serialized legacy preference keys remain compatible with settings export/import and sync. */
enum class RadioSimilarityMethod { SonicSimilarity, InstantMix }

fun PlaybackSettings.radioSimilarityMethod(): RadioSimilarityMethod =
    if (sonicSimilarityEnabled) RadioSimilarityMethod.SonicSimilarity else RadioSimilarityMethod.InstantMix

fun PlaybackSettings.withRadioSimilarityMethod(method: RadioSimilarityMethod): PlaybackSettings = copy(
    sonicSimilarityEnabled = method == RadioSimilarityMethod.SonicSimilarity,
    sonicSimilarityPreferenceConfigured = true,
)
