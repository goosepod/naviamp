package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampPlaybackQueueCommandController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackRepeatCommandController
import app.naviamp.app.NaviampPlaybackSeekRequest
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.sleepTimerSelection
import app.naviamp.domain.playback.PlaybackQueueSelectionUpdate
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest

/** Owns Now Playing transport, queue commands, volume, repeat/shuffle, and sleep-timer policy. */
class NaviampCorePlaybackController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val settings: NaviampCorePlaybackSettingsPort,
    private val presenter: NaviampCoreNowPlayingPresenter,
    private val nowEpochMillis: () -> Long,
) : NaviampCoreCommandController {
    private var display = NaviampCoreNowPlayingDisplayState()
    private val commands = NaviampPlaybackCommandController(effects, playback)
    private val mutations = NaviampPlaybackQueueCommandController(queue) { update ->
        effects.applyQueue(update.queue, update.clearPreparedNext)
    }
    private val repeat = NaviampPlaybackRepeatCommandController(queue, effects::applyRepeatMode)

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.NowPlaying.Playback,
        is NaviampCoreCommand.NowPlaying.Queue,
        is NaviampCoreCommand.NowPlaying.SleepTimer,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.NowPlaying.Playback -> playback(command.request)
            is NaviampCoreCommand.NowPlaying.Queue -> queue(command.request)
            is NaviampCoreCommand.NowPlaying.SleepTimer -> sleepTimer(command.request)
            else -> return null
        }
        presenter.publish(display)
        return NaviampCoreCommandResult.Completed
    }

    fun updateLiveState(
        transform: (app.naviamp.app.NaviampLivePlaybackState) -> app.naviamp.app.NaviampLivePlaybackState,
    ) {
        playback.replace(transform(playback.state.value))
        presenter.publish(display)
    }

    fun updateDisplay(transform: (NaviampCoreNowPlayingDisplayState) -> NaviampCoreNowPlayingDisplayState) {
        display = transform(display)
        presenter.publish(display)
    }

    fun currentDisplay(): NaviampCoreNowPlayingDisplayState = display

    fun attachNativePlayback() {
        effects.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) {
                playback.updatePlaybackState(state)
                if (state == PlaybackState.Finished) {
                    navigate(queue.nextCommand())
                }
                presenter.publish(display)
            }

            override fun onProgressChanged(progress: PlaybackProgress) {
                playback.updateProgress(progress)
                presenter.publish(display)
            }

            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) {
                presenter.updateStreamMetadata(metadata)
                presenter.publish(display)
            }

            override fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) {
                presenter.updateVisualizerFrame(frame)
                presenter.publish(display)
            }
        })
    }

    private fun playback(request: NowPlayingPlaybackActionRequest) {
        val playbackSettings = stateStore.state.value.shell.playback.settings
        when (request.action) {
            NowPlayingPlaybackAction.Pause,
            NowPlayingPlaybackAction.Resume,
            NowPlayingPlaybackAction.PlayCurrent,
            -> if (!commands.playPause()) publishStatus("Nothing is available to play.")
            NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let { seconds ->
                commands.seek(
                    NaviampPlaybackSeekRequest(
                        positionSeconds = seconds,
                        streamQuality = playbackSettings.streamQualityForNetwork(false),
                        playbackSource = effects.playbackSource,
                        issuedAtMillis = nowEpochMillis(),
                    ),
                ) ?: publishStatus("This stream cannot be seeked.")
            } ?: publishStatus("Seek position is missing.")
            NowPlayingPlaybackAction.Previous ->
                navigate(queue.previousCommand(playbackSettings.previousButtonBehavior))
                    .publishIfUnavailable("No previous track is available.")
            NowPlayingPlaybackAction.Next ->
                navigate(queue.nextCommand()).publishIfUnavailable("No next track is available.")
            NowPlayingPlaybackAction.ToggleShuffle -> {
                val update = queue.toggleUpcomingShuffle()
                if (update.changed) effects.applyQueue(update.queue, clearPreparedNext = true)
                else publishStatus("There are not enough upcoming tracks to shuffle.")
            }
            NowPlayingPlaybackAction.CycleRepeatMode -> repeat.cycle()
            NowPlayingPlaybackAction.ChangeVolume -> {
                val requested = request.volumePercent
                if (requested == null) {
                    publishStatus("Volume value is missing.")
                } else {
                    val command = commands.changeVolume(requested, effects.capabilities.supportsSoftwareVolume)
                    val updated = playbackSettings.copy(volumePercent = command.volumePercent)
                    settings.apply(updated, redownload = false)
                    stateStore.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
                }
            }
        }
    }

    private suspend fun queue(request: NowPlayingQueueActionRequest) {
        when (request.action) {
            NowPlayingQueueAction.SaveQueueAsPlaylist -> saveQueue(request.playlistName)
            NowPlayingQueueAction.MoveToNext -> request.queueIndex?.let(mutations::moveToNext)
                ?: publishStatus("Queue position is missing.")
            NowPlayingQueueAction.RemoveFromQueue -> request.queueIndex?.let(mutations::removeAt)
                ?: publishStatus("Queue position is missing.")
            NowPlayingQueueAction.EmptyQueue -> {
                val update = queue.clearQueue()
                if (update.changed) {
                    effects.applyQueue(update.queue, update.clearPreparedNext)
                    commands.stop()
                    playback.replace(
                        playback.state.value.copy(
                            currentTrack = null,
                            currentStation = null,
                            playbackState = PlaybackState.Stopped,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun saveQueue(requestedName: String?) {
        val provider = providerSource.current()
        if (provider == null) {
            publishPlaylistStatus("Connect to Navidrome to save the queue.")
            return
        }
        val name = requestedName?.trim().orEmpty()
        val tracks = playback.state.value.queue.tracks
        when {
            name.isEmpty() -> publishPlaylistStatus("Playlist name cannot be blank.")
            tracks.isEmpty() -> publishPlaylistStatus("The queue is empty.")
            else -> runCatching { provider.createPlaylist(name, tracks.map { it.id }) }
                .onSuccess { publishPlaylistStatus("Saved $name.") }
                .onFailure { publishPlaylistStatus(it.message ?: "Could not save queue.") }
        }
    }

    private fun sleepTimer(request: NowPlayingSleepTimerActionRequest) {
        when (request.action) {
            NowPlayingSleepTimerAction.Select -> {
                val selectionRequest = request.request
                if (selectionRequest == null) {
                    publishStatus("Sleep timer selection is missing.")
                    return
                }
                display = display.withSleepTimer(selectionRequest)
                publishStatus(display.sleepTimer?.let { app.naviamp.domain.playback.sleepTimerDisplayLabel(it, display.sleepTimerNowEpochMillis) }
                    ?: "Sleep timer")
            }
            NowPlayingSleepTimerAction.Cancel -> {
                display = display.copy(sleepTimer = null, sleepTimerNowEpochMillis = nowEpochMillis())
                publishStatus("Sleep timer canceled.")
            }
        }
    }

    fun expireSleepTimer() {
        commands.stop()
        playback.updatePlaybackState(PlaybackState.Stopped)
        display = display.copy(sleepTimer = null, sleepTimerNowEpochMillis = nowEpochMillis())
        publishStatus("Sleep timer stopped playback.")
        presenter.publish(display)
    }

    fun tickSleepTimer(nowMillis: Long) {
        display = display.copy(sleepTimerNowEpochMillis = nowMillis)
        presenter.publish(display)
    }

    private fun NaviampCoreNowPlayingDisplayState.withSleepTimer(
        request: SleepTimerRequest,
    ): NaviampCoreNowPlayingDisplayState {
        val live = playback.state.value
        val selection = sleepTimerSelection(
            request = request,
            nowEpochMillis = nowEpochMillis(),
            nowPlaying = live.currentTrack ?: live.queue.current,
            playbackQueue = live.queue,
            playbackProgress = live.progress,
            playbackState = live.playbackState,
        )
        return copy(sleepTimer = selection.timer, sleepTimerNowEpochMillis = selection.nowEpochMillis)
    }

    private fun PlaybackQueueNavigationCommand.publishIfUnavailable(message: String) {
        if (this == PlaybackQueueNavigationCommand.None) publishStatus(message)
    }

    private fun navigate(command: PlaybackQueueNavigationCommand): PlaybackQueueNavigationCommand {
        val selection = when (command) {
            PlaybackQueueNavigationCommand.Previous -> queue.selectPrevious()
            PlaybackQueueNavigationCommand.Next -> queue.selectNext()
            is PlaybackQueueNavigationCommand.JumpTo ->
                queue.selectIndex(command.index, command.moveSelectedToCurrent)
            PlaybackQueueNavigationCommand.RestartCurrent,
            PlaybackQueueNavigationCommand.None,
            -> null
        }
        selection?.applyToLivePlayback()
        if (command != PlaybackQueueNavigationCommand.None) effects.applyNavigation(command)
        return command
    }

    private fun PlaybackQueueSelectionUpdate.applyToLivePlayback() {
        if (!changed) return
        playback.updateCurrentTrack(queue.current)
    }

    private fun publishPlaylistStatus(message: String) {
        display = display.copy(playlistActionStatus = message)
        publishStatus(message)
    }

    private fun publishStatus(message: String) {
        stateStore.update { state -> state.copy(overlays = state.overlays.copy(status = message)) }
    }
}
