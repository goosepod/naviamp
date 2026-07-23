package app.naviamp.app

import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus

/** Shared presentation decision for an operating-system capability. */
data class NaviampFeaturePresentation(
    val visible: Boolean,
    val enabled: Boolean,
    val experimental: Boolean,
)

/**
 * Capability-aware UI policy shared by every host.
 *
 * Hosts declare facts through [PlatformCapabilities]. Shared presentation code decides whether a
 * feature is hidden, usable, or marked experimental without branching on an operating-system name.
 */
class NaviampCapabilityPresentation(
    private val capabilities: PlatformCapabilities,
) {
    val backgroundPlayback get() = feature(PlatformCapability.BackgroundPlayback)
    val systemMediaControls get() = feature(PlatformCapability.SystemMediaControls)
    val softwareVolumeControl get() = feature(PlatformCapability.SoftwareVolumeControl)
    val hoverTooltips get() = feature(PlatformCapability.HoverTooltips)
    val secureCredentialStorage get() = feature(PlatformCapability.SecureCredentialStorage)
    val insecureServerVerification get() = feature(PlatformCapability.InsecureServerVerification)
    val customServerCertificates get() = feature(PlatformCapability.CustomServerCertificates)
    val clientCertificates get() = feature(PlatformCapability.ClientCertificates)
    val downloads get() = feature(PlatformCapability.Downloads)
    val offlinePlayback get() = feature(PlatformCapability.OfflinePlayback)
    val settingsImportExport get() = feature(PlatformCapability.SettingsImportExport)
    val fileSelection get() = feature(PlatformCapability.FileSelection)
    val sharing get() = feature(PlatformCapability.Sharing)
    val applicationUpdates get() = feature(PlatformCapability.ApplicationUpdates)
    val automotiveBrowsing get() = feature(PlatformCapability.AutomotiveBrowsing)

    fun feature(capability: PlatformCapability): NaviampFeaturePresentation =
        when (capabilities.status(capability)) {
            PlatformCapabilityStatus.Available -> NaviampFeaturePresentation(
                visible = true,
                enabled = true,
                experimental = false,
            )
            PlatformCapabilityStatus.Experimental -> NaviampFeaturePresentation(
                visible = true,
                enabled = true,
                experimental = true,
            )
            PlatformCapabilityStatus.Unavailable -> NaviampFeaturePresentation(
                visible = false,
                enabled = false,
                experimental = false,
            )
        }
}
