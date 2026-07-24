package app.naviamp.presentation

import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampCoreStreamingPlaybackServicesTest {
    @Test
    fun mountsNativeEngineBehindSharedSettingsAndReducedSidecars() {
        val scope = CoroutineScope(SupervisorJob())
        val engine = RecordingStreamingEngine()
        var persisted: PlaybackSettings? = null
        var visualizer: VisualizerSettings? = null

        val services = naviampCoreStreamingPlaybackServices(
            scope = scope,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { null },
            initialPlaybackSettings = PlaybackSettings(
                volumePercent = 61,
                gaplessEnabled = false,
                crossfadeDurationSeconds = 7,
            ),
            persistPlaybackSettings = { persisted = it },
            playbackSessionRepository = EmptyStreamingPlaybackSessions,
            saveVisualizerSettings = { visualizer = it },
        )

        assertEquals(61, engine.volumePercent)
        assertEquals(7, engine.crossfadeSeconds)
        assertTrue(services.effects.capabilities.supportsPause)
        assertTrue(services.effects.capabilities.supportsSeek)
        assertEquals(null, services.sidecars.snapshot().lyrics)

        val updated = services.settings.apply(
            PlaybackSettings(volumePercent = 43, gaplessEnabled = false, crossfadeDurationSeconds = 3),
            redownload = false,
        )
        services.visualizerSettings.save(NaviampVisualizer.ReactiveBars)

        assertEquals(updated, persisted)
        assertEquals(43, engine.volumePercent)
        assertEquals(3, engine.crossfadeSeconds)
        assertEquals(VisualizerSettings(NaviampVisualizer.ReactiveBars.name), visualizer)
        scope.cancel()
    }
}

private object EmptyStreamingPlaybackSessions : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}

private class RecordingStreamingEngine : PlaybackEngine, QueueAwarePlaybackEngine {
    override val name = "Streaming"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = true
    override val supportsCrossfade = true
    override val supportsReplayGain = true
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    var volumePercent = -1
    var crossfadeSeconds = -1

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
    override fun setVolume(percent: Int) { volumePercent = percent }
    override fun stop() = Unit
    override fun setCrossfadeDuration(seconds: Int) { crossfadeSeconds = seconds }
    override fun prepareNext(request: PlaybackRequest) = Unit
    override fun clearPreparedNext() = Unit
}
