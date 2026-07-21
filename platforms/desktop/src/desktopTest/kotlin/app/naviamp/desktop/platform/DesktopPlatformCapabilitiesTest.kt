package app.naviamp.desktop

import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlatformCapabilitiesTest {
    @Test
    fun declaresOnlyImplementedDesktopOsServices() {
        assertEquals(
            PlatformCapabilityStatus.Available,
            DesktopPlatformCapabilities.status(PlatformCapability.FileSelection),
        )
        assertTrue(DesktopCapabilityPresentation.settingsImportExport.enabled)
        assertTrue(DesktopCapabilityPresentation.fileSelection.enabled)
        assertFalse(DesktopCapabilityPresentation.automotiveBrowsing.visible)
    }
}
