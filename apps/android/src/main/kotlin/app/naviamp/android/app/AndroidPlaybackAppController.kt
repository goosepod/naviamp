package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackAdjacentAction
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.planPlaybackAdjacentAction
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.shouldReplayCurrentForSeek
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
) {
    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        handleAndroidPlaybackProgressChanged(
            context = context,
            state = state,
            sessionToken = sessionToken,
            progress = progress,
            maybeReportPlayed = playbackReportController::maybeReportPlayed,
            prepareNextIfNeeded = playlistEngine::prepareNextIfNeeded,
        )
    }

    fun savePlaybackSession() {
        saveAndroidPlaybackSession(state, storage)
    }

    fun savePlaybackSessionThrottled(force: Boolean = false) {
        saveAndroidPlaybackSessionThrottled(state, storage, force)
    }

    fun playTrack(
        track: Track,
        queue: List<Track>? = null,
        openNowPlaying: Boolean = true,
        startPositionSeconds: Double? = null,
        keepRadioQueueActive: Boolean = false,
    ) {
        playAndroidTrack(
            scope = scope,
            state = state,
            audioAssets = audioAssets,
            playbackEngine = playbackEngine,
            playbackQueueController = queueController,
            track = track,
            queue = queue,
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
            station = station,
            savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
            handlePlaybackProgressChanged = ::handlePlaybackProgressChanged,
            onSyncedSettingsChanged = onSyncedSettingsChanged,
        )
    }

    fun performSeek(positionSeconds: Double) {
        val currentTrack = state.nowPlaying
        val seekPlan = planPlaybackSeek(
            isInternetRadioTrack = currentTrack?.isInternetRadioTrack() == true,
            positionSeconds = positionSeconds,
            currentProgress = state.playbackProgress,
            trackDurationSeconds = currentTrack?.durationSeconds,
            streamQuality = currentStreamQuality(),
            shouldReplayTranscodedStream = shouldReplayCurrentForSeek(PlaybackSource.ProviderStream),
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
        state.pendingSeekPositionSeconds = seekPlan.pendingSeekPositionSeconds
        state.pendingSeekIssuedAtMillis = System.currentTimeMillis()
        if (currentTrack != null && seekPlan.shouldReplayCurrent) {
            playTrack(
                track = currentTrack,
                queue = state.playbackQueue.tracks.takeIf { it.isNotEmpty() },
                openNowPlaying = false,
                startPositionSeconds = seekPlan.pendingSeekPositionSeconds,
            )
            return
        }
        playbackEngine.seek(seekPlan.pendingSeekPositionSeconds)
    }

    fun playAdjacentTrack(
        offset: Int,
        finishedTrack: Boolean = false,
    ) {
        when (
            val action = planPlaybackAdjacentAction(
                currentTrack = state.nowPlaying,
                activeQueue = activeQueue(),
                offset = offset,
                repeatMode = state.repeatMode,
                previousButtonBehavior = state.playbackSettings.previousButtonBehavior,
                positionSeconds = state.playbackProgress.positionSeconds,
                restartThresholdSeconds = 3.0,
            )
        ) {
            PlaybackAdjacentAction.None -> {
                if (offset > 0) appendSonicAutoplayAndAdvance()
            }
            PlaybackAdjacentAction.RestartCurrent -> performSeek(0.0)
            is PlaybackAdjacentAction.PlayTrack -> {
                val nextQueue = if (
                    finishedTrack &&
                    offset > 0 &&
                    state.playbackSettings.removePlayedTracksFromQueue
                ) {
                    val nextIndex = action.queue.indexOfFirst { track -> track.id == action.track.id }
                    PlaybackQueue(action.queue, nextIndex).removePlayedHistory().tracks
                } else {
                    action.queue
                }
                playTrack(
                    action.track,
                    nextQueue,
                    openNowPlaying = false,
                )
            }
        }
    }

    private fun appendSonicAutoplayAndAdvance() {
        if (!state.playbackSettings.sonicAutoplayEnabled) return
        if (state.provider?.capabilities?.supportsSonicSimilarity != true) return
        scope.launch {
            val tracks = sonicAutoplayService.continuationTracks(queueController.queue)
            val updatedQueue = queueController.appendTracks(tracks) ?: return@launch
            state.playbackQueue = updatedQueue
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
