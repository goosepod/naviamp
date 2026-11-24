package app.naviamp.ios.platform

import app.naviamp.app.*
import app.naviamp.ios.playback.runIosNativeEventLoopTest
import kotlin.test.*

class IosNaviampUpnpEffectsTest {
    @Test fun secureTokensUseNativeEntropyAndSharedUrlSafeEncoding() {
        val source = IosNaviampSecureTokenSource()
        val tokens = List(16) { source.newToken() }
        assertEquals(tokens.size, tokens.toSet().size)
        assertTrue(tokens.all { it.length == 43 && it.all { c -> c.isLetterOrDigit() || c in "-_" } })
        assertFalse(iosUpnpMulticastAvailable(), "The test bundle has no opt-in multicast build fact")
    }

    @Test fun nativeInterfaceEnumerationReturnsNumericIpv4WithoutSendingPackets() {
        assertTrue(iosUpnpLocalAddresses().all { address ->
            val octets = address.split('.')
            octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }
        })
    }

    @Test fun darwinEngineReachesSharedRelayAndDoesNotFollowRedirects() = runIosNativeEventLoopTest {
        val server = NaviampKtorMediaHttpServerEffect()
        val http = IosNaviampUpnpHttpEffect()
        var requests = 0
        var base = ""
        try {
            base = server.start("127.0.0.1") { request, response ->
                requests++
                if (request.path == "/redirect") response.begin(302, mapOf("Location" to "$base/fixture"))
                else {
                    response.begin(200, mapOf("Content-Type" to "text/xml", "Content-Length" to "5"))
                    response.write("<ok/>".encodeToByteArray(), 5)
                }
            }
            val reply = http.exchange(NaviampUpnpHttpRequest("GET", "$base/fixture"))
            assertEquals(200, reply.status)
            assertEquals("<ok/>", reply.body)
            assertEquals(1, requests)
            assertFailsWith<NaviampUpnpProtocolException> { http.exchange(NaviampUpnpHttpRequest("GET", "$base/redirect")) }
            assertEquals(2, requests, "Redirect must not trigger a second request")
        } finally { http.close(); server.stop() }
    }
}
