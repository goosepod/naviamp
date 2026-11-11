package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.sanitizedPlaybackDiagnosticUrl
import app.naviamp.domain.radio.internetRadioAudioInfo

enum class NaviampStreamDetailLabel {
    Station, Broadcast, Audio, Metadata, Name, StreamUrl, HomePageUrl,
    Title, Artist, Album, Genre, Description, ArtworkUrl, Codec, Bitrate, SampleRate, Channels, ContentType,
}

data class NaviampStreamDetailRow(
    val value: String,
    val label: NaviampStreamDetailLabel? = null,
    val metadataKey: String? = null,
    val embeddedPicture: Boolean = false,
)

data class NaviampStreamDetailSection(
    val label: NaviampStreamDetailLabel,
    val rows: List<NaviampStreamDetailRow>,
)

/** Rebuilt from the current snapshot: no station/song values survive missing metadata. */
fun InternetRadioStation.streamDetails(metadata: PlaybackStreamMetadata): List<NaviampStreamDetailSection> {
    val consumed = mutableSetOf<String>()
    fun property(vararg names: String): String? {
        val keys = names.map(String::lowercase)
        val matches = metadata.properties.entries.filter { it.key.lowercase() in keys }
        consumed += matches.map { it.key }
        return matches.firstNotNullOfOrNull { it.value.trim().takeIf(String::isNotBlank) }
    }
    fun safe(value: String): String =
        if (value.startsWith("https://", true) || value.startsWith("http://", true))
            value.sanitizedPlaybackDiagnosticUrl() else value
    fun row(label: NaviampStreamDetailLabel, value: String?) =
        value?.trim()?.takeIf(String::isNotBlank)?.let { NaviampStreamDetailRow(safe(it), label) }
    fun section(label: NaviampStreamDetailLabel, rows: List<NaviampStreamDetailRow>) =
        NaviampStreamDetailSection(label, rows).takeIf { rows.isNotEmpty() }
    val stationRows = listOfNotNull(
        row(NaviampStreamDetailLabel.Name, name),
        row(NaviampStreamDetailLabel.StreamUrl, streamUrl),
        row(NaviampStreamDetailLabel.HomePageUrl, homePageUrl ?: property("icy-url")),
        row(NaviampStreamDetailLabel.Name, property("icy-name"))?.takeIf { it.value != name },
        row(NaviampStreamDetailLabel.Description, property("icy-description")),
        row(NaviampStreamDetailLabel.Genre, property("icy-genre")),
    )
    val broadcastRows = listOfNotNull(
        row(NaviampStreamDetailLabel.Title, metadata.title),
        row(NaviampStreamDetailLabel.Artist, property("artist")),
        row(NaviampStreamDetailLabel.Album, property("album")),
        row(NaviampStreamDetailLabel.Genre, property("genre")),
        row(NaviampStreamDetailLabel.Description, property("description")),
        row(NaviampStreamDetailLabel.ArtworkUrl, streamMetadataArtworkUrl(metadata.properties)),
    )
    property("StreamTitle", "icy-title", "title")
    val info = metadata.internetRadioAudioInfo()
    val audioHeader = metadata.properties.entries.firstOrNull { it.key.equals("ice-audio-info", true) }?.value.orEmpty()
    val channels = audioHeader.split(';').firstNotNullOfOrNull { field ->
        val key = field.substringBefore('=').trim().lowercase()
        field.substringAfter('=', "").trim().toIntOrNull()?.takeIf {
            key in setOf("channels", "ice-channels") && it > 0
        }
    }
    val audioRows = listOfNotNull(
        row(NaviampStreamDetailLabel.Codec, info.codec),
        row(NaviampStreamDetailLabel.Bitrate, info.bitrateKbps?.toString()),
        row(NaviampStreamDetailLabel.SampleRate, info.samplingRateHz?.toString()),
        row(NaviampStreamDetailLabel.ContentType, info.contentType),
        row(NaviampStreamDetailLabel.Channels, channels?.toString()),
    )
    property("icy-br", "content-type")
    // Preserve the original audio-info header below, including channels and encoder-specific fields.
    val otherRows = metadata.properties.entries.sortedBy { it.key.lowercase() }.mapNotNull { (key, value) ->
        val normalized = key.lowercase().filter(Char::isLetterOrDigit)
        if (key in consumed || value.isBlank() || normalized in transportKeys ||
            sensitiveFragments.any { it in normalized }) return@mapNotNull null
        val picture = normalized == "metadatablockpicture" ||
            (normalized == "coverart" && !value.startsWith("http://", true) && !value.startsWith("https://", true))
        NaviampStreamDetailRow(value = if (picture) "" else safe(value.trim()), metadataKey = key,
            embeddedPicture = picture)
    }
    return listOfNotNull(
        section(NaviampStreamDetailLabel.Station, stationRows),
        section(NaviampStreamDetailLabel.Broadcast, broadcastRows),
        section(NaviampStreamDetailLabel.Audio, audioRows),
        section(NaviampStreamDetailLabel.Metadata, otherRows),
    )
}

private val sensitiveFragments = listOf("authorization", "authenticate", "cookie", "password", "passwd", "token", "apikey", "secret")
private val transportKeys = setOf("server", "connection", "cachecontrol", "expires", "pragma", "vary",
    "transferencoding", "contentlength", "accesscontrolalloworigin", "accesscontrolallowheaders", "icymetaint")
