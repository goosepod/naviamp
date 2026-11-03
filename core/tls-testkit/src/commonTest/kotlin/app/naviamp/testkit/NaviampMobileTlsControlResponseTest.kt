package app.naviamp.testkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaviampMobileTlsControlResponseTest {
    @Test fun runnerBootstrapPreservesBase64PaddingAndLineEndings() {
        assertEquals(mapOf("ca" to "Y2E=", "identity" to "YQ==", "password" to "fixture"),
            decodeNaviampMobileTlsControlResponse("ca=Y2E=\nidentity=YQ==\npassword=fixture\n"))
    }

    @Test fun stateAuditUsesTheSameBoundedControlFormat() {
        assertEquals(mapOf("bytes" to "1024", "peers" to "CN=Naviamp Test Client", "ranges" to "bytes=2000100-"),
            decodeNaviampMobileTlsControlResponse("bytes=1024\npeers=CN=Naviamp Test Client\nranges=bytes=2000100-\n"))
    }

    @Test fun emptyOrOversizedRunnerPayloadIsRejected() {
        assertFailsWith<IllegalArgumentException> { decodeNaviampMobileTlsControlResponse("") }
        assertFailsWith<IllegalArgumentException> { decodeNaviampMobileTlsControlResponse("x".repeat(1_048_577)) }
    }
}
