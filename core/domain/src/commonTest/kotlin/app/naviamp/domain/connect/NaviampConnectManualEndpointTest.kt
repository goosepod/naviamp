package app.naviamp.domain.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampConnectManualEndpointTest {
    @Test
    fun acceptsTailnetIpv4HostnameAndBracketedIpv6() {
        assertEquals("100.101.102.103:45678", parseNaviampConnectManualEndpoint(" 100.101.102.103:45678 ")?.displayAddress)
        assertEquals("player.tailnet.ts.net:1234", parseNaviampConnectManualEndpoint("PLAYER.TAILNET.TS.NET:1234")?.displayAddress)
        assertEquals("[fd7a:115c:a1e0::1]:5000", parseNaviampConnectManualEndpoint("[FD7A:115C:A1E0::1]:5000")?.displayAddress)
    }

    @Test
    fun rejectsMissingOrInvalidPortsAndMalformedAddresses() {
        listOf("", "player", "player:0", "player:65536", "player:abc", "100.300.1.2:42",
            "100.01.2.3:42", "bad..host:42", "-host:42", "fd7a::1:42", "[fd7a:::1]:42",
            "[fd7a::1]42", "[fe80::1%wlan0]:42", "http://player:42").forEach { value ->
            assertNull(parseNaviampConnectManualEndpoint(value), value)
        }
    }
}
