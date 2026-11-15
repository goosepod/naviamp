package app.naviamp.app

data class NaviampUpnpSubscription(val sid: String, val renewAfterMillis: Long)

/** Wire operations only. The common session owner must schedule renewal and cancel on displacement. */
class NaviampUpnpEventSubscriptionClient(private val transport: NaviampUpnpHttpTransport, private val eventUrl: String) {
    init { checkedUpnpUrl(eventUrl) }
    suspend fun subscribe(callbackUrl: String): NaviampUpnpSubscription {
        checkedUpnpUrl(callbackUrl)
        return read(transport.exchange(NaviampUpnpHttpRequest("SUBSCRIBE", eventUrl,
            mapOf("CALLBACK" to "<$callbackUrl>", "NT" to "upnp:event", "TIMEOUT" to "Second-300"))))
    }
    suspend fun renew(subscription: NaviampUpnpSubscription): NaviampUpnpSubscription {
        checkedSid(subscription.sid)
        val renewed = read(transport.exchange(NaviampUpnpHttpRequest("SUBSCRIBE", eventUrl,
            mapOf("SID" to subscription.sid, "TIMEOUT" to "Second-300"))))
        upnpCheck(renewed.sid == subscription.sid)
        return renewed
    }
    suspend fun unsubscribe(subscription: NaviampUpnpSubscription) {
        checkedSid(subscription.sid)
        val reply = transport.exchange(NaviampUpnpHttpRequest("UNSUBSCRIBE", eventUrl, mapOf("SID" to subscription.sid)))
        upnpCheck(reply.status == 200)
    }
    private fun read(reply: NaviampUpnpHttpResponse): NaviampUpnpSubscription {
        upnpCheck(reply.status == 200)
        val sid = reply.header("SID") ?: throw NaviampUpnpProtocolException()
        checkedSid(sid)
        val timeout = reply.header("TIMEOUT") ?: throw NaviampUpnpProtocolException()
        val seconds = if (timeout.equals("Second-infinite", true)) 3_600L else {
            upnpCheck(timeout.startsWith("Second-", true))
            timeout.substringAfter('-').toLongOrNull()?.takeIf { it > 0 } ?: throw NaviampUpnpProtocolException()
        }
        // Renew well before expiration, even when a renderer grants an infinite subscription.
        return NaviampUpnpSubscription(sid, (seconds.coerceAtMost(3_600) * 1_000 / 2).coerceAtLeast(1))
    }
}

private fun checkedSid(sid: String) = upnpCheck(sid.length <= 256 && Regex("uuid:[A-Za-z0-9-]+").matches(sid))

data class NaviampUpnpTransportEvent(
    val state: NaviampUpnpTransportState?,
    val transportUri: String?,
    val trackUri: String?,
)
data class NaviampUpnpAcceptedEvent(val sequence: Long, val needsResync: Boolean, val transport: NaviampUpnpTransportEvent)

/** Validate subscription identity and modulo-32-bit event order before publishing any state. */
class NaviampUpnpEventGate(private val selectionId: Long, private val sid: String) {
    init { checkedSid(sid) }
    private var lastSequence: Long? = null
    fun accept(selectionId: Long, headers: Map<String, String>, body: String): NaviampUpnpAcceptedEvent? {
        if (this.selectionId != selectionId) return null
        val message = NaviampUpnpHttpResponse(200, headers, body)
        if (message.header("SID") != sid) return null
        upnpCheck(message.header("NT") == "upnp:event" && message.header("NTS") == "upnp:propchange")
        upnpCheck(message.header("Content-Type")?.substringBefore(';')?.trim()?.equals("text/xml", true) == true)
        val sequence = message.header("SEQ")?.toLongOrNull()?.takeIf { it in 0..0xFFFF_FFFFL } ?: throw NaviampUpnpProtocolException()
        val last = lastSequence
        if (last != null) {
            val distance = (sequence - last) and 0xFFFF_FFFFL
            if (sequence == 0L || distance == 0L || distance > 0x7FFF_FFFFL) return null
        }
        val root = parseUpnpXml(body)
        upnpCheck(root.namespace == UPNP_EVENT_NS && root.name == "propertyset")
        val changes = root.elements(UPNP_EVENT_NS, "property").flatMap { property ->
            upnpCheck(property.children.size == 1)
            property.children.filter { it.name == "LastChange" && it.namespace.isEmpty() }
        }
        upnpCheck(changes.size == 1)
        val change = parseUpnpXml(changes.single().value())
        val avt = "urn:schemas-upnp-org:metadata-1-0/AVT/"
        upnpCheck(change.name == "Event" && change.namespace == avt)
        val instances = change.elements(avt, "InstanceID").filter { it.attributes["" to "val"] == "0" }
        upnpCheck(instances.size == 1)
        val instance = instances.single()
        fun field(name: String) = instance.optional(avt, name)?.let { it.attributes["" to "val"] ?: throw NaviampUpnpProtocolException() }
        val snapshot = NaviampUpnpTransportEvent(field("TransportState")?.let(::parseUpnpTransportState), field("AVTransportURI"), field("CurrentTrackURI"))
        val expected = last?.let { if (it == 0xFFFF_FFFFL) 1L else it + 1 }
        lastSequence = sequence // A malformed body never consumes a valid sequence number.
        return NaviampUpnpAcceptedEvent(sequence, if (expected == null) sequence != 0L else sequence != expected, snapshot)
    }
}
