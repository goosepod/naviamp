package app.naviamp.android

import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingItemTarget
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.resolveAction

internal fun androidNowPlayingActions(
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    onSyncedSettingsChanged: () -> Unit,
    playbackController: AndroidPlaybackAppController,
    navigationController: AndroidNavigationController,
    mediaController: AndroidMediaAppController,
    shellPlaybackController: AndroidShellPlaybackController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
    sidecarController: AndroidNowPlayingSidecarController,
    settingsMaintenanceController: AndroidSettingsMaintenanceController,
    sleepTimerController: SleepTimerController,
): NaviampNowPlayingActions = NaviampNowPlayingActions(
    onPlaybackAction = { request ->
        when (request.action) {
            NowPlayingPlaybackAction.Pause,
            NowPlayingPlaybackAction.Resume,
            NowPlayingPlaybackAction.PlayCurrent,
            -> playbackController.handlePlayPauseCommand()
            NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let(playbackController::performSeek)
            NowPlayingPlaybackAction.Previous -> playbackController.playAdjacentTrack(-1)
            NowPlayingPlaybackAction.Next -> playbackController.playAdjacentTrack(1)
            NowPlayingPlaybackAction.ToggleShuffle -> shellPlaybackController.toggleShuffle()
            NowPlayingPlaybackAction.CycleRepeatMode -> {
                state.repeatMode = state.sharedQueueCoordinator.cycleRepeatMode()
            }
            NowPlayingPlaybackAction.ChangeVolume -> request.volumePercent?.let { percent ->
                state.volumePercent = playbackController.changeVolume(percent).volumePercent
            }
        }
    },
    onDisplayAction = { request ->
        when (request.action) {
            NowPlayingDisplayAction.ToggleLyrics -> {
                state.lyricsVisible = !state.lyricsVisible
                if (state.lyricsVisible) state.nowPlaying?.let(sidecarController::loadLyrics)
            }
            NowPlayingDisplayAction.ChangeLyricsOffset ->
                request.lyricsOffsetMillis?.let(sidecarController::handleLyricsOffsetChanged)
            NowPlayingDisplayAction.ToggleVisualizer ->
                run { state.visualizerRequestedVisible = !state.visualizerRequestedVisible }
            NowPlayingDisplayAction.SelectVisualizer -> request.visualizer?.let { visualizer ->
                state.selectedVisualizer = visualizer
                if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
                    state.nowPlaying?.let(sidecarController::loadLyrics)
                }
                settingsStore.saveVisualizerSettings(VisualizerSettings(selectedVisualizer = visualizer.name))
                onSyncedSettingsChanged()
            }
            NowPlayingDisplayAction.SelectRadioDj -> {
                val selectedDj = request.radioDjId
                    ?.let { id -> state.playbackSettings.radioDjs.firstOrNull { it.id == id } }
                settingsMaintenanceController.handlePlaybackSettingsChanged(
                    state.playbackSettings.copy(
                        radioTuning = selectedDj?.tuning ?: RadioTuningSettings(),
                        activeRadioDjId = selectedDj?.id,
                    ),
                )
                shellPlaybackController.startCurrentTrackRadio()
                state.status = selectedDj
                    ?.let { "Selected ${it.name} DJ. Rebuilding Up Next..." }
                    ?: "Default radio selected. Rebuilding Up Next..."
            }
            NowPlayingDisplayAction.Collapse -> {
                if (state.nowPlayingOpen) state.nowPlayingOpen = false else navigationController.closeActiveDetail()
            }
        }
    },
    onCurrentTrackAction = { request ->
        when (request.action) {
            NowPlayingCurrentTrackAction.StartRadio -> shellPlaybackController.startCurrentTrackRadio()
            NowPlayingCurrentTrackAction.AddToPlaylist ->
                trackActionController.handleNowPlayingAddToPlaylist(request.playlistChoice)
            NowPlayingCurrentTrackAction.CreatePlaylistAndAdd ->
                request.playlistName?.let(trackActionController::handleNowPlayingCreatePlaylistAndAdd)
            NowPlayingCurrentTrackAction.Download -> state.nowPlaying?.let(downloadActionController::downloadTrack)
            NowPlayingCurrentTrackAction.GoToAlbum -> shellMediaController.handleShellGoToAlbum()
            NowPlayingCurrentTrackAction.GoToArtist ->
                shellMediaController.handleShellGoToArtist(request.artistId, request.artistName)
            NowPlayingCurrentTrackAction.ToggleFavorite -> mediaController.toggleCurrentFavorite()
            NowPlayingCurrentTrackAction.SetRating -> shellMediaController.handleShellRatingSelected(request.rating)
        }
    },
    onQueueAction = { request ->
        when (request.action) {
            NowPlayingQueueAction.SaveQueueAsPlaylist ->
                request.playlistName?.let(playlistActionController::saveQueueAsPlaylist)
            NowPlayingQueueAction.MoveToNext -> request.queueIndex?.let(mediaController::moveQueueTrackNext)
            NowPlayingQueueAction.RemoveFromQueue -> request.queueIndex?.let(mediaController::removeFromQueue)
            NowPlayingQueueAction.EmptyQueue -> mediaController.emptyQueue()
        }
    },
    onSleepTimerAction = { request ->
        when (request.action) {
            NowPlayingSleepTimerAction.Select -> request.request?.let(sleepTimerController::select)
            NowPlayingSleepTimerAction.Cancel -> sleepTimerController.cancel()
        }
    },
    onSelectionAction = { request ->
        when (request.action) {
            NowPlayingSelectionAction.SelectQueueItem ->
                nowPlayingQueueIndex(request.item)?.let(playbackController::playQueueTrack)
            NowPlayingSelectionAction.SelectRelatedItem ->
                mediaController.resolveNowPlayingItemTrack(request.item)?.let { track ->
                    shellMediaController.handleShellTrackSelected(
                        SharedTrackRowUi(
                            id = track.id.value,
                            title = request.item.title,
                            subtitle = request.item.subtitle,
                            coverArtUrl = request.item.coverArtUrl,
                            meta = request.item.meta,
                        ),
                    )
                }
            NowPlayingSelectionAction.SelectRadioStation ->
                state.homeState.radioStations.firstOrNull { it.id == request.item.id }
                    ?.let(shellMediaController::handleRadioStationSelected)
        }
    },
    onQueueItemAction = { request ->
        handleNowPlayingQueueItemAction(
            request = request,
            mediaController = mediaController,
            shellPlaybackController = shellPlaybackController,
            shellMediaController = shellMediaController,
            trackActionController = trackActionController,
            playlistActionController = playlistActionController,
            downloadActionController = downloadActionController,
        )
    },
)

private fun handleNowPlayingQueueItemAction(
    request: NowPlayingItemActionRequest,
    mediaController: AndroidMediaAppController,
    shellPlaybackController: AndroidShellPlaybackController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
) {
    val action = request.resolveAction(fallbackTrack = mediaController.resolveNowPlayingItemTrack(request.item))
    when (action.action) {
        NowPlayingItemAction.StartRadio -> shellPlaybackController.startQueueItemRadio(action.item)
        NowPlayingItemAction.PlayTrackRadioNext -> action.track?.let(trackActionController::playTrackRadioNext)
        NowPlayingItemAction.AddTrackRadioToQueue -> action.track?.let(trackActionController::addTrackRadioToQueue)
        NowPlayingItemAction.PlayNext -> {
            nowPlayingQueueIndex(action.item)?.let(mediaController::moveQueueTrackNext)
                ?: mediaController.resolveNowPlayingItemTrack(action.item)?.let(mediaController::playNext)
        }
        NowPlayingItemAction.AddToQueue ->
            mediaController.resolveNowPlayingItemTrack(action.item)?.let(mediaController::addToQueue)
        NowPlayingItemAction.AddToPlaylist ->
            action.track?.let { playlistActionController.addTrackToPlaylist(it, action.playlistChoice, null) }
        NowPlayingItemAction.CreatePlaylistAndAdd ->
            action.track?.let { playlistActionController.addTrackToPlaylist(it, null, action.playlistName) }
        NowPlayingItemAction.Download -> action.track?.let(downloadActionController::downloadTrack)
        NowPlayingItemAction.GoToAlbum -> action.track?.let(shellMediaController::handleTrackGoToAlbum)
        NowPlayingItemAction.GoToArtist -> action.track?.let(shellMediaController::handleTrackGoToArtist)
        NowPlayingItemAction.ToggleFavorite -> action.track?.let(mediaController::toggleTrackFavorite)
        NowPlayingItemAction.RemoveFromQueue ->
            (request.target as? NowPlayingItemTarget.QueueIndex)?.let { mediaController.removeFromQueue(it.index) }
    }
}
