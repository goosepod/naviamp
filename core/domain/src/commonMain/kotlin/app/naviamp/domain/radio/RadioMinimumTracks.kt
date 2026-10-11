package app.naviamp.domain.radio

const val DefaultRadioMinimumTracks = 10
const val MaxRadioMinimumTracks = 100
val RadioMinimumTrackOptions: List<Int> = (10..MaxRadioMinimumTracks step 10).toList()

/** Clamp imported values and round to the nearest selectable batch size. */
fun normalizedRadioMinimumTracks(value: Int): Int =
    ((value.coerceIn(10, MaxRadioMinimumTracks) + 5) / 10) * 10
