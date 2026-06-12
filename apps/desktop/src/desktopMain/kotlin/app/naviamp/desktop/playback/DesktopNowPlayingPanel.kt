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
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.nowPlayingRelatedIndex
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toMiniNowPlayingUi
import app.naviamp.ui.toRadioNowPlayingUi
import app.naviamp.ui.nowPlayingTrackCapabilities
import app.naviamp.ui.resolveAction
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
    onToggleTrackFavorite: (Track) -> Unit,
    onTrackRatingSelected: (Track, Int?) -> Unit,
    onArtistSelected: (Track) -> Unit,
    onAlbumSelected: (Track) -> Unit,
    onTrackRadioSelected: (Track) -> Unit,
    onDownloadTrackSelected: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onSleepTimerSelected: (SleepTimerRequest) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onInternetRadioStationSelected: (InternetRadioStation) -> Unit,
    onQueueIndexSelected: (Int) -> Unit,
    onUpNextTrackRadioSelected: (Track) -> Unit,
    onUpNextTrackDownloadSelected: (Track) -> Unit,
    onUpNextTrackAddToPlaylist: (Track) -> Unit,
    onRelatedTrackSelected: (Int) -> Unit,
    onRelatedTrackPlayNext: (Track) -> Unit,
    onRelatedTrackAddToQueue: (Track) -> Unit,
    onRelatedTrackRadioSelected: (Track) -> Unit,
    onRelatedTrackDownloadSelected: (Track) -> Unit,
    onRelatedTrackAddToPlaylist: (Track) -> Unit,
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
            onPause = onPause,
            onResume = onResume,
            onPlayCurrent = onPlayCurrent,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
            onVolumeChanged = onVolumeChanged,
            onToggleLyrics = onToggleLyrics,
            onLyricsOffsetChanged = onLyricsOffsetChanged,
            onToggleVisualizer = onToggleVisualizer,
            onVisualizerSelected = onVisualizerSelected,
            onTrackRadio = { nowPlayingTrack?.let(onTrackRadioSelected) },
            onAddToPlaylist = { nowPlayingTrack?.let(onAddTrackToPlaylist) },
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            onSleepTimerSelected = onSleepTimerSelected,
            onCancelSleepTimer = onCancelSleepTimer,
            onDownloadTrack = { nowPlayingTrack?.let(onDownloadTrackSelected) },
            onGoToAlbum = { nowPlayingTrack?.let(onAlbumSelected) },
            onGoToArtist = { nowPlayingTrack?.let(onArtistSelected) },
            onToggleFavorite = { nowPlayingTrack?.let(onToggleTrackFavorite) },
            onRatingSelected = { rating -> nowPlayingTrack?.let { onTrackRatingSelected(it, rating) } },
            onCollapse = onCollapseToHome,
            onQueueItemSelected = { item ->
                nowPlayingQueueIndex(item)?.let(onQueueIndexSelected)
            },
            onRelatedItemSelected = { item ->
                nowPlayingRelatedIndex(item)?.let(onRelatedTrackSelected)
            },
            onRadioStationSelected = { item ->
                internetRadioStations.firstOrNull { it.id == item.id }?.let(onInternetRadioStationSelected)
            },
            onQueueItemAction = { request ->
                val action = request.resolveAction(queueTracks = playbackQueue.tracks, relatedTracks = relatedTracks)
                when (action.action) {
                    NowPlayingItemAction.StartRadio -> action.track?.let {
                        if (action.isRelated) onRelatedTrackRadioSelected(it) else onUpNextTrackRadioSelected(it)
                    }
                    NowPlayingItemAction.PlayNext -> {
                        if (action.isRelated) action.track?.let(onRelatedTrackPlayNext)
                    }
                    NowPlayingItemAction.AddToQueue -> {
                        if (action.isRelated) action.track?.let(onRelatedTrackAddToQueue)
                    }
                    NowPlayingItemAction.AddToPlaylist -> action.track?.let {
                        if (action.isRelated) onRelatedTrackAddToPlaylist(it) else onUpNextTrackAddToPlaylist(it)
                    }
                    NowPlayingItemAction.CreatePlaylistAndAdd -> Unit
                    NowPlayingItemAction.Download -> action.track?.let {
                        if (action.isRelated) onRelatedTrackDownloadSelected(it) else onUpNextTrackDownloadSelected(it)
                    }
                }
            },
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
