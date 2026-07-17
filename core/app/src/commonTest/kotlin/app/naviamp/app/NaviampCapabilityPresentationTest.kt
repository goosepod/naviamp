package app.naviamp.app

import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCapabilityPresentationTest {
    @Test
    fun unavailableFeaturesAreHiddenAndDisabled() {
        val feature = NaviampCapabilityPresentation(PlatformCapabilities()).downloads

        assertFalse(feature.visible)
        assertFalse(feature.enabled)
        assertFalse(feature.experimental)
    }

    @Test
    fun availableFeaturesAreVisibleAndEnabled() {
        val capabilities = PlatformCapabilities().withStatus(
            PlatformCapability.Downloads,
            PlatformCapabilityStatus.Available,
        )

        val feature = NaviampCapabilityPresentation(capabilities).downloads

        assertTrue(feature.visible)
        assertTrue(feature.enabled)
        assertFalse(feature.experimental)
    }

    @Test
    fun experimentalFeaturesRemainUsableAndAreIdentified() {
        val capabilities = PlatformCapabilities().withStatus(
            PlatformCapability.Sharing,
            PlatformCapabilityStatus.Experimental,
        )

        val feature = NaviampCapabilityPresentation(capabilities).sharing

        assertTrue(feature.visible)
        assertTrue(feature.enabled)
        assertTrue(feature.experimental)
    }
}
