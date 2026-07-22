package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCorePlaybackEngineAdapterTest {
    @Test
    fun coreResolvesProviderPlaybackAndPublishesNativeObservations() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val states = mutableListOf<PlaybackState>()
        val progressEvents = mutableListOf<PlaybackProgress>()
        val metadataEvents = mutableListOf<PlaybackStreamMetadata>()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
        )
        adapter.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) { states += state }
            override fun onProgressChanged(progress: PlaybackProgress) { progressEvents += progress }
            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) { metadataEvents += metadata }
        })

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track), 0), 0)
        advanceUntilIdle()

        assertEquals("https://example.test/core-track", engine.request?.url)
        assertEquals(listOf<PlaybackState>(PlaybackState.Playing), states)
        assertEquals(12.0, progressEvents.single().positionSeconds)
        assertEquals("Core Stream", metadataEvents.single().title)
    }

    @Test
    fun coreAppliesEffectiveSettingsToEveryEngine() {
        val engine = RecordingPlaybackEngine()
        val settings = NaviampCorePlaybackEngineSettings(engine)

        val effective = settings.apply(PlaybackSettings(volumePercent = 140), redownload = false)

        assertEquals(100, effective.volumePercent)
        assertEquals(100, engine.appliedVolume)
    }
}

private class RecordingPlaybackEngine : PlaybackEngine {
    override val name = "Recording"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    var request: PlaybackRequest? = null
    var appliedVolume = -1

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.request = request
        onStateChanged(PlaybackState.Playing)
        onProgressChanged(PlaybackProgress(12.0, 180.0))
        onMetadataChanged(PlaybackStreamMetadata(title = "Core Stream"))
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) { appliedVolume = percent }
    override fun stop() = Unit
}
