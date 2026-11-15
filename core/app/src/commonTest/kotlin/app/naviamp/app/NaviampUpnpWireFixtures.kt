package app.naviamp.app

internal fun upnpFixtureDescription(): String = """
<root xmlns="urn:schemas-upnp-org:device-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><device>
<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>Fixture &amp; renderer</friendlyName><UDN>uuid:fixture</UDN><serviceList>
${NaviampUpnpServiceKind.entries.joinToString("") { kind -> """<service><serviceType>urn:schemas-upnp-org:service:$kind:1</serviceType><serviceId>urn:upnp-org:serviceId:$kind</serviceId><controlURL>../control/$kind</controlURL><eventSubURL>../events/$kind</eventSubURL><SCPDURL>../service/$kind.xml</SCPDURL></service>""" }}
</serviceList></device></root>
""".trimIndent()

internal val upnpFixtureActions = mapOf(
    NaviampUpnpServiceKind.AVTransport to listOf("SetAVTransportURI", "SetNextAVTransportURI", "Play", "Pause", "Stop", "Seek", "GetTransportInfo", "GetPositionInfo"),
    NaviampUpnpServiceKind.ConnectionManager to listOf("GetProtocolInfo"),
    NaviampUpnpServiceKind.RenderingControl to listOf("SetVolume"),
)

internal fun upnpFixtureScpd(kind: NaviampUpnpServiceKind, nextSupported: Boolean = true): String =
    "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><actionList>" +
        upnpFixtureActions.getValue(kind).filter { nextSupported || it != "SetNextAVTransportURI" }.joinToString("") { "<action><name>$it</name></action>" } + "</actionList></scpd>"

internal fun upnpFixtureSoap(kind: NaviampUpnpServiceKind, action: String, fields: Map<String, String> = emptyMap()): String =
    "<env:Envelope xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\"><env:Body><reply:${action}Response xmlns:reply=\"urn:schemas-upnp-org:service:$kind:1\">" +
        fields.entries.joinToString("") { (name, value) -> "<$name>${upnpXmlText(value)}</$name>" } + "</reply:${action}Response></env:Body></env:Envelope>"

internal fun upnpFixtureFault(): String = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode><errorDescription>Transition not available</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

internal fun upnpFixtureEvent(uri: String, state: String = "PLAYING"): String {
    val change = "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT/\"><InstanceID val=\"0\"><TransportState val=\"$state\"/><CurrentTrackURI val=\"${upnpXmlText(uri)}\"/></InstanceID></Event>"
    return "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\"><e:property><LastChange>${upnpXmlText(change)}</LastChange></e:property></e:propertyset>"
}

internal fun upnpFixtureHeaders(sequence: Long, sid: String = "uuid:subscription") = mapOf("SID" to sid, "SEQ" to sequence.toString(), "NT" to "upnp:event", "NTS" to "upnp:propchange", "Content-Type" to "text/xml; charset=utf-8")

internal class UpnpFixtureTransport(var nextSupported: Boolean = true) : NaviampUpnpHttpTransport {
    val requests = mutableListOf<NaviampUpnpHttpRequest>()
    var failNext = false
    var wrongResponse = false
    override suspend fun exchange(request: NaviampUpnpHttpRequest): NaviampUpnpHttpResponse {
        requests += request
        if (request.method == "GET") {
            return NaviampUpnpHttpResponse(200, emptyMap(), if (request.url.endsWith("device.xml")) upnpFixtureDescription() else
                upnpFixtureScpd(NaviampUpnpServiceKind.entries.single { request.url.endsWith("$it.xml") }, nextSupported))
        }
        if (request.method == "SUBSCRIBE") return NaviampUpnpHttpResponse(200, mapOf("sid" to "uuid:subscription", "timeout" to "Second-300"), "")
        if (request.method == "UNSUBSCRIBE") return NaviampUpnpHttpResponse(200, emptyMap(), "")
        val action = request.headers.getValue("SOAPACTION").trim('"').substringAfter('#')
        val kind = NaviampUpnpServiceKind.entries.single { request.url.endsWith("/$it") }
        if (failNext && action == "SetNextAVTransportURI") return NaviampUpnpHttpResponse(500, emptyMap(), upnpFixtureFault())
        val fields = when (action) {
            "GetProtocolInfo" -> mapOf("Source" to "", "Sink" to "http-get:*:audio/flac:*")
            "GetTransportInfo" -> mapOf("CurrentTransportState" to "STOPPED")
            "GetPositionInfo" -> mapOf("TrackURI" to "http://127.0.0.1/media/a", "RelTime" to "00:00:01.250", "TrackDuration" to "NOT_IMPLEMENTED")
            else -> emptyMap()
        }
        return NaviampUpnpHttpResponse(200, emptyMap(), upnpFixtureSoap(kind, if (wrongResponse) "Wrong" else action, fields))
    }
}
