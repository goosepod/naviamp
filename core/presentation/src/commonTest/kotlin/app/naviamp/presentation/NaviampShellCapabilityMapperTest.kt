package app.naviamp.presentation

import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampShellCapabilityMapperTest {
    @Test
    fun settingsPickerActionsRequireBothSharedSettingsAndFileCapabilities() {
        val complete = capabilityPresentation(
            PlatformCapability.SettingsImportExport,
            PlatformCapability.FileSelection,
        ).toCoreActionAvailability()
        val settingsOnly = capabilityPresentation(
            PlatformCapability.SettingsImportExport,
        ).toCoreActionAvailability()

        assertEquals(NaviampCoreActionAvailability(true, true, true, true), complete)
        assertEquals(NaviampCoreActionAvailability(), settingsOnly)
    }

    @Test
    fun mapsHostFactsAndPlaybackContractsWithoutPlatformBranching() {
        val platform = PlatformCapabilities()
            .withStatus(PlatformCapability.Downloads, PlatformCapabilityStatus.Available)
            .withStatus(PlatformCapability.SettingsImportExport, PlatformCapabilityStatus.Experimental)
            .withStatus(PlatformCapability.FileSelection, PlatformCapabilityStatus.Available)
            .withStatus(PlatformCapability.CustomServerCertificates, PlatformCapabilityStatus.Available)
            .withStatus(PlatformCapability.ApplicationUpdates, PlatformCapabilityStatus.Available)
            .withStatus(PlatformCapability.SoftwareVolumeControl, PlatformCapabilityStatus.Available)
            .withStatus(PlatformCapability.HoverTooltips, PlatformCapabilityStatus.Available)
        val capabilities = NaviampCapabilityPresentation(platform).toShellCapabilitiesUi(
            playbackEngine = TestPlaybackEngine,
            sonicSimilarityAvailable = true,
            showMobileNetworkQuality = true,
        )

        assertTrue(capabilities.downloads)
        assertTrue(capabilities.settingsImportExport)
        assertTrue(capabilities.fileSelection)
        assertTrue(capabilities.applicationUpdates)
        assertTrue(capabilities.replayGain)
        assertTrue(capabilities.gapless)
        assertTrue(capabilities.crossfade)
        assertTrue(capabilities.equalizer)
        assertTrue(capabilities.sonicSimilarity)
        assertTrue(capabilities.softwareVolumeControl)
        assertTrue(capabilities.hoverTooltips)
        assertTrue(capabilities.showMobileNetworkQuality)
        assertFalse(capabilities.connection.insecureServerVerification)
        assertTrue(capabilities.connection.customServerCertificates)
        assertFalse(capabilities.connection.clientCertificates)
    }

    @Test
    fun hidesSoftwareVolumeWhenTheHostDoesNotExposeIt() {
        val capabilities = NaviampCapabilityPresentation(PlatformCapabilities()).toShellCapabilitiesUi(
            playbackEngine = TestPlaybackEngine,
            sonicSimilarityAvailable = false,
        )

        assertFalse(capabilities.softwareVolumeControl)
        assertFalse(capabilities.hoverTooltips)
        assertFalse(capabilities.applicationUpdates)
    }
}

private fun capabilityPresentation(vararg available: PlatformCapability): NaviampCapabilityPresentation =
    NaviampCapabilityPresentation(
        available.fold(PlatformCapabilities()) { capabilities, capability ->
            capabilities.withStatus(capability, PlatformCapabilityStatus.Available)
        },
    )

private object TestPlaybackEngine : PlaybackEngine, EqualizerPlaybackEngine {
    override val name: String = "Test"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsReplayGain: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = false
    override val supportsEqualizer: Boolean = true

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) = Unit

    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun setEqualizer(settings: EqualizerSettings) = Unit
}
