package app.naviamp.desktop

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioStationId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.resolveAction
import app.naviamp.ui.toNaviampSleepTimerUi

@Composable
internal fun ColumnScope.DesktopPlayerRouteContent(
    appColors: DesktopAppColors,
    playbackEngine: PlaybackEngine,
    connectedProvider: NavidromeProvider?,
    nowPlayingTrack: Track,
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
    cacheSettings: CacheSettings,
    sleepTimer: SleepTimerState?,
    sleepTimerNowEpochMillis: Long,
    onPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit,
    onDisplayAction: (NowPlayingDisplayActionRequest) -> Unit,
    onQueueAction: (NowPlayingQueueActionRequest) -> Unit,
    onSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit,
    onSelectionAction: (NowPlayingSelectionActionRequest) -> Unit,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
) {
    DesktopNowPlayingPanel(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        appColors = appColors,
        playbackEngineName = playbackEngine.name,
        supportsPause = playbackEngine.supportsPause,
        supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
        supportsTrackRadio = connectedProvider?.capabilities?.supportsTrackRadio == true,
        supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
        supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
        nowPlayingTrack = nowPlayingTrack,
        nowPlayingWaveform = nowPlayingController.waveform.takeIf { cacheSettings.waveformsEnabled },
        visualizerFrame = nowPlayingPresentation.visualizerFrame,
        selectedVisualizer = nowPlayingPresentation.selectedVisualizer,
        visualizerColors = nowPlayingPresentation.targetBackgroundColors,
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
        currentInternetRadioStationId = nowPlayingInternetRadioStationId ?: nowPlayingTrack.internetRadioStationId(),
        radioTrackArtworkByKey = nowPlayingPresentation.radioTrackArtworkByKey,
        relatedTracks = nowPlayingController.relatedTracks,
        relatedTracksSource = nowPlayingController.relatedTracksSource,
        relatedSimilarityByTrackId = nowPlayingController.relatedSimilarityByTrackId,
        coverArtUrlForTrack = { track -> track.coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
        hasPrevious = playbackController.canUsePreviousButton(),
        hasNext = playbackController.canUseNextButton(),
        shuffleActive = shuffledUpNextSnapshot != null,
        repeatMode = repeatMode,
        playbackState = playbackState,
        playbackProgress = playbackProgress,
        volumePercent = playbackSettings.volumePercent,
        sleepTimer = sleepTimer.toNaviampSleepTimerUi(sleepTimerNowEpochMillis),
        streamQuality = playbackSettings.streamQuality(playbackEngine),
        replayGainInspectorEnabled = playbackSettings.replayGainInspectorEnabled,
        replayGainMode = playbackSettings.replayGainMode,
        sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
        radioDjs = playbackSettings.radioDjs,
        activeRadioDjId = playbackSettings.activeRadioDjId,
        supportsSeek = playbackEngine.supportsSeek && !nowPlayingTrack.isInternetRadioTrack(),
        onPlaybackAction = onPlaybackAction,
        onDisplayAction = onDisplayAction,
        onQueueAction = onQueueAction,
        onSleepTimerAction = onSleepTimerAction,
        onSelectionAction = onSelectionAction,
        onCurrentTrackAction = { request ->
            when (request.action) {
                NowPlayingCurrentTrackAction.StartRadio ->
                    appActions.convertCurrentTrackToRadio(request.track)
                NowPlayingCurrentTrackAction.AddToPlaylist ->
                    playlistsController.openTrackAddToPlaylist(request.track)
                NowPlayingCurrentTrackAction.CreatePlaylistAndAdd -> Unit
                NowPlayingCurrentTrackAction.Download ->
                    appActions.downloadTrack(request.track)
                NowPlayingCurrentTrackAction.GoToAlbum ->
                    appActions.openTrackAlbumDetails(request.track)
                NowPlayingCurrentTrackAction.GoToArtist ->
                    appActions.openTrackArtistDetails(request.track)
                NowPlayingCurrentTrackAction.ToggleFavorite ->
                    appActions.toggleTrackFavorite(request.track)
                NowPlayingCurrentTrackAction.SetRating ->
                    appActions.setTrackRating(request.track, request.rating)
            }
        },
        onQueueItemAction = { request ->
            val action = request.resolveAction(
                queueTracks = playbackQueue.tracks,
                relatedTracks = nowPlayingController.relatedTracks,
            )
            when (action.action) {
                NowPlayingItemAction.StartRadio ->
                    action.track?.let(appActions::playTrackRadio)
                NowPlayingItemAction.PlayTrackRadioNext ->
                    action.track?.let(appActions::playTrackRadioNext)
                NowPlayingItemAction.AddTrackRadioToQueue ->
                    action.track?.let(appActions::addTrackRadioToQueue)
                NowPlayingItemAction.PlayNext -> {
                    if (action.isRelated) action.track?.let(playlistsController::playNext)
                }
                NowPlayingItemAction.AddToQueue -> {
                    if (action.isRelated) action.track?.let(playlistsController::addTrackToQueue)
                }
                NowPlayingItemAction.AddToPlaylist ->
                    action.track?.let(playlistsController::openTrackAddToPlaylist)
                NowPlayingItemAction.CreatePlaylistAndAdd -> Unit
                NowPlayingItemAction.Download ->
                    action.track?.let(appActions::downloadTrack)
                NowPlayingItemAction.GoToAlbum ->
                    action.track?.let(appActions::openTrackAlbumDetails)
                NowPlayingItemAction.GoToArtist ->
                    action.track?.let(appActions::openTrackArtistDetails)
                NowPlayingItemAction.RemoveFromQueue ->
                    nowPlayingQueueIndex(request.item)?.let { index ->
                        onQueueAction(
                            NowPlayingQueueActionRequest(
                                action = NowPlayingQueueAction.RemoveFromQueue,
                                queueIndex = index,
                            ),
                        )
                    }
            }
        },
    )
}
