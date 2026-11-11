package app.naviamp.domain.radio

import app.naviamp.domain.AudioInfo
import app.naviamp.domain.playback.PlaybackStreamMetadata

/** Advertised properties of the active stream, never the library transcode preference. */
fun PlaybackStreamMetadata.internetRadioAudioInfo(): AudioInfo {
    fun property(key: String) = properties.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
        ?.value?.trim()?.takeIf(String::isNotBlank)
    val contentType = property("content-type")?.substringBefore(';')?.trim()?.lowercase()
    val codec = when (contentType) {
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/aac", "audio/aacp", "audio/x-aac" -> "AAC"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/opus" -> "OPUS"
        "audio/ogg", "application/ogg" -> "OGG"
        else -> null
    }
    val iceAudio = property("ice-audio-info").orEmpty().split(';').mapNotNull { field ->
        val split = field.indexOf('=')
        if (split < 1) null else field.take(split).trim().lowercase() to field.drop(split + 1).trim()
    }.toMap()
    fun String?.positiveInt() = this?.toIntOrNull()?.takeIf { it > 0 }
    val bitrate = property("icy-br").positiveInt() ?: iceAudio["bitrate"].positiveInt()
    return AudioInfo(codec = codec, bitrateKbps = bitrate, contentType = contentType,
        bitDepth = null, samplingRateHz = iceAudio["samplerate"].positiveInt())
}
