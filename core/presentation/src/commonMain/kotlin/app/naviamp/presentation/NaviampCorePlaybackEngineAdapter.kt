package app.naviamp.presentation

import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainPlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SampleRateConverterPlaybackEngine
import app.naviamp.domain.playback.SampleRateMatchingPlaybackEngine
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.playbackTargetPlan
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.AudioOutputDeviceMode
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.streamQualityForNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one playback-engine adapter used by every Naviamp host.
 *
 * Hosts create a native [PlaybackEngine]. Core owns provider URL resolution, queue navigation,
 * prepared-next behavior, callbacks, and settings application through this class.
 */
class NaviampCorePlaybackEngineAdapter(
    private val scope: CoroutineScope,
    private val engine: PlaybackEngine,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val settings: () -> PlaybackSettings,
    private val isMobileData: () -> Boolean = { false },
) : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities(
        engineName = engine.name,
        supportsPause = engine.supportsPause,
        supportsSeek = engine.supportsSeek,
        supportsSoftwareVolume = engine.supportsSoftwareVolume,
        supportsVisualizer = (engine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
    )
    override val playbackSource: PlaybackSource = PlaybackSource.ProviderStream

    private var observer: NaviampCorePlaybackObserver? = null
    private var queue = PlaybackQueue()
    private var repeatMode = RepeatMode.Off
    private var resolutionJob: Job? = null
    private var generation = 0L

    override fun attach(observer: NaviampCorePlaybackObserver) {
        this.observer = observer
    }

    override fun pause() = engine.pause()
    override fun resume() = engine.resume()

    override fun startOrRestore(): Boolean {
        val index = queue.currentIndex.takeIf { it in queue.tracks.indices } ?: return false
        playQueueSelection(queue, index)
        return true
    }

    override fun seek(positionSeconds: Double) = engine.seek(positionSeconds)

    override fun replayCurrent(positionSeconds: Double) {
        startCurrent(positionSeconds)
    }

    override fun setVolume(percent: Int) = engine.setVolume(percent)

    override fun stop() {
        generation += 1
        resolutionJob?.cancel()
        resolutionJob = null
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        engine.stop()
    }

    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        this.queue = queue
        if (clearPreparedNext) (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
    }

    override fun applyNavigation(command: PlaybackQueueNavigationCommand) {
        when (command) {
            PlaybackQueueNavigationCommand.Previous -> queue = queue.previous(repeatMode)
            PlaybackQueueNavigationCommand.Next -> queue = queue.next(repeatMode)
            is PlaybackQueueNavigationCommand.JumpTo -> queue = queue.jumpTo(command.index)
            PlaybackQueueNavigationCommand.RestartCurrent -> {
                engine.seek(0.0)
                return
            }
            PlaybackQueueNavigationCommand.None -> return
        }
        startCurrent(startPositionSeconds = null)
    }

    override fun applyRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        if (index !in queue.tracks.indices) return
        this.queue = queue.jumpTo(index)
        startCurrent(startPositionSeconds = null)
    }

    private fun startCurrent(startPositionSeconds: Double?) {
        val track = queue.current ?: return
        val requestGeneration = ++generation
        resolutionJob?.cancel()
        resolutionJob = scope.launch {
            val provider = providerSource.current()
            if (provider == null) {
                observer?.onStateChanged(PlaybackState.Error("Connect to Navidrome to play music."))
                return@launch
            }
            val playbackSettings = settings().effectiveForEngine(engine)
            val target = playbackTargetPlan(
                track = track,
                quality = playbackSettings.streamQualityForNetwork(isMobileData()),
                startPositionSeconds = startPositionSeconds,
                hasLocalAudio = false,
            )
            val streamUrl = runCatching { provider.streamUrl(target.providerStreamRequest) }
                .getOrElse { failure ->
                    if (requestGeneration == generation) {
                        observer?.onStateChanged(
                            PlaybackState.Error(failure.message ?: "Could not resolve the audio stream."),
                        )
                    }
                    return@launch
                }
            if (requestGeneration != generation) return@launch

            val work = planPlaylistTrackStartWork(
                sessionId = requestGeneration,
                track = track,
                playbackSource = PlaybackSource.ProviderStream,
                streamUrl = streamUrl,
                replayGainMode = playbackSettings.replayGainMode,
                replayGainPreampDb = playbackSettings.replayGainPreampDb,
                replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                supportsReplayGain = engine.supportsReplayGain,
                engineStartPositionSeconds = target.engineStartPositionSeconds,
                coverArtUrl = track.coverArtId?.let(provider::coverArtUrl),
            )
            engine.play(
                scope = scope,
                request = work.request,
                onStateChanged = { state ->
                    if (requestGeneration == generation) observer?.onStateChanged(state)
                },
                onProgressChanged = { progress ->
                    if (requestGeneration == generation) {
                        observer?.onProgressChanged(progress)
                        observer?.onVisualizerFrameChanged(
                            (engine as? VisualizerPlaybackEngine)?.visualizerFrame(),
                        )
                    }
                },
                onMetadataChanged = { metadata ->
                    if (requestGeneration == generation) observer?.onMetadataChanged(metadata)
                },
            )
            prepareNext(provider, playbackSettings, requestGeneration)
        }
    }

    private suspend fun prepareNext(
        provider: app.naviamp.domain.provider.MediaProvider,
        playbackSettings: PlaybackSettings,
        requestGeneration: Long,
    ) {
        val queueEngine = engine as? QueueAwarePlaybackEngine ?: return
        val nextIndex = queue.nextIndex(repeatMode, repeatTrack = true) ?: return
        val next = queue.tracks.getOrNull(nextIndex) ?: return
        val target = playbackTargetPlan(
            track = next,
            quality = playbackSettings.streamQualityForNetwork(isMobileData()),
            startPositionSeconds = null,
            hasLocalAudio = false,
        )
        val streamUrl = runCatching { provider.streamUrl(target.providerStreamRequest) }.getOrNull() ?: return
        if (requestGeneration != generation) return
        val work = planPlaylistTrackStartWork(
            sessionId = requestGeneration,
            track = next,
            playbackSource = PlaybackSource.ProviderStream,
            streamUrl = streamUrl,
            replayGainMode = playbackSettings.replayGainMode,
            replayGainPreampDb = playbackSettings.replayGainPreampDb,
            replayGain = next.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
            supportsReplayGain = engine.supportsReplayGain,
            engineStartPositionSeconds = null,
            coverArtUrl = next.coverArtId?.let(provider::coverArtUrl),
        )
        queueEngine.prepareNext(work.request)
    }
}

/** Shared playback-settings owner and complete engine application policy. */
class NaviampCorePlaybackEngineSettings(
    private val engine: PlaybackEngine,
    initial: PlaybackSettings = PlaybackSettings(),
    private val persist: (PlaybackSettings) -> Unit = {},
) : NaviampCorePlaybackSettingsPort {
    private var current = initial.effectiveForEngine(engine)

    init {
        applyToEngine(current)
    }

    fun current(): PlaybackSettings = current

    override fun apply(settings: PlaybackSettings, redownload: Boolean): PlaybackSettings {
        current = settings.effectiveForEngine(engine)
        applyToEngine(current)
        persist(current)
        return current
    }

    private fun applyToEngine(settings: PlaybackSettings) {
        engine.setVolume(settings.volumePercent)
        (engine as? QueueAwarePlaybackEngine)?.setCrossfadeDuration(settings.crossfadeDurationSeconds)
        (engine as? EqualizerPlaybackEngine)?.setEqualizer(settings.equalizer)
        (engine as? ReplayGainPlaybackEngine)?.setReplayGain(
            settings.replayGainMode,
            settings.replayGainPreampDb,
        )
        (engine as? SampleRateConverterPlaybackEngine)?.setSampleRateConverter(settings.sampleRateConverter)
        (engine as? SampleRateMatchingPlaybackEngine)?.setSampleRateMatching(settings.sampleRateMatching)
        (engine as? AudioOutputDevicePlaybackEngine)?.let { output ->
            val deviceId = settings.outputDevice.deviceId
                .takeIf { settings.outputDevice.mode == AudioOutputDeviceMode.Pinned }
            output.setAudioOutputDevice(deviceId)
        }
    }
}

/** Thread-safe enough for callback publication: immutable snapshots are replaced atomically. */
class NaviampCoreMutableNowPlayingSidecars : NaviampCoreNowPlayingSidecarPort {
    private var state = NaviampCoreNowPlayingSidecars()

    override fun snapshot(): NaviampCoreNowPlayingSidecars = state

    override suspend fun loadForTrack(track: app.naviamp.domain.Track) {
        state = NaviampCoreNowPlayingSidecars()
    }

    override suspend fun loadLyrics(track: app.naviamp.domain.Track) = Unit

    override suspend fun changeLyricsOffset(track: app.naviamp.domain.Track, offsetMillis: Int) = Unit

    override fun updateStreamMetadata(metadata: PlaybackStreamMetadata) {
        state = state.copy(streamMetadata = metadata)
    }

    override fun updateVisualizerFrame(frame: app.naviamp.domain.playback.PlaybackVisualizerFrame?) {
        state = state.copy(visualizerFrame = frame)
    }
}
