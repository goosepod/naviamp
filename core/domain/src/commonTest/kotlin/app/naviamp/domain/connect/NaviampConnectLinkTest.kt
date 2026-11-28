package app.naviamp.domain.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectLinkTest {
    private val secret = naviampConnectLinkSecret(ByteArray(16) { (it * 7 + 3).toByte() })
    private val offer = NaviampConnectLinkOffer(
        displayName = "Living Room & Den",
        hosts = listOf("192.168.1.20", "fd00::20"),
        port = 45_123,
        identityFingerprint = "ab".repeat(32),
        offerId = "3f2a7c1e-0000-4000-8000-000000000001",
        secret = secret,
    )

    @Test fun aLinkRoundTripsWithEverySecretInTheFragment() {
        val uri = offer.toUri()
        assertTrue(uri.startsWith("naviamp://connect?v=1#"))
        assertFalse(secret in uri.substringBefore('#'), "Nothing secret is placed before the fragment")
        val parsed = assertIs<NaviampConnectLinkParse.Offer>(parseNaviampConnectLink(uri)).offer
        assertEquals(offer.displayName, parsed.displayName)
        assertEquals(offer.hosts, parsed.hosts)
        assertEquals(offer.port, parsed.port)
        assertEquals(offer.identityFingerprint, parsed.identityFingerprint)
        assertEquals(offer.offerId, parsed.offerId)
        assertEquals(secret, parsed.secret)
    }

    @Test fun theSecretIsNeverPrinted() {
        assertFalse(secret in offer.toString())
    }

    @Test fun aSecretMustCarryAtLeast128Bits() {
        assertFailsWith<IllegalArgumentException> { naviampConnectLinkSecret(ByteArray(15)) }
        val short = offer.toUri().replace("s=$secret", "s=${secret.take(10)}")
        assertEquals(NaviampConnectLinkParse.Invalid, parseNaviampConnectLink(short))
    }

    @Test fun alteredOrIncompleteLinksAreRefused() {
        val uri = offer.toUri()
        listOf(
            "https://example.com/connect?v=1#" + uri.substringAfter('#'),
            uri.substringBefore('#'),
            uri.replace("f=${offer.identityFingerprint}", "f=${"zz".repeat(32)}"),
            uri.replace("p=45123", "p=70000"),
            uri.replace("&o=${offer.offerId}", ""),
            "$uri&s=$secret",
            uri.replace("h=", "h=bad%20host,"),
            uri.replace("n=", "n=%E0%A4"),
            "naviamp://connect?v=0#" + uri.substringAfter('#'),
        ).forEach { assertEquals(NaviampConnectLinkParse.Invalid, parseNaviampConnectLink(it), it) }
    }

    @Test fun aLinkFromANewerReleaseIsReportedAsSuch() {
        val newer = offer.toUri().replace("?v=1#", "?v=2#")
        assertEquals(NaviampConnectLinkParse.UnsupportedVersion, parseNaviampConnectLink(newer))
    }

    @Test fun aLinkWithoutHostsStillParsesForDiscovery() {
        val uri = NaviampConnectLinkOffer("TV", emptyList(), 1_234, "0f".repeat(32), "offer-1", secret).toUri()
        assertEquals(emptyList(), assertIs<NaviampConnectLinkParse.Offer>(parseNaviampConnectLink(uri)).offer.hosts)
    }
}
