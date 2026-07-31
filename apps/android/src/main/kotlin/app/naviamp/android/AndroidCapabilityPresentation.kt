package app.naviamp.android

import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.presentation.NaviampCoreActionAvailability
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.presentation.toShellCapabilitiesUi

/** Declares only Android-host capabilities that have concrete Activity or service implementations. */
internal object AndroidCapabilityPresentation {
    internal val capabilities = listOf(
        PlatformCapability.BackgroundPlayback,
        PlatformCapability.SystemMediaControls,
        PlatformCapability.FileSelection,
        PlatformCapability.SettingsImportExport,
        PlatformCapability.SecureCredentialStorage,
        PlatformCapability.InsecureServerVerification,
        PlatformCapability.CustomServerCertificates,
        PlatformCapability.ClientCertificates,
        PlatformCapability.Downloads,
        PlatformCapability.OfflinePlayback,
        PlatformCapability.ApplicationUpdates,
        PlatformCapability.AutomotiveBrowsing,
    ).fold(PlatformCapabilities()) { current, capability ->
        current.withStatus(capability, PlatformCapabilityStatus.Available)
    }

    fun toCoreActionAvailability(): NaviampCoreActionAvailability =
        NaviampCapabilityPresentation(capabilities).toCoreActionAvailability()

    fun toShellCapabilitiesUi(playbackEngine: PlaybackEngine) =
        NaviampCapabilityPresentation(capabilities).toShellCapabilitiesUi(
            playbackEngine = playbackEngine,
            sonicSimilarityAvailable = false,
            showMobileNetworkQuality = true,
        )
}
