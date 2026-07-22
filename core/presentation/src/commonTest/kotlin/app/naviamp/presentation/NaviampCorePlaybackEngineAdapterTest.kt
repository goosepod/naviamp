package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.TrackId
import app.naviamp.domain.InternetRadioStation
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
    fun restoredQueueWaitsForPlayAndResumesAtTheSavedPosition() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
        )

        adapter.restoreQueue(PlaybackQueue(listOf(provider.track), 0), startPositionSeconds = 37.0)
        assertEquals(null, engine.request)

        adapter.startOrRestore()
        advanceUntilIdle()

        assertEquals(37.0, engine.request?.startPositionSeconds)
    }

    @Test
    fun internetRadioUsesTheStationStreamWithoutProviderResolution() = runTest {
        val engine = RecordingPlaybackEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { null },
            settings = { PlaybackSettings() },
        )

        adapter.play(
            InternetRadioStation(
                id = "radio-1",
                name = "Radio One",
                streamUrl = "https://radio.example/live",
            ),
        )
        advanceUntilIdle()

        assertEquals("https://radio.example/live", engine.request?.url)
        assertEquals("internet-radio:radio-1", engine.request?.mediaId)
    }

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

        val next = provider.track.copy(id = TrackId("next"), title = "Next")
        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()

        assertEquals("https://example.test/core-track", engine.request?.url)
        assertEquals(listOf<PlaybackState>(PlaybackState.Playing), states)
        assertEquals(12.0, progressEvents.single().positionSeconds)
        assertEquals("Core Stream", metadataEvents.single().title)
        assertEquals("https://example.test/next", engine.preparedRequest?.url)
        assertEquals(listOf("play:core-track", "prepare:next"), engine.events)
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

private class RecordingPlaybackEngine : PlaybackEngine, QueueAwarePlaybackEngine {
    override val name = "Recording"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = true
    override val supportsCrossfade = true
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    var request: PlaybackRequest? = null
    var preparedRequest: PlaybackRequest? = null
    var appliedVolume = -1
    val events = mutableListOf<String>()

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.request = request
        events += "play:${request.mediaId}"
        onStateChanged(PlaybackState.Playing)
        onProgressChanged(PlaybackProgress(12.0, 180.0))
        onMetadataChanged(PlaybackStreamMetadata(title = "Core Stream"))
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) { appliedVolume = percent }
    override fun stop() = Unit
    override fun setCrossfadeDuration(seconds: Int) = Unit
    override fun prepareNext(request: PlaybackRequest) {
        preparedRequest = request
        events += "prepare:${request.mediaId}"
    }
    override fun clearPreparedNext() {
        preparedRequest = null
    }
}
