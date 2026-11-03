package app.naviamp.testkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaviampMobileTlsEndpointsTest {
    private val manifest = mapOf("controlPort" to "41001", "mutualPort" to "41002", "privateCaPort" to "41003", "wrongHostPort" to "41004")

    @Test fun publishedEndpointsDriveEachTlsRole() {
        val endpoints = NaviampMobileTlsEndpoints.fromManifest(manifest)
        assertEquals(41001, endpoints.controlPort)
        assertEquals("https://127.0.0.1:41002", endpoints.mutualUrl)
        assertEquals("https://127.0.0.1:41003", endpoints.privateCaUrl)
        assertEquals("https://127.0.0.1:41004", endpoints.wrongHostUrl)
    }

    @Test fun absentEndpointCannotFallBackToAnotherFixture() {
        assertFailsWith<NoSuchElementException> { NaviampMobileTlsEndpoints.fromManifest(manifest - "mutualPort") }
    }

    @Test fun invalidOrOverlappingEndpointsAreRejected() {
        for (port in listOf("0", "-1", "65536", "41001", "bad")) {
            assertFailsWith<IllegalArgumentException> { NaviampMobileTlsEndpoints.fromManifest(manifest + ("mutualPort" to port)) }
        }
    }
}
