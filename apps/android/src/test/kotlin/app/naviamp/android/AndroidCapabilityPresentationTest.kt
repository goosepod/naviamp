package app.naviamp.android

import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
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

class AndroidCapabilityPresentationTest {
    @Test
    fun declaresMountedAndroidServicesWithoutExposingSoftwareVolume() {
        val expected = listOf(
            PlatformCapability.BackgroundPlayback,
            PlatformCapability.SystemMediaControls,
            PlatformCapability.Downloads,
            PlatformCapability.OfflinePlayback,
            PlatformCapability.ApplicationUpdates,
            PlatformCapability.AutomotiveBrowsing,
        )
        expected.forEach { capability ->
            assertEquals(
                PlatformCapabilityStatus.Available,
                AndroidCapabilityPresentation.capabilities.status(capability),
            )
        }

        val shell = AndroidCapabilityPresentation.toShellCapabilitiesUi(TestPlaybackEngine)
        assertTrue(shell.downloads)
        assertTrue(shell.applicationUpdates)
        assertFalse(shell.softwareVolumeControl)
    }
}

private object TestPlaybackEngine : PlaybackEngine {
    override val name = "Test"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsReplayGain = false
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = false
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
}
