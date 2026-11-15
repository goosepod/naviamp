package app.naviamp.app

class NaviampUpnpActionFault(val code: Int, val description: String) : Exception("UPnP action fault ($code)")
class NaviampUpnpUnsupportedAction(val action: String) : Exception("UPnP action is not advertised")

data class NaviampUpnpRendererCapabilities(
    val actions: Map<NaviampUpnpServiceKind, Set<String>>,
    val sinkProtocols: List<NaviampUpnpSinkProtocol>,
) {
    val supportsNext get() = "SetNextAVTransportURI" in actions[NaviampUpnpServiceKind.AVTransport].orEmpty()
    val supportsVolume get() = "SetVolume" in actions[NaviampUpnpServiceKind.RenderingControl].orEmpty()
}

data class NaviampUpnpRendererMedia(
    val url: String,
    val plan: NaviampUpnpMediaPlan.Original,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMillis: Long? = null,
)

enum class NaviampUpnpTransportState { STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING, NO_MEDIA_PRESENT, RECORDING, PAUSED_RECORDING }
data class NaviampUpnpPosition(val trackUri: String, val positionMillis: Long?, val durationMillis: Long?)

/** Shared UPnP protocol client. Action acknowledgement never grants playback authority. */
class NaviampUpnpRendererClient(
    private val transport: NaviampUpnpHttpTransport,
    val renderer: NaviampUpnpRendererDescription,
) {
    private var capabilities: NaviampUpnpRendererCapabilities? = null

    suspend fun probe(): NaviampUpnpRendererCapabilities {
        val actions = renderer.services.mapValues { (_, service) -> parseUpnpActions(get(service.descriptionUrl)) }
        val manager = renderer.services.getValue(NaviampUpnpServiceKind.ConnectionManager)
        if ("GetProtocolInfo" !in actions.getValue(manager.kind)) throw NaviampUpnpUnsupportedAction("GetProtocolInfo")
        val info = invoke(manager, "GetProtocolInfo", emptyMap())
        return NaviampUpnpRendererCapabilities(actions, parseNaviampUpnpSinkProtocols(info["Sink"] ?: throw NaviampUpnpProtocolException()))
            .also { capabilities = it }
    }

    suspend fun setCurrent(media: NaviampUpnpRendererMedia) = command("SetAVTransportURI", linkedMapOf("CurrentURI" to media.url, "CurrentURIMetaData" to upnpDidl(media)))
    suspend fun setNext(media: NaviampUpnpRendererMedia?) = command("SetNextAVTransportURI", linkedMapOf("NextURI" to (media?.url ?: ""), "NextURIMetaData" to (media?.let(::upnpDidl) ?: "")))
    suspend fun play() = command("Play", mapOf("Speed" to "1"))
    suspend fun pause() = command("Pause")
    suspend fun stop() = command("Stop")
    suspend fun seek(positionMillis: Long) {
        require(positionMillis >= 0)
        command("Seek", linkedMapOf("Unit" to "REL_TIME", "Target" to upnpTime(positionMillis)))
    }
    suspend fun setVolume(percent: Int) {
        require(percent in 0..100)
        action(NaviampUpnpServiceKind.RenderingControl, "SetVolume", linkedMapOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to percent.toString()))
    }
    suspend fun transportState(): NaviampUpnpTransportState {
        val value = command("GetTransportInfo")["CurrentTransportState"] ?: throw NaviampUpnpProtocolException()
        return parseUpnpTransportState(value)
    }
    suspend fun position(): NaviampUpnpPosition {
        val fields = command("GetPositionInfo")
        return NaviampUpnpPosition(fields["TrackURI"] ?: throw NaviampUpnpProtocolException(),
            parseUpnpTime(fields["RelTime"] ?: throw NaviampUpnpProtocolException()), parseUpnpTime(fields["TrackDuration"] ?: throw NaviampUpnpProtocolException()))
    }

    private suspend fun command(name: String, fields: Map<String, String> = emptyMap()): Map<String, String> =
        action(NaviampUpnpServiceKind.AVTransport, name, linkedMapOf("InstanceID" to "0") + fields)

    private suspend fun action(kind: NaviampUpnpServiceKind, name: String, fields: Map<String, String>): Map<String, String> {
        if (name !in capabilities?.actions?.get(kind).orEmpty()) throw NaviampUpnpUnsupportedAction(name)
        return invoke(renderer.services.getValue(kind), name, fields)
    }

    private suspend fun invoke(service: NaviampUpnpService, name: String, fields: Map<String, String>): Map<String, String> {
        val arguments = fields.entries.joinToString("") { (key, value) -> "<$key>${upnpXmlText(value)}</$key>" }
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"$UPNP_SOAP_NS\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$name xmlns:u=\"${service.type}\">$arguments</u:$name></s:Body></s:Envelope>"
        val reply = transport.exchange(NaviampUpnpHttpRequest("POST", service.controlUrl,
            mapOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"${service.type}#$name\""), body))
        val root = parseUpnpXml(reply.body)
        upnpCheck(root.namespace == UPNP_SOAP_NS && root.name == "Envelope")
        val payload = root.required(UPNP_SOAP_NS, "Body").children.singleOrNull() ?: throw NaviampUpnpProtocolException()
        if (payload.name == "Fault" && payload.namespace == UPNP_SOAP_NS) {
            val detail = payload.required("", "detail")
            val errorNs = "urn:schemas-upnp-org:control-1-0"
            val error = detail.required(errorNs, "UPnPError")
            val code = error.required(errorNs, "errorCode").value().trim().toIntOrNull() ?: throw NaviampUpnpProtocolException()
            throw NaviampUpnpActionFault(code, error.required(errorNs, "errorDescription").value())
        }
        upnpCheck(reply.status == 200 && payload.namespace == service.type && payload.name == "${name}Response")
        val result = mutableMapOf<String, String>()
        for (field in payload.children) {
            upnpCheck(field.namespace in setOf("", service.type) && field.name !in result)
            result[field.name] = field.value()
        }
        return result
    }

    private suspend fun get(url: String): String = transport.exchange(NaviampUpnpHttpRequest("GET", url)).also { upnpCheck(it.status == 200) }.body

    companion object {
        suspend fun describe(transport: NaviampUpnpHttpTransport, location: String, expectedUdn: String): NaviampUpnpRendererClient {
            checkedUpnpUrl(location)
            val reply = transport.exchange(NaviampUpnpHttpRequest("GET", location))
            upnpCheck(reply.status == 200)
            return NaviampUpnpRendererClient(transport, parseNaviampUpnpRendererDescription(reply.body, location, expectedUdn))
        }
    }
}

internal fun parseUpnpTransportState(value: String): NaviampUpnpTransportState =
    NaviampUpnpTransportState.entries.firstOrNull { it.name == value } ?: throw NaviampUpnpProtocolException()

internal fun parseUpnpTime(value: String): Long? {
    if (value == "NOT_IMPLEMENTED") return null
    val match = Regex("([0-9]{1,7}):([0-5][0-9]):([0-5][0-9])(?:\\.([0-9]{1,3}))?").matchEntire(value) ?: throw NaviampUpnpProtocolException()
    val (hours, minutes, seconds, fraction) = match.destructured
    return ((hours.toLong() * 60 + minutes.toInt()) * 60 + seconds.toInt()) * 1_000 + fraction.padEnd(3, '0').toInt()
}

internal fun upnpTime(millis: Long): String =
    "${(millis / 3_600_000).toString().padStart(2, '0')}:${((millis / 60_000) % 60).toString().padStart(2, '0')}:${((millis / 1_000) % 60).toString().padStart(2, '0')}.${(millis % 1_000).toString().padStart(3, '0')}"

internal fun upnpDidl(media: NaviampUpnpRendererMedia): String {
    checkedUpnpUrl(media.url)
    media.artworkUrl?.let(::checkedUpnpUrl)
    media.durationMillis?.let { require(it >= 0) }
    val source = media.plan.source
    val attributes = buildString {
        append(" protocolInfo=\"http-get:*:${upnpXmlText(media.plan.contentType)}:*\"")
        media.durationMillis?.let { append(" duration=\"${upnpTime(it)}\"") }
        source.bitDepth?.let { append(" bitsPerSample=\"$it\"") }
        source.sampleRateHz?.let { append(" sampleFrequency=\"$it\"") }
        source.channelCount?.let { append(" nrAudioChannels=\"$it\"") }
    }
    return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${upnpXmlText(media.title)}</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><upnp:artist>${upnpXmlText(media.artist)}</upnp:artist>" +
        (media.album?.let { "<upnp:album>${upnpXmlText(it)}</upnp:album>" } ?: "") +
        (media.artworkUrl?.let { "<upnp:albumArtURI>${upnpXmlText(it)}</upnp:albumArtURI>" } ?: "") +
        "<res$attributes>${upnpXmlText(media.url)}</res></item></DIDL-Lite>"
}
