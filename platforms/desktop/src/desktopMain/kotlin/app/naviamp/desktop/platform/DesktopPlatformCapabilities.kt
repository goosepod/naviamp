package app.naviamp.desktop

import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.app.NaviampClock
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus

/** Desktop OS facts shared by the legacy and replacement hosts during cutover. */
val DesktopPlatformCapabilities: PlatformCapabilities = listOf(
    PlatformCapability.BackgroundPlayback,
    PlatformCapability.SecureCredentialStorage,
    PlatformCapability.InsecureServerVerification,
    PlatformCapability.CustomServerCertificates,
    PlatformCapability.ClientCertificates,
    PlatformCapability.Downloads,
    PlatformCapability.OfflinePlayback,
    PlatformCapability.SettingsImportExport,
    PlatformCapability.FileSelection,
    PlatformCapability.ApplicationUpdates,
).fold(PlatformCapabilities()) { capabilities, capability ->
    capabilities.withStatus(capability, PlatformCapabilityStatus.Available)
}

val DesktopCapabilityPresentation = NaviampCapabilityPresentation(DesktopPlatformCapabilities)
val DesktopSystemClock = NaviampClock(System::currentTimeMillis)
