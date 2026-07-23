package app.naviamp.presentation

import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.ui.NaviampConnectionCapabilitiesUi
import app.naviamp.ui.NaviampShellCapabilitiesUi

/** Native pickers are optional effects; their visibility is derived once from shared capability policy. */
fun NaviampCapabilityPresentation.toCoreActionAvailability(): NaviampCoreActionAvailability {
    val settingsDocuments = settingsImportExport.enabled && fileSelection.enabled
    return NaviampCoreActionAvailability(
        importFile = settingsDocuments,
        chooseSyncFolder = settingsDocuments,
        importFolder = settingsDocuments,
        exportFolder = settingsDocuments,
    )
}

/**
 * Maps platform-service facts and shared playback contracts to one product capability model.
 *
 * Hosts declare facts and implement services. They do not independently decide which normal
 * Naviamp features exist or how those facts are presented by the shared shell.
 */
fun NaviampCapabilityPresentation.toShellCapabilitiesUi(
    playbackEngine: PlaybackEngine,
    sonicSimilarityAvailable: Boolean,
    showMobileNetworkQuality: Boolean = false,
): NaviampShellCapabilitiesUi = NaviampShellCapabilitiesUi(
    replayGain = playbackEngine.supportsReplayGain,
    gapless = playbackEngine.supportsGapless,
    crossfade = playbackEngine.supportsCrossfade,
    equalizer = (playbackEngine as? EqualizerPlaybackEngine)?.supportsEqualizer == true,
    sonicSimilarity = sonicSimilarityAvailable,
    softwareVolumeControl = softwareVolumeControl.visible && playbackEngine.supportsSoftwareVolume,
    downloads = downloads.visible,
    settingsImportExport = settingsImportExport.visible,
    applicationUpdates = applicationUpdates.visible,
    fileSelection = fileSelection.visible,
    showMobileNetworkQuality = showMobileNetworkQuality,
    connection = NaviampConnectionCapabilitiesUi(
        insecureServerVerification = insecureServerVerification.visible,
        customServerCertificates = customServerCertificates.visible,
        clientCertificates = clientCertificates.visible,
    ),
)
