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
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.radioArtworkUrl
import app.naviamp.ui.radioTrackArtworkKey
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toNowPlayingUi
import app.naviamp.ui.toMiniNowPlayingUi

@Composable
fun DesktopNowPlayingPanel(
    modifier: Modifier = Modifier,
    appColors: DesktopAppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsSoftwareVolume: Boolean,
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
    val backTo = playbackQueue.backTo()
    val upNext = playbackQueue.upNext()
    val firstBackToQueueIndex = playbackQueue.currentIndex - 1
    val firstUpNextQueueIndex = playbackQueue.currentIndex + 1
    val canTogglePlayback = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error &&
        (supportsPause || playbackState != PlaybackState.Playing)
    val backToItems = remember(backTo, firstBackToQueueIndex, coverArtUrlForTrack) {
        backTo.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "queue:${firstBackToQueueIndex - index}",
                coverArtUrl = coverArtUrlForTrack(track),
                meta = "",
            )
        }
    }
    val upNextItems = remember(upNext, firstUpNextQueueIndex, coverArtUrlForTrack) {
        upNext.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "queue:${firstUpNextQueueIndex + index}",
                coverArtUrl = coverArtUrlForTrack(track),
                meta = "",
            )
        }
    }
    val relatedItems = remember(relatedTracks, coverArtUrlForTrack) {
        relatedTracks.mapIndexed { index, track ->
            track.toNowPlayingItemUi(
                id = "related:$index",
                coverArtUrl = coverArtUrlForTrack(track),
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
            .map { station -> station.toNowPlayingStationUi() }
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
                shuffleEnabled = upNext.size > 1,
                shuffleActive = shuffleActive,
                repeatMode = repeatMode,
                canRepeat = !isLiveStream,
                canStartRadio = !isLiveStream,
                canAddToPlaylist = !isLiveStream,
                canSaveQueueAsPlaylist = !isLiveStream,
                sleepTimer = sleepTimer,
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
                relatedTabLabel = if (sonicSimilarityEnabled) "SONIC" else "RELATED",
                relatedEmptyLabel = if (sonicSimilarityEnabled) {
                    "Sonic matches are not loaded."
                } else {
                    "Related tracks are not loaded."
                },
            ),
        ).copy(
            isLive = isLiveStream,
            radioStations = radioStations,
        )
    } else {
        internetRadioStations.firstOrNull { it.id == currentInternetRadioStationId }?.let { station ->
            station.toNowPlayingUi(
            NowPlayingRadioUiConfig(
                streamTitle = nowPlayingStreamMetadata.title,
                coverArtUrl = radioArtworkUrl(
                    station = station,
                    streamMetadataProperties = nowPlayingStreamMetadata.properties,
                    trackArtworkUrl = radioTrackArtworkKey(station, nowPlayingStreamMetadata.title)
                        ?.let { radioTrackArtworkByKey[it] },
                ),
                stateLabel = playbackState.label(),
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canPlayPause = canTogglePlayback,
                canChangeVolume = supportsSoftwareVolume,
                radioStations = radioStations,
            ),
            )
        } ?: NowPlayingUi(
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
            onQueueItemPlayNext = { item ->
                itemTracks[item.id]?.takeIf { item.id.startsWith("related:") }?.let(onRelatedTrackPlayNext)
            },
            onQueueItemAddToQueue = { item ->
                itemTracks[item.id]?.takeIf { item.id.startsWith("related:") }?.let(onRelatedTrackAddToQueue)
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
