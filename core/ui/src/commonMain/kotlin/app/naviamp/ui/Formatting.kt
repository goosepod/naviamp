package app.naviamp.ui

import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.waveform.playbackFraction
import kotlin.math.abs
import kotlin.math.round

fun Track.durationLabel(): String =
    durationSeconds?.durationLabel() ?: "--:--"

fun Int.durationLabel(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun Double?.durationLabel(): String =
    this?.toTimeLabel() ?: "--:--"

fun PlaybackProgress.label(effectiveDurationSeconds: Double?): String {
    val position = positionSeconds.durationLabel()
    val duration = effectiveDurationSeconds.durationLabel()
    return "$position / $duration"
}

fun PlaybackProgress.positionLabel(): String =
    positionSeconds.durationLabel()

fun PlaybackProgress.fraction(effectiveDurationSeconds: Double?): Double =
    playbackFraction(positionSeconds, effectiveDurationSeconds)

fun Long.bytesLabel(): String {
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "${(this / gib).oneDecimalLabel()} GB"
        this >= mib -> "${(this / mib).oneDecimalLabel()} MB"
        this >= kib -> "${(this / kib).oneDecimalLabel()} KB"
        else -> "$this B"
    }
}

fun Long.storageBytesLabel(): String {
    val mib = 1024.0 * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "${(this / gib).oneDecimalLabel()} GB"
        this >= mib -> "${round(this / mib).toLong()} MB"
        else -> "$this B"
    }
}

fun StreamQuality.label(): String =
    when (this) {
        StreamQuality.Original -> "Original"
        is StreamQuality.Transcoded -> "${codec.name.uppercase()} transcode at $bitrateKbps kbps"
    }

fun AudioInfo.compactLabel(): String =
    buildList {
        val normalizedCodec = codec?.takeIf { it.isNotBlank() }
        normalizedCodec?.let(::add)
        if (!normalizedCodec.equals("FLAC", ignoreCase = true)) {
            bitrateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
        }
        samplingRateHz?.takeIf { it > 0 }?.let { add("${it / 1000.0} kHz") }
        bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
    }.joinToString("  ")

fun Int.ratingLabel(): String =
    "${coerceIn(1, 5)} / 5"

fun Double.twoDecimalLabel(): String =
    round(this * 100.0).div(100.0).toString()

fun Double.oneDecimalLabel(): String =
    fixedOneDecimalLabel()

internal fun Float.signedOneDecimalLabel(): String {
    val roundedTenths = round(this * 10f).toInt()
    val magnitude = abs(roundedTenths)
    val sign = if (roundedTenths >= 0) "+" else "-"
    return "$sign${magnitude / 10}.${magnitude % 10}"
}

internal fun Int.signedLabel(): String =
    if (this >= 0) "+$this" else toString()

fun Iterable<NaviampDownloadedTrackUi>.totalDownloadBytes(): Long =
    sumOf { download -> download.sizeBytes.coerceAtLeast(0L) }

fun Double.sixDecimalLabel(): String =
    round(this * 1_000_000.0).div(1_000_000.0).toString()

fun List<Track>.totalDurationLabel(): String {
    val totalSeconds = mapNotNull { it.durationSeconds }.sum()
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val remainingMinutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "$minutes minutes"
}

private fun Double.toTimeLabel(): String {
    val totalSeconds = toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Double.fixedOneDecimalLabel(): String {
    if (!isFinite()) return toString()
    val roundedTenths = round(this * 10.0).toLong()
    val magnitude = abs(roundedTenths)
    val sign = if (roundedTenths < 0) "-" else ""
    return "$sign${magnitude / 10}.${magnitude % 10}"
}
