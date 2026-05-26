package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.label
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.ui.NaviampMiniNowPlaying
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampNowPlayingPanel
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.MiniNowPlayingUiConfig
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toNowPlayingUi
import app.naviamp.ui.toMiniNowPlayingUi

@Composable
fun NowPlayingPanel(
    appColors: AppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsSoftwareVolume: Boolean,
    supportsTrackFavorites: Boolean,
    supportsTrackRatings: Boolean,
    nowPlayingTrack: Track?,
    nowPlayingWaveform: AudioWaveform?,
    visualizerBandsProvider: () -> List<Float>,
    selectedVisualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    nowPlayingAudioTags: List<AudioTag>?,
    nowPlayingLyrics: Lyrics?,
    nowPlayingLyricsStatus: String?,
    lyricsVisible: Boolean,
    visualizerAvailable: Boolean,
    visualizerVisible: Boolean,
    coverArtUrl: String?,
    backTo: List<Track>,
    upNext: List<Track>,
    internetRadioStations: List<InternetRadioStation>,
    currentInternetRadioStationId: String?,
    firstBackToQueueIndex: Int,
    firstUpNextQueueIndex: Int,
    upNextCoverArtUrl: (Track) -> String?,
    relatedTracks: List<Track>,
    relatedCoverArtUrl: (Track) -> String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffleEnabled: Boolean,
    shuffleActive: Boolean,
    repeatMode: RepeatMode,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    volumePercent: Int,
    streamQuality: StreamQuality,
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
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
    onToggleTrackFavorite: (Track) -> Unit,
    onTrackRatingSelected: (Track, Int?) -> Unit,
    onArtistSelected: (Track) -> Unit,
    onAlbumSelected: (Track) -> Unit,
    onTrackRadioSelected: (Track) -> Unit,
    onDownloadTrackSelected: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onInternetRadioStationSelected: (InternetRadioStation) -> Unit,
    onQueueIndexSelected: (Int) -> Unit,
    onUpNextTrackRadioSelected: (Track) -> Unit,
    onUpNextTrackDownloadSelected: (Track) -> Unit,
    onUpNextTrackAddToPlaylist: (Track) -> Unit,
    onRelatedTrackSelected: (Int) -> Unit,
    onRelatedTrackRadioSelected: (Track) -> Unit,
    onRelatedTrackDownloadSelected: (Track) -> Unit,
    onRelatedTrackAddToPlaylist: (Track) -> Unit,
    onCollapseToHome: () -> Unit,
) {
    val isLiveStream = currentInternetRadioStationId != null
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val canTogglePlayback = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error &&
        (supportsPause || playbackState != PlaybackState.Playing)
    val backToItems = remember(backTo, firstBackToQueueIndex, upNextCoverArtUrl) {
        backTo.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "queue:${firstBackToQueueIndex - index}",
                coverArtUrl = upNextCoverArtUrl(track),
                meta = "",
            )
        }
    }
    val upNextItems = remember(upNext, firstUpNextQueueIndex, upNextCoverArtUrl) {
        upNext.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "queue:${firstUpNextQueueIndex + index}",
                coverArtUrl = upNextCoverArtUrl(track),
                meta = "",
            )
        }
    }
    val relatedItems = remember(relatedTracks, relatedCoverArtUrl) {
        relatedTracks.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "related:$index",
                coverArtUrl = relatedCoverArtUrl(track),
                meta = "",
            )
        }
    }
    val itemTracks = remember(backTo, upNext, relatedTracks, firstBackToQueueIndex, firstUpNextQueueIndex) {
        buildMap {
            backTo.forEachIndexed { index, track -> put("queue:${firstBackToQueueIndex - index}", track) }
            upNext.forEachIndexed { index, track -> put("queue:${firstUpNextQueueIndex + index}", track) }
            relatedTracks.forEachIndexed { index, track -> put("related:$index", track) }
        }
    }
    val radioStations = remember(internetRadioStations) {
        internetRadioStations
            .sortedBy { it.name.lowercase() }
            .map { station ->
                NaviampNowPlayingItemUi(
                    id = station.id,
                    title = station.name,
                    subtitle = station.homePageUrl ?: station.streamUrl,
                )
            }
    }
    val nowPlayingUi = if (nowPlayingTrack != null) {
        nowPlayingTrack.toNowPlayingUi(
            NowPlayingTrackUiConfig(
                stateLabel = playbackState.label(),
                coverArtUrl = coverArtUrl,
                playbackEngineName = playbackEngineName,
                waveform = nowPlayingWaveform,
                visualizerAvailable = visualizerAvailable,
                visualizerVisible = visualizerVisible,
                positionSeconds = playbackProgress.positionSeconds,
                durationSeconds = effectiveDurationSeconds,
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canPlayPause = canTogglePlayback,
                canSeek = supportsSeek && !isLiveStream,
                canChangeVolume = supportsSoftwareVolume,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                shuffleEnabled = shuffleEnabled,
                shuffleActive = shuffleActive,
                repeatMode = repeatMode,
                canRepeat = !isLiveStream,
                canStartRadio = !isLiveStream,
                canAddToPlaylist = !isLiveStream,
                canFavorite = supportsTrackFavorites && !isLiveStream,
                canRate = supportsTrackRatings && !isLiveStream,
                lyricsAvailable = !isLiveStream,
                lyricsVisible = lyricsVisible,
                lyricsStatus = nowPlayingLyricsStatus,
                lyrics = nowPlayingLyrics,
                menuEnabled = true,
                streamQuality = streamQuality,
                embeddedTags = nowPlayingAudioTags?.map { it.key to it.value }
                    ?: listOf("Status" to "Loading from cached audio"),
                useInlinePlaylistPicker = false,
                backTo = backToItems,
                upNext = upNextItems,
                related = relatedItems,
            ),
        ).copy(
            isLive = isLiveStream,
            radioStations = radioStations,
        )
    } else {
        internetRadioStations.firstOrNull { it.id == currentInternetRadioStationId }?.toNowPlayingUi(
            NowPlayingRadioUiConfig(
                stateLabel = playbackState.label(),
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canPlayPause = canTogglePlayback,
                canChangeVolume = supportsSoftwareVolume,
                radioStations = radioStations,
            ),
        ) ?: NowPlayingUi(
            title = "Queue will appear here after connection",
            subtitle = if (isLiveStream) "Internet radio" else "Nothing Playing",
            stateLabel = playbackState.label(),
            coverArtUrl = coverArtUrl,
            volumePercent = volumePercent,
            isPlaying = playbackState == PlaybackState.Playing,
            isPaused = playbackState == PlaybackState.Paused,
            canPlayPause = canTogglePlayback,
            canChangeVolume = supportsSoftwareVolume,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            radioStations = radioStations,
        )
    }

    NaviampNowPlayingPanel(
        nowPlaying = nowPlayingUi,
        colors = appColors,
        visualizerBandsProvider = visualizerBandsProvider,
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
            onToggleVisualizer = onToggleVisualizer,
            onVisualizerSelected = onVisualizerSelected,
            onTrackRadio = { nowPlayingTrack?.let(onTrackRadioSelected) },
            onAddToPlaylist = { nowPlayingTrack?.let(onAddTrackToPlaylist) },
            onDownloadTrack = { nowPlayingTrack?.let(onDownloadTrackSelected) },
            onGoToAlbum = { nowPlayingTrack?.let(onAlbumSelected) },
            onGoToArtist = { nowPlayingTrack?.let(onArtistSelected) },
            onToggleFavorite = { nowPlayingTrack?.let(onToggleTrackFavorite) },
            onRatingSelected = { rating -> nowPlayingTrack?.let { onTrackRatingSelected(it, rating) } },
            onCollapse = onCollapseToHome,
            onQueueItemSelected = { item ->
                item.id.removePrefix("queue:").toIntOrNull()?.let(onQueueIndexSelected)
            },
            onRelatedItemSelected = { item ->
                item.id.removePrefix("related:").toIntOrNull()?.let(onRelatedTrackSelected)
            },
            onRadioStationSelected = { item ->
                internetRadioStations.firstOrNull { it.id == item.id }?.let(onInternetRadioStationSelected)
            },
            onQueueItemRadio = { item ->
                itemTracks[item.id]?.let {
                    if (item.id.startsWith("related:")) {
                        onRelatedTrackRadioSelected(it)
                    } else {
                        onUpNextTrackRadioSelected(it)
                    }
                }
            },
            onQueueItemAddToPlaylist = { item, _ ->
                itemTracks[item.id]?.let {
                    if (item.id.startsWith("related:")) {
                        onRelatedTrackAddToPlaylist(it)
                    } else {
                        onUpNextTrackAddToPlaylist(it)
                    }
                }
            },
            onQueueItemDownload = { item ->
                itemTracks[item.id]?.let {
                    if (item.id.startsWith("related:")) {
                        onRelatedTrackDownloadSelected(it)
                    } else {
                        onUpNextTrackDownloadSelected(it)
                    }
                }
            },
        ),
    )
}

@Composable
fun MiniPlayerPanel(
    appColors: AppColors,
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
