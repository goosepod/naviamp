package app.naviamp.ios.platform

import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.presentation.UnavailableNaviampPlaybackEngine
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.presentation.toShellCapabilitiesUi

/** Declares only Apple services that the current thin host has actually mounted. */
object IosCapabilityPresentation {
    private val platform = listOf(
        PlatformCapability.SecureCredentialStorage,
        PlatformCapability.ApplicationUpdates,
    ).fold(PlatformCapabilities()) { current, capability ->
        current.withStatus(capability, PlatformCapabilityStatus.Available)
    }
    private val presentation = NaviampCapabilityPresentation(platform)

    val actionAvailability = presentation.toCoreActionAvailability()
    val shell = presentation.toShellCapabilitiesUi(
        playbackEngine = UnavailableNaviampPlaybackEngine,
        sonicSimilarityAvailable = false,
        showMobileNetworkQuality = false,
    )
}
