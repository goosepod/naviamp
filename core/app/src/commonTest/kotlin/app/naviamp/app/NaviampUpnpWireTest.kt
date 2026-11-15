package app.naviamp.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampUpnpWireTest {
    private val location = "http://127.0.0.1:19002/desc/device.xml"

    @Test fun ssdpUsesPacketSourceAndRejectsAmbiguousHeadersOrOffDeviceLocations() {
        val response = "HTTP/1.1 200 OK\r\nST: $NAVIAMP_UPNP_RENDERER_TYPE\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\nLOCATION: $location\r\nCACHE-CONTROL: max-age=1800\r\n\r\n"
        val parsed = assertIs<NaviampUpnpAdvertisement.Available>(parseNaviampUpnpAdvertisement(response, "127.0.0.1"))
        assertEquals(1_800, parsed.maxAgeSeconds)
        assertEquals("uuid:fixture", parsed.udn)
        assertEquals(parsed, parseNaviampUpnpAdvertisement(response.replace("CACHE-CONTROL", "BOOTID.UPNP.ORG: 1\r\nCACHE-CONTROL"), "127.0.0.1"))
        assertTrue(naviampUpnpSearchDatagram().contains("MAN: \"ssdp:discover\"\r\n"))
        for (bad in listOf(response.replace("CACHE-CONTROL", "Location: $location\r\nCACHE-CONTROL"), response.replace("127.0.0.1", "example.com"), response.replace("max-age=1800", "max-age=0"), response + "body")) {
            assertFailsWith<NaviampUpnpProtocolException> { parseNaviampUpnpAdvertisement(bad, "127.0.0.1") }
        }
        val departed = "NOTIFY * HTTP/1.1\r\nNT: $NAVIAMP_UPNP_RENDERER_TYPE\r\nNTS: ssdp:byebye\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\n\r\n"
        assertEquals("127.0.0.1", assertIs<NaviampUpnpAdvertisement.Departed>(parseNaviampUpnpAdvertisement(departed, "127.0.0.1")).sourceHost)
    }

    @Test fun descriptionsResolvePathsAndPinIdentityNamespaceAndOrigin() {
        val description = parseNaviampUpnpRendererDescription(upnpFixtureDescription(), location, "uuid:fixture")
        assertEquals("Fixture & renderer", description.name)
        assertEquals("http://127.0.0.1:19002/control/AVTransport", description.services.getValue(NaviampUpnpServiceKind.AVTransport).controlUrl)
        assertEquals("http://127.0.0.1:19002/desc/device.xml?control=1", resolveUpnpUrl(location, "?control=1"))
        for (bad in listOf(upnpFixtureDescription().replace("uuid:fixture", "uuid:other"), upnpFixtureDescription().replace("../control/AVTransport", "http://example.com/control"), upnpFixtureDescription().replace(UPNP_DEVICE_NS, "urn:spoof"))) {
            assertFailsWith<NaviampUpnpProtocolException> { parseNaviampUpnpRendererDescription(bad, location, "uuid:fixture") }
        }
        for (bad in listOf("http://user:secret@127.0.0.1/device", "file:///tmp/device", "$location#fragment", "relative.xml", "http://127.0.0.1/x><http://example.com")) {
            assertFailsWith<NaviampUpnpProtocolException> { checkedUpnpUrl(bad) }
        }
    }

    @Test fun xmlRejectsDeclarationsEntitiesDepthAndTrailingDocuments() {
        val badXml = listOf("<!DOCTYPE root SYSTEM 'http://example.com/entity'><root/>", "<!DOCTYPE root [<!ENTITY x 'boom'>]><root>&x;</root>",
            "<root>&unknown;</root>", "<a/> <b/>", "<a>".repeat(33) + "</a>".repeat(33), "<a>" + "x".repeat(262_144) + "</a>", "<a><b></a>")
        for (bad in badXml) assertFailsWith<NaviampUpnpProtocolException> { parseUpnpXml(bad) }
        assertEquals("< & 😀", parseUpnpXml("<a>&lt; &amp; &#x1f600;</a>").value())
        assertFailsWith<NaviampUpnpProtocolException> { upnpXmlText("bad\u0000") }
        assertFailsWith<NaviampUpnpProtocolException> { upnpXmlText("\uD800") }
    }

    @Test fun capabilityProbeAndSoapAcceptanceAreDistinctFromObservedPlaying() = runTest {
        val transport = UpnpFixtureTransport()
        val client = NaviampUpnpRendererClient.describe(transport, location, "uuid:fixture")
        val caps = client.probe()
        assertTrue(caps.supportsNext)
        assertTrue(caps.supportsVolume)
        val plan = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(NaviampUpnpSourceFormat(NaviampUpnpAudioFormat.Flac, "audio/flac"), caps.sinkProtocols))
        client.setCurrent(NaviampUpnpRendererMedia("http://127.0.0.1/media?a=1&b=2", plan, "Title <& \"😀", "Artist"))
        assertEquals(NaviampUpnpTransportState.STOPPED, client.transportState())
        val load = transport.requests.single { it.headers["SOAPACTION"]?.contains("#SetAVTransportURI") == true }
        assertTrue(load.body.contains("&amp;b=2"))
        assertTrue(load.body.contains("&amp;lt;")) // DIDL is XML nested inside SOAP text.
        client.play(); client.pause(); client.seek(1_250); client.setVolume(50); client.stop()
        val position = client.position()
        assertEquals(1_250, position.positionMillis)
        assertNull(position.durationMillis)
        transport.failNext = true
        assertEquals(701, assertFailsWith<NaviampUpnpActionFault> { client.setNext(null) }.code)
        transport.wrongResponse = true
        assertFailsWith<NaviampUpnpProtocolException> { client.play() }
    }

    @Test fun missingOptionalNextActionIsNotInferredFromNameOrSuccessfulHttp() = runTest {
        val transport = UpnpFixtureTransport(nextSupported = false)
        val client = NaviampUpnpRendererClient.describe(transport, location, "uuid:fixture")
        assertFalse(client.probe().supportsNext)
        val before = transport.requests.size
        assertFailsWith<NaviampUpnpUnsupportedAction> { client.setNext(null) }
        assertEquals(before, transport.requests.size)
    }

    @Test fun subscriptionRenewalDoesNotSendFreshSubscriptionHeaders() = runTest {
        val transport = UpnpFixtureTransport()
        val client = NaviampUpnpEventSubscriptionClient(transport, "http://127.0.0.1:19002/events/AVTransport")
        val subscription = client.subscribe("http://127.0.0.1:19003/callback/token")
        assertEquals(150_000, subscription.renewAfterMillis)
        assertEquals(subscription, client.renew(subscription))
        assertNull(transport.requests.last().headers["NT"])
        assertNull(transport.requests.last().headers["CALLBACK"])
        client.unsubscribe(subscription)
        assertEquals("UNSUBSCRIBE", transport.requests.last().method)
    }

    @Test fun eventIdentityDuplicatesGapsAndMalformedBodiesDoNotCorruptOrdering() {
        val gate = NaviampUpnpEventGate(7, "uuid:subscription")
        val body = upnpFixtureEvent("http://127.0.0.1/media?a=1&b=2")
        assertNull(gate.accept(6, upnpFixtureHeaders(0), body))
        assertNull(gate.accept(7, upnpFixtureHeaders(0, "uuid:other"), body))
        assertFalse(assertNotNull(gate.accept(7, upnpFixtureHeaders(0), body)).needsResync)
        assertNull(gate.accept(7, upnpFixtureHeaders(0), body))
        assertTrue(assertNotNull(gate.accept(7, upnpFixtureHeaders(2), body)).needsResync)
        assertFailsWith<NaviampUpnpProtocolException> { gate.accept(7, upnpFixtureHeaders(3), "<wrong/>") }
        val valid = assertNotNull(gate.accept(7, upnpFixtureHeaders(3), body))
        assertFalse(valid.needsResync)
        assertEquals("http://127.0.0.1/media?a=1&b=2", valid.transport.trackUri)
        assertFailsWith<NaviampUpnpProtocolException> { gate.accept(7, upnpFixtureHeaders(4) + ("Content-Type" to "text/plain"), body) }
    }

    @Test fun eventCounterWrapSkipsZeroAndRejectsLatePreWrapEvents() {
        val gate = NaviampUpnpEventGate(7, "uuid:subscription")
        val body = upnpFixtureEvent("http://127.0.0.1/media")
        assertTrue(assertNotNull(gate.accept(7, upnpFixtureHeaders(0xFFFF_FFFFL), body)).needsResync)
        assertFalse(assertNotNull(gate.accept(7, upnpFixtureHeaders(1), body)).needsResync)
        assertNull(gate.accept(7, upnpFixtureHeaders(0xFFFF_FFFFL), body))
        assertNull(gate.accept(7, upnpFixtureHeaders(0), body))
    }
}
