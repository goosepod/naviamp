package app.naviamp.domain.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesTest {
    @Test
    fun unknownCapabilitiesFailClosed() {
        val capabilities = PlatformCapabilities()

        assertEquals(
            PlatformCapabilityStatus.Unavailable,
            capabilities.status(PlatformCapability.BackgroundPlayback),
        )
        assertFalse(capabilities.supports(PlatformCapability.BackgroundPlayback))
        assertFalse(capabilities.isReleaseReady(PlatformCapability.BackgroundPlayback))
    }

    @Test
    fun experimentalCapabilitiesCanBeUsedWithoutClaimingReleaseReadiness() {
        val capabilities = PlatformCapabilities(
            mapOf(PlatformCapability.SystemMediaControls to PlatformCapabilityStatus.Experimental),
        )

        assertTrue(capabilities.supports(PlatformCapability.SystemMediaControls))
        assertFalse(capabilities.isReleaseReady(PlatformCapability.SystemMediaControls))
    }

    @Test
    fun availableCapabilitiesAreReleaseReady() {
        val capabilities = PlatformCapabilities()
            .withStatus(PlatformCapability.Downloads, PlatformCapabilityStatus.Available)

        assertTrue(capabilities.supports(PlatformCapability.Downloads))
        assertTrue(capabilities.isReleaseReady(PlatformCapability.Downloads))
    }
}
