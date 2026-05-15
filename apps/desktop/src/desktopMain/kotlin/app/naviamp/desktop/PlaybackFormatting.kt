package app.naviamp.desktop

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.waveform.playbackFraction

fun PlaybackProgress.label(effectiveDurationSeconds: Double?): String {
    val position = positionSeconds?.toTimeLabel() ?: "--:--"
    val duration = effectiveDurationSeconds?.toTimeLabel() ?: "--:--"
    return "$position / $duration"
}

fun PlaybackProgress.positionLabel(): String =
    positionSeconds?.toTimeLabel() ?: "--:--"

fun Double?.durationLabel(): String =
    this?.toTimeLabel() ?: "--:--"

fun PlaybackProgress.fraction(effectiveDurationSeconds: Double?): Double {
    return playbackFraction(positionSeconds, effectiveDurationSeconds)
}

fun PlaybackEngine.streamQuality(): StreamQuality =
    if (prefersOriginalStream) {
        StreamQuality.Original
    } else {
        StreamQuality.Transcoded(
            codec = AudioCodec.Mp3,
            bitrateKbps = 192,
        )
    }

private fun Double.toTimeLabel(): String {
    val totalSeconds = toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
