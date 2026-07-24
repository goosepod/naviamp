package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.normalized
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope

/**
 * Core-owned capability catalog for a host whose native audio engine has not been mounted yet.
 * This keeps an early host browseable without inventing platform playback policy or capabilities.
 */
fun unavailableNaviampCorePlaybackServices(
    persistSettings: (PlaybackSettings) -> Unit = {},
    sessions: PlaybackSessionRepository,
): NaviampCorePlaybackServices {
    return NaviampCorePlaybackServices(
        effects = UnavailableNaviampCorePlaybackEffects,
        settings = NaviampCorePlaybackSettingsPort { requested, _ ->
            requested.normalized()
                .effectiveForEngine(UnavailableNaviampPlaybackEngine)
                .also(persistSettings)
        },
        sidecars = object : NaviampCoreNowPlayingSidecarPort {
            override fun snapshot() = NaviampCoreNowPlayingSidecars()
            override suspend fun loadForTrack(track: Track) = Unit
            override suspend fun loadLyrics(track: Track) = Unit
            override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
        },
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) = Unit
        },
        sessions = NaviampPlaybackSessionController(sessions),
    )
}

/** Capability-only engine fact used while a host has not installed its native audio adapter. */
object UnavailableNaviampPlaybackEngine : PlaybackEngine {
    override val name = "Playback not mounted"
    override val supportsPause = false
    override val supportsSeek = false
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = false
    override val prefersOriginalStream = true

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

private object UnavailableNaviampCorePlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities(
        engineName = "Playback not mounted",
        supportsPause = false,
        supportsSeek = false,
        supportsSoftwareVolume = false,
        supportsVisualizer = false,
    )
    override val playbackSource = PlaybackSource.ProviderStream

    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = false
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
}
