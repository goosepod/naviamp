package app.naviamp.ios.platform

import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.presentation.toShellCapabilitiesUi

/** Declares only Apple services that the current thin host has actually mounted. */
object IosCapabilityPresentation {
    private val platform = listOf(
        PlatformCapability.SecureCredentialStorage,
        PlatformCapability.InsecureServerVerification,
        PlatformCapability.ApplicationUpdates,
    ).fold(PlatformCapabilities()) { current, capability ->
        current.withStatus(capability, PlatformCapabilityStatus.Available)
    }
    private val presentation = NaviampCapabilityPresentation(platform)

    val actionAvailability = presentation.toCoreActionAvailability()
    fun shell(playbackEngine: PlaybackEngine) = presentation.toShellCapabilitiesUi(
        playbackEngine = playbackEngine,
        sonicSimilarityAvailable = false,
        showMobileNetworkQuality = false,
    )
}
