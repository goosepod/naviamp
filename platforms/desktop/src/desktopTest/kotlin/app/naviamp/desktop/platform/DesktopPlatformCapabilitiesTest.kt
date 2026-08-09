package app.naviamp.desktop

import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.domain.settings.DesktopShortcutPlatform
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
        assertTrue(DesktopCapabilityPresentation.secureCredentialStorage.enabled)
        assertTrue(DesktopCapabilityPresentation.applicationUpdates.enabled)
        assertFalse(DesktopCapabilityPresentation.automotiveBrowsing.visible)
    }

    @Test
    fun mapsDesktopOperatingSystemsToShortcutPlatforms() {
        assertEquals(DesktopShortcutPlatform.Windows, desktopShortcutPlatform("Windows 11"))
        assertEquals(DesktopShortcutPlatform.MacOS, desktopShortcutPlatform("Mac OS X"))
        assertEquals(DesktopShortcutPlatform.Linux, desktopShortcutPlatform("Linux"))
    }
}
