package app.naviamp.domain.settings

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackSettingsTest {
    @Test
    fun effectiveForEngineDisablesUnsupportedFeatures() {
        val settings = PlaybackSettings(
            replayGainMode = ReplayGainMode.Album,
            gaplessEnabled = true,
            crossfadeDurationSeconds = 9,
            volumePercent = 42,
        )

        val effective = settings.effectiveForEngine(
            FakePlaybackEngine(
                supportsReplayGain = false,
                supportsGapless = false,
                supportsCrossfade = false,
                supportsSoftwareVolume = false,
            ),
        )

        assertEquals(ReplayGainMode.Off, effective.replayGainMode)
        assertFalse(effective.gaplessEnabled)
        assertEquals(0, effective.crossfadeDurationSeconds)
        assertEquals(100, effective.volumePercent)
    }

    @Test
    fun effectiveForEngineNormalizesSupportedSettings() {
        val settings = PlaybackSettings(
            replayGainMode = ReplayGainMode.Track,
            gaplessEnabled = false,
            crossfadeDurationSeconds = 99,
            volumePercent = -10,
            wifiStreamingQuality = StreamQualityPreference(bitrateKbps = 999),
            mobileStreamingQuality = StreamQualityPreference(bitrateKbps = 128),
            downloadQuality = StreamQualityPreference(bitrateKbps = 1),
        )

        val effective = settings.effectiveForEngine(FakePlaybackEngine())

        assertEquals(ReplayGainMode.Track, effective.replayGainMode)
        assertEquals(12, effective.crossfadeDurationSeconds)
        assertEquals(0, effective.volumePercent)
        assertEquals(192, effective.wifiStreamingQuality.bitrateKbps)
        assertEquals(128, effective.mobileStreamingQuality.bitrateKbps)
        assertEquals(192, effective.downloadQuality.bitrateKbps)
    }

    @Test
    fun effectiveForEngineLetsGaplessWinOverCrossfadeWhenSupported() {
        val settings = PlaybackSettings(
            gaplessEnabled = true,
            crossfadeDurationSeconds = 8,
        )

        val effective = settings.effectiveForEngine(FakePlaybackEngine())

        assertEquals(true, effective.gaplessEnabled)
        assertEquals(0, effective.crossfadeDurationSeconds)
    }

    private class FakePlaybackEngine(
        override val supportsGapless: Boolean = true,
        override val supportsCrossfade: Boolean = true,
        override val supportsReplayGain: Boolean = true,
        override val supportsSoftwareVolume: Boolean = true,
    ) : PlaybackEngine {
        override val name: String = "Fake"
        override val supportsPause: Boolean = true
        override val supportsSeek: Boolean = true
        override val prefersOriginalStream: Boolean = false

        override fun play(
            scope: CoroutineScope,
            request: PlaybackRequest,
            onStateChanged: (PlaybackState) -> Unit,
            onProgressChanged: (PlaybackProgress) -> Unit,
            onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
        ) = Unit

        override fun pause() = Unit
        override fun resume() = Unit
        override fun seek(positionSeconds: Double) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun stop() = Unit
    }
}

