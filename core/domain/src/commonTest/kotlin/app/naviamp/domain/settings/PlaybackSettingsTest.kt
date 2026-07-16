package app.naviamp.domain.settings

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.targetOutputSampleRate
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackSettingsTest {
    @Test
    fun downloadedTrackSwipesDefaultToPlayAndRemove() {
        val swipes = TrackSwipeSettings()

        assertEquals(TrackSwipeAction.Play, swipes.downloadsRight)
        assertEquals(TrackSwipeAction.Remove, swipes.downloadsLeft)
    }

    @Test
    fun downloadStreamQualityUsesTheDedicatedDownloadPreference() {
        val settings = PlaybackSettings(
            wifiStreamingQuality = StreamQualityPreference(
                mode = StreamQualityMode.Original,
            ),
            downloadQuality = StreamQualityPreference(
                mode = StreamQualityMode.Transcode,
                codec = StreamingCodec.Opus,
                bitrateKbps = 128,
            ),
        )

        assertEquals(
            StreamQuality.Transcoded(AudioCodec.Opus, 128),
            settings.downloadStreamQuality(),
        )
    }

    @Test
    fun sampleRateConvertersMapToBassSrcQualityLevels() {
        assertEquals(listOf(0, 1, 2, 3, 4), SampleRateConverter.entries.map { it.bassQuality })
        assertEquals(SampleRateConverter.Sinc16, PlaybackSettings().sampleRateConverter)
        assertEquals(SampleRateMatching.Disabled, PlaybackSettings().sampleRateMatching)
    }

    @Test
    fun sampleRateMatchingSelectsTargetOutputRate() {
        assertEquals(null, targetOutputSampleRate(SampleRateMatching.Disabled, 48_000, startingFromIdle = true))
        assertEquals(48_000, targetOutputSampleRate(SampleRateMatching.Smart, 48_000, startingFromIdle = true))
        assertEquals(null, targetOutputSampleRate(SampleRateMatching.Smart, 48_000, startingFromIdle = false))
        assertEquals(96_000, targetOutputSampleRate(SampleRateMatching.Strict, 96_000, startingFromIdle = false))
        assertEquals(null, targetOutputSampleRate(SampleRateMatching.Strict, 7_999, startingFromIdle = true))
        assertEquals(null, targetOutputSampleRate(SampleRateMatching.Strict, null, startingFromIdle = true))
    }

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
            outputDevice = AudioOutputDevicePreference(
                mode = AudioOutputDeviceMode.Pinned,
                deviceId = "  built-in-output  ",
                deviceName = "  Built-in Output  ",
            ),
            gaplessEnabled = false,
            crossfadeDurationSeconds = 99,
            volumePercent = -10,
            wifiStreamingQuality = StreamQualityPreference(bitrateKbps = 999),
            mobileStreamingQuality = StreamQualityPreference(bitrateKbps = 128),
            downloadQuality = StreamQualityPreference(bitrateKbps = 1),
        )

        val effective = settings.effectiveForEngine(FakePlaybackEngine())

        assertEquals(ReplayGainMode.Track, effective.replayGainMode)
        assertEquals(AudioOutputDeviceMode.Pinned, effective.outputDevice.mode)
        assertEquals("built-in-output", effective.outputDevice.deviceId)
        assertEquals("Built-in Output", effective.outputDevice.deviceName)
        assertEquals(12, effective.crossfadeDurationSeconds)
        assertEquals(0, effective.volumePercent)
        assertEquals(192, effective.wifiStreamingQuality.bitrateKbps)
        assertEquals(128, effective.mobileStreamingQuality.bitrateKbps)
        assertEquals(192, effective.downloadQuality.bitrateKbps)
    }

    @Test
    fun normalizedPlaybackSettingsClampPlayReportTriggerPercent() {
        assertEquals(0, PlaybackSettings(playReportDurationPercent = -1).normalized().playReportDurationPercent)
        assertEquals(100, PlaybackSettings(playReportDurationPercent = 250).normalized().playReportDurationPercent)
    }

    @Test
    fun audioOutputDevicePreferenceFallsBackToFollowSystemWithoutPinnedDeviceId() {
        val preference = AudioOutputDevicePreference(
            mode = AudioOutputDeviceMode.Pinned,
            deviceId = " ",
            deviceName = "Headphones",
        )

        assertEquals(AudioOutputDevicePreference(), preference.normalized())
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

    @Test
    fun playbackSettingsChangeReportsWhenLyricsSidecarsNeedReloading() {
        val previous = PlaybackSettings(lrclibLyricsEnabled = false)
        val unchanged = playbackSettingsChange(
            requested = previous.copy(volumePercent = 40),
            playbackEngine = FakePlaybackEngine(),
            previous = previous,
        )
        val changed = playbackSettingsChange(
            requested = previous.copy(lrclibLyricsEnabled = true),
            playbackEngine = FakePlaybackEngine(),
            previous = previous,
        )

        assertEquals(40, unchanged.settings.volumePercent)
        assertFalse(unchanged.shouldReloadLyricsSidecars)
        assertEquals(true, changed.shouldReloadLyricsSidecars)
    }

    @Test
    fun maintenanceControllerSavesEffectiveSettingsAndReloadsLyricsSidecars() {
        var current = PlaybackSettings(lrclibLyricsEnabled = false)
        var saved: PlaybackSettings? = null
        var reloadCount = 0

        PlaybackSettingsMaintenanceController(
            playbackEngine = FakePlaybackEngine(supportsSoftwareVolume = false),
            playbackSettings = { current },
            setPlaybackSettings = { settings -> current = settings },
            savePlaybackSettings = { settings -> saved = settings },
            reloadLyricsSidecars = { reloadCount += 1 },
        ).applyPlaybackSettings(
            PlaybackSettings(
                lrclibLyricsEnabled = true,
                volumePercent = 25,
            ),
        )

        assertEquals(100, current.volumePercent)
        assertEquals(current, saved)
        assertEquals(1, reloadCount)
    }

    @Test
    fun maintenanceControllerRedownloadsExistingDownloadsAfterApplyingSettings() {
        var current = PlaybackSettings()
        val downloaded = listOf(track("one"), track("two"))
        var redownloadedTracks = emptyList<Track>()
        var redownloadLabel: String? = null

        PlaybackSettingsMaintenanceController(
            playbackEngine = FakePlaybackEngine(),
            playbackSettings = { current },
            setPlaybackSettings = { settings -> current = settings },
            savePlaybackSettings = { _ -> },
            reloadLyricsSidecars = {},
            downloadedTracks = { downloaded },
            redownloadTracks = { tracks, label ->
                redownloadedTracks = tracks
                redownloadLabel = label
            },
        ).applyPlaybackSettingsAndRedownload(
            PlaybackSettings(downloadQuality = StreamQualityPreference(bitrateKbps = 128)),
        )

        assertEquals(128, current.downloadQuality.bitrateKbps)
        assertEquals(downloaded, redownloadedTracks)
        assertEquals("downloads", redownloadLabel)
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

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
