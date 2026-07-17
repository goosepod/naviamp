package app.naviamp.domain.app

import app.naviamp.domain.playback.PlaybackEngine

/**
 * Operating-system integrations that can change shared application behavior or presentation.
 *
 * Playback-device features such as crossfade and ReplayGain remain on [PlaybackEngine] and its
 * optional capability interfaces. This registry is for host-level services so shared code never
 * needs to branch on an operating-system name.
 */
enum class PlatformCapability {
    BackgroundPlayback,
    SystemMediaControls,
    SecureCredentialStorage,
    InsecureServerVerification,
    CustomServerCertificates,
    ClientCertificates,
    Downloads,
    OfflinePlayback,
    SettingsImportExport,
    FileSelection,
    Sharing,
    ApplicationUpdates,
    AutomotiveBrowsing,
}

enum class PlatformCapabilityStatus {
    Available,
    Experimental,
    Unavailable,
}

data class PlatformCapabilities(
    private val statuses: Map<PlatformCapability, PlatformCapabilityStatus> = emptyMap(),
) {
    fun status(capability: PlatformCapability): PlatformCapabilityStatus =
        statuses[capability] ?: PlatformCapabilityStatus.Unavailable

    fun supports(capability: PlatformCapability): Boolean =
        status(capability) != PlatformCapabilityStatus.Unavailable

    fun isReleaseReady(capability: PlatformCapability): Boolean =
        status(capability) == PlatformCapabilityStatus.Available

    fun withStatus(
        capability: PlatformCapability,
        status: PlatformCapabilityStatus,
    ): PlatformCapabilities =
        copy(statuses = statuses + (capability to status))
}
