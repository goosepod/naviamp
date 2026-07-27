package app.naviamp.ios.platform

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertTrue

class IosCapabilityPresentationTest {
    @Test
    fun exposesDownloadsAfterTheNativeStorageEffectIsMounted() {
        assertTrue(IosCapabilityPresentation.shell(TestPlaybackEngine).downloads)
    }
}

private object TestPlaybackEngine : PlaybackEngine {
    override val name = "Test"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsReplayGain = false
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsSoftwareVolume = false
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
