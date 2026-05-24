package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.streamQualityForNetwork
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

fun PlaybackSettings.streamQuality(playbackEngine: PlaybackEngine): StreamQuality =
    if (playbackEngine.prefersOriginalStream) {
        streamQualityForNetwork(isMobileData = false)
    } else {
        copy(wifiStreamingQuality = wifiStreamingQuality.copy(mode = StreamQualityMode.Transcode))
            .streamQualityForNetwork(isMobileData = false)
    }

private fun Double.toTimeLabel(): String {
    val totalSeconds = toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
