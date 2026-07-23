package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.label
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.ui.NaviampNowPlayingContentInput
import app.naviamp.ui.nowPlayingTrackCapabilities
import app.naviamp.ui.toNaviampSleepTimerUi
import app.naviamp.ui.toNowPlayingUi

data class NaviampCoreNowPlayingDisplayState(
    val lyricsVisible: Boolean = false,
    val visualizerVisible: Boolean = false,
    val sleepTimer: SleepTimerState? = null,
    val sleepTimerNowEpochMillis: Long = 0L,
    val playlistActionStatus: String? = null,
)

/** Maps the complete live playback graph into the one authoritative shared Now Playing state. */
class NaviampCoreNowPlayingPresenter(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val sidecars: NaviampCoreNowPlayingSidecarPort,
    private val network: NaviampCoreMobileNetworkPort = NaviampCoreMobileNetworkPort { false },
    private val internetRadioStations: () -> List<InternetRadioStation> = { emptyList() },
) {
    fun updateStreamMetadata(metadata: PlaybackStreamMetadata) {
        sidecars.updateStreamMetadata(metadata)
    }

    fun updateVisualizerFrame(frame: PlaybackVisualizerFrame?) {
        sidecars.updateVisualizerFrame(frame)
    }

    fun publish(display: NaviampCoreNowPlayingDisplayState = NaviampCoreNowPlayingDisplayState()) {
        val live = playback.state.value
        val shell = stateStore.state.value.shell
        val provider = providerSource.current()
        val sidecar = sidecars.snapshot()
        val track = live.currentTrack ?: live.queue.current
        val isLive = sidecar.currentInternetRadioStationId != null || track?.isInternetRadioTrack() == true
        val capabilities = nowPlayingTrackCapabilities(
            isLiveStream = isLive,
            playbackState = live.playbackState,
            hasPlaybackTarget = track != null || live.currentStation != null,
            supportsPause = effects.capabilities.supportsPause,
            supportsSeek = effects.capabilities.supportsSeek,
            supportsSoftwareVolume = effects.capabilities.supportsSoftwareVolume &&
                shell.capabilities.softwareVolumeControl,
            supportsTrackRadio = provider?.capabilities?.supportsTrackRadio == true,
            supportsTrackFavorites = provider?.capabilities?.supportsTrackFavorites == true,
            supportsTrackRatings = provider?.capabilities?.supportsTrackRatings == true,
            canRepeatQueue = true,
            canSaveQueueAsPlaylist = provider != null,
        )
        val playbackSettings = shell.playback.settings
        val coverArtForTrack: (app.naviamp.domain.Track) -> String? = { item ->
            item.coverArtId?.let { provider?.coverArtUrl(it) }
        }
        val nowPlaying = NaviampNowPlayingContentInput(
            stateLabel = live.playbackState.label(),
            playbackEngineName = effects.capabilities.engineName,
            capabilities = capabilities,
            nowPlayingTrack = track,
            nowPlayingWaveform = sidecar.waveform.takeIf { shell.cache.settings.waveformsEnabled },
            nowPlayingAudioTags = sidecar.audioTags,
            nowPlayingLyrics = sidecar.lyrics,
            nowPlayingLyricsStatus = sidecar.lyricsStatus,
            nowPlayingStreamMetadata = sidecar.streamMetadata,
            lyricsVisible = display.lyricsVisible,
            visualizerAvailable = effects.capabilities.supportsVisualizer,
            visualizerVisible = display.visualizerVisible && effects.capabilities.supportsVisualizer,
            coverArtUrl = track?.let(coverArtForTrack),
            playbackQueue = live.queue,
            internetRadioStations = (
                internetRadioStations() +
                    sidecar.internetRadioStations +
                    listOfNotNull(live.currentStation)
                )
                .distinctBy { it.id },
            currentInternetRadioStationId = live.currentStation?.id,
            radioTrackArtworkByKey = sidecar.radioTrackArtworkByKey,
            relatedTracks = sidecar.relatedTracks,
            relatedTracksSource = sidecar.relatedTracksSource,
            relatedSimilarityByTrackId = sidecar.relatedSimilarityByTrackId,
            coverArtUrlForTrack = coverArtForTrack,
            hasPrevious = queue.canUsePreviousButton(playbackSettings.previousButtonBehavior),
            hasNext = queue.canUseNextButton(),
            shuffleActive = live.shuffledUpNextSnapshot != null,
            repeatMode = live.repeatMode,
            playbackState = live.playbackState,
            playbackProgress = live.progress,
            durationSeconds = track?.durationSeconds?.toDouble() ?: live.progress.durationSeconds,
            volumePercent = playbackSettings.volumePercent,
            sleepTimer = display.sleepTimer.toNaviampSleepTimerUi(display.sleepTimerNowEpochMillis),
            streamQuality = playbackSettings.streamQualityForNetwork(network.isActiveNetworkMobileData()),
            replayGainInspectorEnabled = playbackSettings.replayGainInspectorEnabled,
            replayGainMode = playbackSettings.replayGainMode,
            sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
            radioDjs = playbackSettings.radioDjs,
            activeRadioDjId = playbackSettings.activeRadioDjId,
            playlistChoices = shell.playlistChoices,
            playlistActionStatus = display.playlistActionStatus,
        ).toNowPlayingUi().copy(visualizerFrame = sidecar.visualizerFrame)
        stateStore.updateShell { current -> current.copy(nowPlaying = nowPlaying) }
    }
}
