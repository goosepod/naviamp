package app.naviamp.app

import app.naviamp.domain.StreamQuality

/** Container/encoding pairs qualified by the initial original-byte route. */
enum class NaviampUpnpAudioFormat(val lossless: Boolean, internal val mimeTypes: Set<String>) {
    Flac(true, setOf("audio/flac", "audio/x-flac")),
    Mp3(false, setOf("audio/mpeg", "audio/mp3")),
}

data class NaviampUpnpSourceFormat(
    val format: NaviampUpnpAudioFormat,
    val contentType: String,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
)

/** An advertisement is media-type evidence, not a verified hardware sample-format limit. */
data class NaviampUpnpSinkProtocol(
    val protocol: String,
    val network: String,
    val contentType: String,
    val additionalInfo: String,
)

/** Bounded parsing of ConnectionManager.GetProtocolInfo's Sink value. No XML or native I/O. */
fun parseNaviampUpnpSinkProtocols(value: String): List<NaviampUpnpSinkProtocol> {
    if (value.length > 65_536 || value.any { it.code < 32 && it != '\t' }) return emptyList()
    val entries = value.split(',', limit = 1_025)
    if (entries.size > 1_024) return emptyList()
    return entries.mapNotNull { entry ->
        val fields = entry.trim().split(':', limit = 4).map(String::trim)
        if (fields.size != 4 || fields.any(String::isEmpty)) null
        else NaviampUpnpSinkProtocol(fields[0], fields[1], fields[2].lowercase(), fields[3])
    }
}

enum class NaviampUpnpMediaRejection {
    InvalidSourceMetadata,
    SourceFormatMismatch,
    NoCompatibleOriginal,
    RequiresProfileVerification,
}

sealed interface NaviampUpnpMediaPlan {
    /** No conversion is authorized by this plan. Unknown sample parameters stay unknown. */
    data class Original(
        val contentType: String,
        val source: NaviampUpnpSourceFormat,
    ) : NaviampUpnpMediaPlan {
        val quality: StreamQuality get() = StreamQuality.Original
    }

    data class Rejected(val reason: NaviampUpnpMediaRejection) : NaviampUpnpMediaPlan
}

/** Shared route policy. In particular, this must never inherit Cast's MP3 transcode fallback. */
fun planNaviampUpnpMedia(
    source: NaviampUpnpSourceFormat,
    sinks: List<NaviampUpnpSinkProtocol>,
): NaviampUpnpMediaPlan {
    if (listOf(source.bitDepth, source.sampleRateHz, source.channelCount).any { it != null && it <= 0 }) {
        return NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.InvalidSourceMetadata)
    }
    val mime = source.contentType.trim().lowercase()
    if (mime !in source.format.mimeTypes) {
        return NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.SourceFormatMismatch)
    }
    val matching = sinks.filter {
        it.protocol.equals("http-get", ignoreCase = true) && it.network == "*" &&
            it.contentType.lowercase() in source.format.mimeTypes
    }
    // A wildcard MIME, profile name or vendor flag alone does not qualify an encoding/profile.
    // DLNA profile matching and receiver-specific sample-format limits need separate evidence.
    val compatible = matching.firstOrNull { it.additionalInfo == "*" }
    return when {
        compatible != null -> NaviampUpnpMediaPlan.Original(compatible.contentType.lowercase(), source)
        matching.isNotEmpty() -> NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.RequiresProfileVerification)
        else -> NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.NoCompatibleOriginal)
    }
}
