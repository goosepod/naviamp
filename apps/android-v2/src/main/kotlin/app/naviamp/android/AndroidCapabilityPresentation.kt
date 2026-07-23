package app.naviamp.android

import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.presentation.NaviampCoreActionAvailability
import app.naviamp.presentation.toCoreActionAvailability

/** Declares only Activity-host capabilities that have concrete Android implementations. */
internal object AndroidCapabilityPresentation {
    private val capabilities = listOf(
        PlatformCapability.FileSelection,
        PlatformCapability.SettingsImportExport,
        PlatformCapability.SecureCredentialStorage,
    ).fold(PlatformCapabilities()) { current, capability ->
        current.withStatus(capability, PlatformCapabilityStatus.Available)
    }

    fun toCoreActionAvailability(): NaviampCoreActionAvailability =
        NaviampCapabilityPresentation(capabilities).toCoreActionAvailability()
}
