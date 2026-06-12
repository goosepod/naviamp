package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.label
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.ui.NaviampMiniNowPlaying
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampNowPlayingPanel
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampSleepTimerUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.MiniNowPlayingUiConfig
import app.naviamp.ui.NowPlayingCurrentTrackActionRequest
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.nowPlayingRelatedIndex
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toMiniNowPlayingUi
import app.naviamp.ui.toRadioNowPlayingUi
import app.naviamp.ui.nowPlayingTrackCapabilities
import app.naviamp.ui.toNowPlayingSectionsUi
import app.naviamp.ui.toTrackNowPlayingUi

@Composable
fun DesktopNowPlayingPanel(
    modifier: Modifier = Modifier,
    appColors: DesktopAppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsSoftwareVolume: Boolean,
    supportsTrackRadio: Boolean,
    supportsTrackFavorites: Boolean,
    supportsTrackRatings: Boolean,
    nowPlayingTrack: Track?,
    nowPlayingWaveform: AudioWaveform?,
    visualizerFrame: PlaybackVisualizerFrame?,
    selectedVisualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    nowPlayingAudioTags: List<AudioTag>?,
    nowPlayingLyrics: Lyrics?,
    nowPlayingLyricsStatus: String?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    lyricsVisible: Boolean,
    visualizerAvailable: Boolean,
    visualizerVisible: Boolean,
    coverArtUrl: String?,
    playbackQueue: PlaybackQueue,
    internetRadioStations: List<InternetRadioStation>,
    currentInternetRadioStationId: String?,
    radioTrackArtworkByKey: Map<String, String?>,
    relatedTracks: List<Track>,
    coverArtUrlForTrack: (Track) -> String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffleActive: Boolean,
    repeatMode: RepeatMode,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    volumePercent: Int,
    sleepTimer: NaviampSleepTimerUi,
    streamQuality: StreamQuality,
    sonicSimilarityEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleLyrics: () -> Unit,
    onLyricsOffsetChanged: (Int) -> Unit,
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onSleepTimerSelected: (SleepTimerRequest) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onInternetRadioStationSelected: (InternetRadioStation) -> Unit,
    onQueueIndexSelected: (Int) -> Unit,
    onRelatedTrackSelected: (Int) -> Unit,
    onCurrentTrackAction: (NowPlayingCurrentTrackActionRequest) -> Unit,
    onQueueItemAction: (NowPlayingItemActionRequest) -> Unit,
    onCollapseToHome: () -> Unit,
) {
    val isLiveStream = currentInternetRadioStationId != null
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val sections = remember(playbackQueue, relatedTracks, coverArtUrlForTrack, sonicSimilarityEnabled, repeatMode) {
        playbackQueue.toNowPlayingSectionsUi(
            relatedTracks = relatedTracks,
            coverArtUrl = coverArtUrlForTrack,
            sonicSimilarityEnabled = sonicSimilarityEnabled,
            repeatMode = repeatMode,
        )
    }
    val trackCapabilities = nowPlayingTrackCapabilities(
        isLiveStream = isLiveStream,
        playbackState = playbackState,
        hasPlaybackTarget = nowPlayingTrack != null || isLiveStream,
        supportsPause = supportsPause,
        supportsSeek = supportsSeek,
        supportsSoftwareVolume = supportsSoftwareVolume,
        supportsTrackRadio = supportsTrackRadio,
        supportsTrackFavorites = supportsTrackFavorites,
        supportsTrackRatings = supportsTrackRatings,
        canRepeatQueue = true,
        canSaveQueueAsPlaylist = true,
    )
    val radioStations = remember(internetRadioStations) {
        internetRadioStations
            .sortedBy { it.name.lowercase() }
            .map { station -> station.toNowPlayingStationUi() }
    }
    val nowPlayingUi = if (nowPlayingTrack != null) {
        nowPlayingTrack.toTrackNowPlayingUi(
            stateLabel = playbackState.label(),
            coverArtUrl = coverArtUrl,
            playbackProgress = playbackProgress,
            playbackState = playbackState,
            capabilities = trackCapabilities,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            shuffleEnabled = sections.shuffleEnabled,
            shuffleActive = shuffleActive,
            repeatMode = repeatMode,
            sleepTimer = sleepTimer,
            relatedLabels = sections.relatedLabels,
            playbackEngineName = playbackEngineName,
            waveform = nowPlayingWaveform,
            visualizerAvailable = visualizerAvailable,
            visualizerVisible = visualizerVisible,
            durationSeconds = effectiveDurationSeconds,
            lyricsVisible = lyricsVisible,
            lyricsStatus = nowPlayingLyricsStatus,
            lyrics = nowPlayingLyrics,
            streamQuality = streamQuality,
            embeddedTags = nowPlayingAudioTags?.map { it.key to it.value },
            useInlinePlaylistPicker = false,
            backTo = sections.backTo,
            upNext = sections.upNext,
            related = sections.related,
            volumePercent = volumePercent,
        ).copy(
            isLive = isLiveStream,
            radioStations = radioStations,
        )
    } else {
        internetRadioStations.firstOrNull { it.id == currentInternetRadioStationId }?.let { station ->
            station.toRadioNowPlayingUi(
                streamMetadata = nowPlayingStreamMetadata,
                playbackState = playbackState,
                volumePercent = volumePercent,
                radioStations = internetRadioStations,
                radioTrackArtworkByKey = radioTrackArtworkByKey,
                canPlayPause = trackCapabilities.canPlayPause,
                canChangeVolume = supportsSoftwareVolume,
            )
        } ?: NowPlayingUi(
            title = "Queue will appear here after connection",
            subtitle = if (isLiveStream) "Internet radio" else "Nothing Playing",
            stateLabel = playbackState.label(),
            coverArtUrl = coverArtUrl,
            volumePercent = volumePercent,
            isPlaying = playbackState == PlaybackState.Playing,
            isPaused = playbackState == PlaybackState.Paused,
            canPlayPause = trackCapabilities.canPlayPause,
            canChangeVolume = supportsSoftwareVolume,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            radioStations = radioStations,
        )
    }

    NaviampNowPlayingPanel(
        modifier = modifier,
        nowPlaying = nowPlayingUi,
        colors = appColors,
        visualizerBandsProvider = { visualizerFrame?.bands.orEmpty() },
        selectedVisualizer = selectedVisualizer,
        visualizerColors = visualizerColors,
        actions = NaviampNowPlayingActions(
            onPlaybackAction = { request ->
                when (request.action) {
                    NowPlayingPlaybackAction.Pause -> onPause()
                    NowPlayingPlaybackAction.Resume -> onResume()
                    NowPlayingPlaybackAction.PlayCurrent -> onPlayCurrent()
                    NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let(onSeek)
                    NowPlayingPlaybackAction.Previous -> onPrevious()
                    NowPlayingPlaybackAction.Next -> onNext()
                    NowPlayingPlaybackAction.ToggleShuffle -> onToggleShuffle()
                    NowPlayingPlaybackAction.CycleRepeatMode -> onCycleRepeatMode()
                    NowPlayingPlaybackAction.ChangeVolume -> request.volumePercent?.let(onVolumeChanged)
                }
            },
            onDisplayAction = { request ->
                when (request.action) {
                    NowPlayingDisplayAction.ToggleLyrics -> onToggleLyrics()
                    NowPlayingDisplayAction.ChangeLyricsOffset ->
                        request.lyricsOffsetMillis?.let(onLyricsOffsetChanged)
                    NowPlayingDisplayAction.ToggleVisualizer -> onToggleVisualizer()
                    NowPlayingDisplayAction.SelectVisualizer ->
                        request.visualizer?.let(onVisualizerSelected)
                    NowPlayingDisplayAction.Collapse -> onCollapseToHome()
                }
            },
            onCurrentTrackAction = { request ->
                nowPlayingTrack?.let { track ->
                    onCurrentTrackAction(
                        NowPlayingCurrentTrackActionRequest(
                            track = track,
                            action = request.action,
                            playlistChoice = request.playlistChoice,
                            playlistName = request.playlistName,
                            rating = request.rating,
                        ),
                    )
                }
            },
            onQueueAction = { request ->
                when (request.action) {
                    NowPlayingQueueAction.SaveQueueAsPlaylist -> onSaveQueueAsPlaylist(request.playlistName)
                }
            },
            onSleepTimerAction = { request ->
                when (request.action) {
                    NowPlayingSleepTimerAction.Select -> request.request?.let(onSleepTimerSelected)
                    NowPlayingSleepTimerAction.Cancel -> onCancelSleepTimer()
                }
            },
            onSelectionAction = { request ->
                when (request.action) {
                    NowPlayingSelectionAction.SelectQueueItem ->
                        nowPlayingQueueIndex(request.item)?.let(onQueueIndexSelected)
                    NowPlayingSelectionAction.SelectRelatedItem ->
                        nowPlayingRelatedIndex(request.item)?.let(onRelatedTrackSelected)
                    NowPlayingSelectionAction.SelectRadioStation ->
                        internetRadioStations.firstOrNull { it.id == request.item.id }
                            ?.let(onInternetRadioStationSelected)
                }
            },
            onQueueItemAction = onQueueItemAction,
        ),
    )
}

@Composable
fun DesktopMiniPlayerPanel(
    appColors: DesktopAppColors,
    nowPlayingTrack: Track?,
    coverArtUrl: String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playbackState: PlaybackState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val canTogglePause = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error
    NaviampMiniNowPlaying(
        nowPlaying = nowPlayingTrack.toMiniNowPlayingUi(
            MiniNowPlayingUiConfig(
                stateLabel = playbackState.label(),
                coverArtUrl = coverArtUrl,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canPlayPause = canTogglePause,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
            ),
        ),
        colors = appColors,
        onOpen = onOpenPlayer,
        onPause = onPause,
        onResume = onResume,
        onPlayCurrent = onPlayCurrent,
        onPrevious = onPrevious,
        onNext = onNext,
    )
}
