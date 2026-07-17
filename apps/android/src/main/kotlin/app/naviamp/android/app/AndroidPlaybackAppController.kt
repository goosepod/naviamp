package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlaybackSeekRequest
import app.naviamp.domain.Album
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackQueueSelection
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class AndroidPlaybackAppController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val playbackSessions: NaviampPlaybackSessionController,
    private val queueCoordinator: NaviampPlaybackQueueCoordinator,
    private val settingsStore: AndroidSettingsStore,
    private val audioAssets: PlaybackAudioAssetRepository,
    private val playbackEngine: AndroidPlaybackEngine,
    private val queueController: PlaybackQueueController,
    private val playlistEngine: AndroidPlaylistEngine,
    private val playbackReportController: AndroidPlaybackReportController,
    private val sidecarController: AndroidNowPlayingSidecarController,
    private val activeQueue: () -> List<Track>,
    private val currentStreamQuality: () -> StreamQuality,
    private val loadRelatedTracks: (Track) -> Unit,
    private val sonicAutoplayService: SonicAutoplayService,
    private val onSyncedSettingsChanged: () -> Unit = {},
) : NaviampPlaybackExecution {
    private val playbackCommands = NaviampPlaybackCommandController(
        execution = this,
        playback = state.sharedLivePlaybackController,
    )

    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        handleAndroidPlaybackProgressChanged(
            context = context,
            state = state,
            sessionToken = sessionToken,
            progress = progress,
            maybeReportPlaybackState = playbackReportController::maybeReportPlaybackState,
            prepareNextIfNeeded = playlistEngine::prepareNextIfNeeded,
        )
    }

    fun savePlaybackSession() {
        saveAndroidPlaybackSession(state, playbackSessions)
    }

    fun savePlaybackSessionThrottled(force: Boolean = false) {
        saveAndroidPlaybackSessionThrottled(state, playbackSessions, force)
    }

    fun playTrack(
        track: Track,
        queue: List<Track>? = null,
        openNowPlaying: Boolean = true,
        startPositionSeconds: Double? = null,
        keepRadioQueueActive: Boolean = false,
        selectedQueue: PlaybackQueue? = null,
    ) {
        playAndroidTrack(
            scope = scope,
            state = state,
            audioAssets = audioAssets,
            playbackEngine = playbackEngine,
            playbackQueueController = queueController,
            queueCoordinator = queueCoordinator,
            track = track,
            queue = queue,
            selectedQueue = selectedQueue,
            openNowPlaying = openNowPlaying,
            startPositionSeconds = startPositionSeconds,
            keepRadioQueueActive = keepRadioQueueActive,
            activeQueue = activeQueue,
            currentStreamQuality = currentStreamQuality,
            savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
            reportNowPlaying = playbackReportController::reportNowPlaying,
            loadRelatedTracks = loadRelatedTracks,
            loadLyrics = sidecarController::loadLyrics,
            loadAudioTags = sidecarController::loadAudioTags,
            startAudioPrefetch = playlistEngine::startAudioPrefetch,
            startSidecarPrep = playlistEngine::startSidecarPrep,
            handlePlaybackProgressChanged = ::handlePlaybackProgressChanged,
            maybeReportPlaybackState = playbackReportController::maybeReportPlaybackState,
            playAdjacentTrack = ::playAdjacentTrack,
            coverArtUrl = { item, provider -> item.savedCoverArtUrl(provider, storage.latestNavidromeSource()) },
        )
    }

    fun playInternetRadioStation(station: InternetRadioStation) {
        playAndroidInternetRadioStation(
            scope = scope,
            state = state,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            playbackQueueController = queueController,
            queueCoordinator = queueCoordinator,
            station = station,
            savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
            handlePlaybackProgressChanged = ::handlePlaybackProgressChanged,
            onSyncedSettingsChanged = onSyncedSettingsChanged,
        )
    }

    fun performSeek(positionSeconds: Double) {
        val seekPlan = playbackCommands.seek(
            NaviampPlaybackSeekRequest(
                positionSeconds = positionSeconds,
                streamQuality = currentStreamQuality(),
                playbackSource = PlaybackSource.ProviderStream,
                issuedAtMillis = System.currentTimeMillis(),
            ),
        ) ?: return
        if (seekPlan.shouldClearRestoredStartPosition) {
            state.restoredStartPositionSeconds = null
            state.pendingRestoreStartPositionSeconds = null
        }
        state.playbackProgress = seekPlan.progress
        val positionMillis = state.playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = state.playbackProgress.durationSeconds?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
    }

    override fun seek(positionSeconds: Double) {
        playbackEngine.seek(positionSeconds)
    }

    override fun replayCurrent(positionSeconds: Double) {
        val currentTrack = state.nowPlaying
        if (currentTrack == null) {
            playbackEngine.seek(positionSeconds)
            return
        }
        playTrack(
            track = currentTrack,
            queue = state.playbackQueue.tracks.takeIf { it.isNotEmpty() },
            openNowPlaying = false,
            startPositionSeconds = positionSeconds,
        )
    }

    fun playAdjacentTrack(
        offset: Int,
        finishedTrack: Boolean = false,
    ) {
        if (
            offset < 0 &&
            queueCoordinator.previousCommand(
                previousButtonBehavior = state.playbackSettings.previousButtonBehavior,
            ) == PlaybackQueueNavigationCommand.RestartCurrent
        ) {
            performSeek(0.0)
            return
        }
        val selection = if (finishedTrack && offset > 0) {
            queueController.applyFinishedUpdate(
                queueCoordinator.finishCurrentTrack(
                    removePlayedTracksFromQueue = state.playbackSettings.removePlayedTracksFromQueue,
                ),
            )
        } else {
            queueController.applySelection(queueCoordinator.selectAdjacent(offset))
        } ?: run {
            if (offset > 0) appendSonicAutoplayAndAdvance()
            return
        }
        reportCurrentTrackStopped()
        playQueueSelection(selection)
    }

    fun playQueueTrack(index: Int) {
        if (!queueCoordinator.selectIndex(index).changed) return
        queueController.jumpTo(index)?.let { selection ->
            reportCurrentTrackStopped()
            playQueueSelection(selection)
        }
    }

    private fun reportCurrentTrackStopped() {
        playbackReportController.maybeReportPlaybackState(PlaybackState.Stopped, state.playbackProgress)
    }

    private fun playQueueSelection(
        selection: PlaybackQueueSelection,
    ) {
        val selectedQueue = selection.queue
        val selectedTrack = selectedQueue.current ?: return
        state.playbackQueue = selectedQueue
        playTrack(
            track = selectedTrack,
            queue = selectedQueue.tracks,
            openNowPlaying = false,
            selectedQueue = selectedQueue,
        )
    }

    private fun appendSonicAutoplayAndAdvance() {
        if (!state.playbackSettings.sonicAutoplayEnabled) return
        if (state.provider?.capabilities?.supportsSonicSimilarity != true) return
        scope.launch {
            val tracks = sonicAutoplayService.continuationTracks(queueController.queue)
            val previousQueue = state.playbackQueue
            val update = queueCoordinator.appendSonicContinuationTracks(tracks)
            if (!update.tracksChanged) return@launch
            queueController.replaceQueue(previousQueue)
            queueController.replaceQueue(update.queue)
            savePlaybackSessionThrottled(force = true)
            playAdjacentTrack(1)
        }
    }

    fun rememberRecentRadioStream(stream: RecentRadioStream) {
        val recentStreams = recentRadioStreamsWith(settingsStore.loadRecentRadioStreams(), stream)
        settingsStore.saveRecentRadioStreams(recentStreams)
        state.homeState = state.homeState.copy(recentRadioStreams = recentStreams)
        onSyncedSettingsChanged()
    }

    fun startTrackRadio(track: Track) {
        startAndroidTrackRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            track = track,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    fun startAlbumRadio(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        startAndroidAlbumRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            album = album,
            loadedAlbumTracks = loadedAlbumTracks,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }
}
