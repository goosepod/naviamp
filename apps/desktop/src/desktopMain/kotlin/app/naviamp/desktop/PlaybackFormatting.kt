package app.naviamp.desktop

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackProgress

fun Track.durationLabel(): String {
    val duration = durationSeconds ?: return "--:--"
    val minutes = duration / 60
    val seconds = duration % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun Track.favoriteGlyph(): String =
    if (favoritedAtIso8601 != null) "♥" else "♡"

fun Track.ratingGlyphs(): String {
    val rating = userRating?.coerceIn(0, 5) ?: 0
    return "★".repeat(rating) + "☆".repeat(5 - rating)
}

fun Track.compactFavoriteRatingLabel(): String? {
    val parts = listOfNotNull(
        favoritedAtIso8601?.let { "♥" },
        userRating?.takeIf { it in 1..5 }?.let { "$it★" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

fun Track.albumTitleWithYear(): String? =
    albumTitle?.let { title ->
        albumReleaseYear?.let { "$title ($it)" } ?: title
    }

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
    val position = positionSeconds ?: return 0.0
    val duration = effectiveDurationSeconds ?: return 0.0
    if (duration <= 0.0) return 0.0
    return (position / duration).coerceIn(0.0, 1.0)
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

fun Track.playbackAudioLabel(playbackEngineName: String): String? =
    if (playbackEngineName == "JLayer") {
        "MP3 transcode • 192 kbps"
    } else {
        audioInfo?.label()
    }

private fun AudioInfo.label(): String? {
    val parts = listOfNotNull(
        codec,
        bitrateKbps?.let { "$it kbps" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun Double.toTimeLabel(): String {
    val totalSeconds = toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
