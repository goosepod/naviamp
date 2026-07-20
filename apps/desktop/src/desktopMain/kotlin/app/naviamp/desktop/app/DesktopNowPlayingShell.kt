package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioStationId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingContentInput
import app.naviamp.ui.NaviampNowPlayingPresentationInput
import app.naviamp.ui.NaviampNowPlayingPresentationUi
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.nowPlayingTrackCapabilities
import app.naviamp.ui.rememberNaviampNowPlayingPresentation
import app.naviamp.ui.resolveAction
import app.naviamp.ui.toNaviampSleepTimerUi

@Composable
internal fun rememberDesktopNowPlayingPresentation(
    playbackEngine: PlaybackEngine,
    connectedProvider: NavidromeProvider?,
    nowPlayingTrack: Track?,
    nowPlayingController: DesktopNowPlayingController,
    nowPlayingPresentation: DesktopNowPlayingPresentationState,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    nowPlayingLyricsVisible: Boolean,
    nowPlayingVisualizerVisible: Boolean,
    playbackQueue: PlaybackQueue,
    internetRadioController: DesktopInternetRadioController,
    nowPlayingInternetRadioStationId: String?,
    playbackController: DesktopPlaybackController,
    shuffledUpNextSnapshot: List<Track>?,
    repeatMode: RepeatMode,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    interfaceSettings: InterfaceSettings,
    cacheSettings: CacheSettings,
    sleepTimer: SleepTimerState?,
    sleepTimerNowEpochMillis: Long,
): NaviampNowPlayingPresentationUi {
    val isLive = nowPlayingInternetRadioStationId != null || nowPlayingTrack?.isInternetRadioTrack() == true
    val coverArtUrlForTrack: (Track) -> String? = remember(connectedProvider) {
        { track -> track.coverArtId?.let { connectedProvider?.coverArtUrl(it) } }
    }
    val capabilities = nowPlayingTrackCapabilities(
        isLiveStream = isLive,
        playbackState = playbackState,
        hasPlaybackTarget = nowPlayingTrack != null || isLive,
        supportsPause = playbackEngine.supportsPause,
        supportsSeek = playbackEngine.supportsSeek && !isLive,
        supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
        supportsTrackRadio = connectedProvider?.capabilities?.supportsTrackRadio == true,
        supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
        supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
        canRepeatQueue = true,
        canSaveQueueAsPlaylist = true,
    )
    return rememberNaviampNowPlayingPresentation(
        NaviampNowPlayingPresentationInput(
            content = NaviampNowPlayingContentInput(
                stateLabel = playbackState.label(),
                playbackEngineName = playbackEngine.name,
                capabilities = capabilities,
                nowPlayingTrack = nowPlayingTrack,
                nowPlayingWaveform = nowPlayingController.waveform.takeIf { cacheSettings.waveformsEnabled },
                nowPlayingAudioTags = nowPlayingController.audioTags,
                nowPlayingLyrics = nowPlayingController.lyrics,
                nowPlayingLyricsStatus = nowPlayingController.lyricsStatus,
                nowPlayingStreamMetadata = nowPlayingStreamMetadata,
                lyricsVisible = nowPlayingLyricsVisible,
                visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                visualizerVisible = nowPlayingVisualizerVisible,
                coverArtUrl = nowPlayingPresentation.effectiveCoverArtUrl,
                playbackQueue = playbackQueue,
                internetRadioStations = internetRadioController.stations,
                currentInternetRadioStationId =
                    nowPlayingInternetRadioStationId ?: nowPlayingTrack?.internetRadioStationId(),
                radioTrackArtworkByKey = nowPlayingPresentation.radioTrackArtworkByKey,
                relatedTracks = nowPlayingController.relatedTracks,
                relatedTracksSource = nowPlayingController.relatedTracksSource,
                relatedSimilarityByTrackId = nowPlayingController.relatedSimilarityByTrackId,
                coverArtUrlForTrack = coverArtUrlForTrack,
                hasPrevious = playbackController.canUsePreviousButton(),
                hasNext = playbackController.canUseNextButton(),
                miniHasPrevious = playbackController.canUsePreviousButton(),
                miniHasNext = playbackQueue.hasNext(),
                shuffleActive = shuffledUpNextSnapshot != null,
                repeatMode = repeatMode,
                playbackState = playbackState,
                playbackProgress = playbackProgress,
                durationSeconds = nowPlayingTrack?.durationSeconds?.toDouble() ?: playbackProgress.durationSeconds,
                volumePercent = playbackSettings.volumePercent,
                sleepTimer = sleepTimer.toNaviampSleepTimerUi(sleepTimerNowEpochMillis),
                streamQuality = playbackSettings.streamQuality(playbackEngine),
                replayGainInspectorEnabled = playbackSettings.replayGainInspectorEnabled,
                replayGainMode = playbackSettings.replayGainMode,
                sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
                radioDjs = playbackSettings.radioDjs,
                activeRadioDjId = playbackSettings.activeRadioDjId,
                useInlinePlaylistPicker = false,
            ),
            displaySettings = interfaceSettings.nowPlaying,
            visualizerFrame = nowPlayingPresentation.visualizerFrame,
            selectedVisualizer = nowPlayingPresentation.selectedVisualizer,
            visualizerColors = nowPlayingPresentation.targetBackgroundColors,
        ),
    )
}

internal fun desktopNowPlayingActions(
    nowPlayingTrack: Track?,
    playbackQueue: PlaybackQueue,
    relatedTracks: List<Track>,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    onPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit,
    onDisplayAction: (NowPlayingDisplayActionRequest) -> Unit,
    onQueueAction: (NowPlayingQueueActionRequest) -> Unit,
    onSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit,
    onSelectionAction: (NowPlayingSelectionActionRequest) -> Unit,
): NaviampNowPlayingActions = NaviampNowPlayingActions(
    onPlaybackAction = onPlaybackAction,
    onDisplayAction = onDisplayAction,
    onCurrentTrackAction = { request ->
        nowPlayingTrack?.let { track ->
            when (request.action) {
                NowPlayingCurrentTrackAction.StartRadio -> appActions.convertCurrentTrackToRadio(track)
                NowPlayingCurrentTrackAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                NowPlayingCurrentTrackAction.CreatePlaylistAndAdd -> Unit
                NowPlayingCurrentTrackAction.Download -> appActions.downloadTrack(track)
                NowPlayingCurrentTrackAction.GoToAlbum -> appActions.openTrackAlbumDetails(track)
                NowPlayingCurrentTrackAction.GoToArtist -> appActions.openTrackArtistDetails(
                    track,
                    artistId = request.artistId,
                    artistName = request.artistName,
                )
                NowPlayingCurrentTrackAction.ToggleFavorite -> appActions.toggleTrackFavorite(track)
                NowPlayingCurrentTrackAction.SetRating -> appActions.setTrackRating(track, request.rating)
            }
        }
    },
    onQueueAction = onQueueAction,
    onSleepTimerAction = onSleepTimerAction,
    onSelectionAction = onSelectionAction,
    onQueueItemAction = { request ->
        val action = request.resolveAction(playbackQueue.tracks, relatedTracks)
        when (action.action) {
            NowPlayingItemAction.StartRadio -> action.track?.let(appActions::playTrackRadio)
            NowPlayingItemAction.PlayTrackRadioNext -> action.track?.let(appActions::playTrackRadioNext)
            NowPlayingItemAction.AddTrackRadioToQueue -> action.track?.let(appActions::addTrackRadioToQueue)
            NowPlayingItemAction.PlayNext -> {
                nowPlayingQueueIndex(request.item)?.let { index ->
                    onQueueAction(NowPlayingQueueActionRequest(NowPlayingQueueAction.MoveToNext, queueIndex = index))
                } ?: action.track?.let(playlistsController::playNext)
            }
            NowPlayingItemAction.AddToQueue -> {
                if (action.isRelated) action.track?.let(playlistsController::addTrackToQueue)
            }
            NowPlayingItemAction.AddToPlaylist -> action.track?.let(playlistsController::openTrackAddToPlaylist)
            NowPlayingItemAction.CreatePlaylistAndAdd -> Unit
            NowPlayingItemAction.Download -> action.track?.let(appActions::downloadTrack)
            NowPlayingItemAction.GoToAlbum -> action.track?.let(appActions::openTrackAlbumDetails)
            NowPlayingItemAction.GoToArtist -> action.track?.let(appActions::openTrackArtistDetails)
            NowPlayingItemAction.ToggleFavorite -> action.track?.let(appActions::toggleTrackFavorite)
            NowPlayingItemAction.RemoveFromQueue ->
                nowPlayingQueueIndex(request.item)?.let { index ->
                    onQueueAction(
                        NowPlayingQueueActionRequest(NowPlayingQueueAction.RemoveFromQueue, queueIndex = index),
                    )
                }
        }
    },
)
