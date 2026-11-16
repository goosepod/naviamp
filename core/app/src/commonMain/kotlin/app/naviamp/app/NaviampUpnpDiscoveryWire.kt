package app.naviamp.app

const val NAVIAMP_UPNP_MULTICAST_HOST = "239.255.255.250"
const val NAVIAMP_UPNP_MULTICAST_PORT = 1900

const val NAVIAMP_UPNP_RENDERER_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"

sealed interface NaviampUpnpAdvertisement {
    val udn: String
    val sourceHost: String
    data class Available(override val udn: String, val location: String, val maxAgeSeconds: Long, override val sourceHost: String) : NaviampUpnpAdvertisement
    data class Departed(override val udn: String, override val sourceHost: String) : NaviampUpnpAdvertisement
}

fun naviampUpnpSearchDatagram(maxWaitSeconds: Int = 2): String {
    require(maxWaitSeconds in 1..5)
    return "M-SEARCH * HTTP/1.1\r\nHOST: $NAVIAMP_UPNP_MULTICAST_HOST:$NAVIAMP_UPNP_MULTICAST_PORT\r\nMAN: \"ssdp:discover\"\r\nMX: $maxWaitSeconds\r\nST: $NAVIAMP_UPNP_RENDERER_TYPE\r\n\r\n"
}

/** The socket adapter supplies the actual packet source address, never a device-supplied header. */
fun parseNaviampUpnpAdvertisement(datagram: String, sourceHost: String): NaviampUpnpAdvertisement {
    upnpCheck(datagram.length <= 16_384 && datagram.endsWith("\r\n\r\n"))
    val lines = datagram.removeSuffix("\r\n\r\n").split("\r\n").dropLastWhile(String::isEmpty)
    upnpCheck(lines.size in 2..65)
    val notify = lines[0] == "NOTIFY * HTTP/1.1"
    upnpCheck(notify || lines[0] == "HTTP/1.1 200 OK")
    val headers = mutableMapOf<String, String>()
    for (line in lines.drop(1)) {
        upnpCheck(line.isNotEmpty() && !line[0].isWhitespace() && line.none { it.code < 32 && it != '\t' })
        val colon = line.indexOf(':')
        upnpCheck(colon > 0)
        val key = line.substring(0, colon).lowercase()
        upnpCheck(key.all { it in 'a'..'z' || it in '0'..'9' || it in "!#$%&'*+-.^_`|~" } && key !in headers)
        headers[key] = line.substring(colon + 1).trim()
    }
    val target = headers[if (notify) "nt" else "st"]
    upnpCheck(target == NAVIAMP_UPNP_RENDERER_TYPE)
    val usn = headers["usn"] ?: throw NaviampUpnpProtocolException()
    val udn = usn.substringBefore("::")
    upnpCheck(udn.length <= 256 && Regex("uuid:[A-Za-z0-9-]+").matches(udn) && usn == "$udn::$target")
    if (notify && headers["nts"] == "ssdp:byebye") return NaviampUpnpAdvertisement.Departed(udn, sourceHost)
    if (notify) upnpCheck(headers["nts"] == "ssdp:alive")
    val location = headers["location"] ?: throw NaviampUpnpProtocolException()
    val url = checkedUpnpUrl(location)
    upnpCheck(url.host.removeSurrounding("[", "]").equals(sourceHost.removeSurrounding("[", "]"), true))
    val cache = headers["cache-control"] ?: throw NaviampUpnpProtocolException()
    val matches = Regex("(?:^|,)\\s*max-age\\s*=\\s*([0-9]+)\\s*(?=,|$)", RegexOption.IGNORE_CASE).findAll(cache).toList()
    upnpCheck(matches.size == 1)
    val seconds = matches.single().groupValues[1].toLongOrNull() ?: throw NaviampUpnpProtocolException()
    upnpCheck(seconds > 0)
    return NaviampUpnpAdvertisement.Available(udn, url.toString(), seconds.coerceAtMost(86_400), sourceHost)
}

enum class NaviampUpnpServiceKind { AVTransport, ConnectionManager, RenderingControl }
data class NaviampUpnpService(val kind: NaviampUpnpServiceKind, val type: String, val controlUrl: String, val eventUrl: String, val descriptionUrl: String) {
    init { upnpCheck(Regex("urn:schemas-upnp-org:service:$kind:[1-9][0-9]*").matches(type)) }
}
data class NaviampUpnpRendererDescription(val udn: String, val name: String, val services: Map<NaviampUpnpServiceKind, NaviampUpnpService>)

fun parseNaviampUpnpRendererDescription(xml: String, descriptionUrl: String, expectedUdn: String): NaviampUpnpRendererDescription {
    checkedUpnpUrl(descriptionUrl)
    val root = parseUpnpXml(xml)
    upnpCheck(root.namespace == UPNP_DEVICE_NS && root.name == "root")
    val base = root.optional(UPNP_DEVICE_NS, "URLBase")?.value()?.trim()?.let { resolveUpnpUrl(descriptionUrl, it) } ?: descriptionUrl
    val devices = mutableListOf<UpnpXmlNode>()
    fun visit(node: UpnpXmlNode) {
        devices += node
        node.optional(UPNP_DEVICE_NS, "deviceList")?.elements(UPNP_DEVICE_NS, "device")?.forEach(::visit)
    }
    visit(root.required(UPNP_DEVICE_NS, "device"))
    val device = devices.filter { it.required(UPNP_DEVICE_NS, "UDN").value().trim() == expectedUdn }.singleOrNull()
        ?: throw NaviampUpnpProtocolException()
    upnpCheck(Regex("urn:schemas-upnp-org:device:MediaRenderer:[1-9][0-9]*").matches(device.required(UPNP_DEVICE_NS, "deviceType").value().trim()))
    val name = device.required(UPNP_DEVICE_NS, "friendlyName").value().trim()
    upnpCheck(name.isNotBlank() && name.length <= 256)
    val services = mutableMapOf<NaviampUpnpServiceKind, NaviampUpnpService>()
    for (node in device.required(UPNP_DEVICE_NS, "serviceList").elements(UPNP_DEVICE_NS, "service")) {
        val type = node.required(UPNP_DEVICE_NS, "serviceType").value().trim()
        val match = Regex("urn:schemas-upnp-org:service:(AVTransport|ConnectionManager|RenderingControl):[1-9][0-9]*").matchEntire(type) ?: continue
        val kind = NaviampUpnpServiceKind.valueOf(match.groupValues[1])
        upnpCheck(kind !in services)
        fun endpoint(name: String) = resolveUpnpUrl(base, node.required(UPNP_DEVICE_NS, name).value().trim())
        services[kind] = NaviampUpnpService(kind, type, endpoint("controlURL"), endpoint("eventSubURL"), endpoint("SCPDURL"))
    }
    upnpCheck(NaviampUpnpServiceKind.AVTransport in services && NaviampUpnpServiceKind.ConnectionManager in services)
    return NaviampUpnpRendererDescription(expectedUdn, name, services)
}

internal fun parseUpnpActions(xml: String): Set<String> {
    val root = parseUpnpXml(xml)
    upnpCheck(root.name == "scpd" && root.namespace == UPNP_SERVICE_NS)
    val names = root.required(UPNP_SERVICE_NS, "actionList").elements(UPNP_SERVICE_NS, "action").map {
        it.required(UPNP_SERVICE_NS, "name").value().trim().also { name -> upnpCheck(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}").matches(name)) }
    }
    upnpCheck(names.size == names.toSet().size)
    return names.toSet()
}
