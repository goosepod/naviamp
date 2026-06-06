package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.app.StorageStatsRefreshIntervalMillis
import app.naviamp.domain.app.shouldRefreshStorageStats
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.library.LibraryFreshnessCheckIntervalMillis
import app.naviamp.domain.playback.DefaultNowPlayingHeartbeatIntervalMillis
import app.naviamp.domain.playback.DefaultVisualizerFrameIntervalMillis
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.shouldReportNowPlaying
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AndroidAppRuntimeEffects(
    state: AndroidAppState,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
    openNowPlayingRequest: Int,
    autoPlayMediaIdRequest: String?,
    autoCommandRequest: String?,
) {
    with(state) {
        LaunchedEffect(bassLoadReport) {
            if (!bassLoadReport.available) {
                status = "BASS libraries are bundled but did not load on this device."
            }
        }

        LaunchedEffect(nowPlaying?.id, nowPlayingStation?.id) {
            visualizerFrame = null
        }

        LaunchedEffect(provider, nowPlaying?.id, playbackState) {
            val activeProvider = provider ?: return@LaunchedEffect
            val track = nowPlaying ?: return@LaunchedEffect
            if (
                !shouldReportNowPlaying(
                    supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                    isInternetRadioTrack = track.isInternetRadioTrack(),
                    playbackState = playbackState,
                )
            ) {
                return@LaunchedEffect
            }

            while (true) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        activeProvider.reportNowPlaying(track.id)
                    }
                }
                delay(DefaultNowPlayingHeartbeatIntervalMillis)
            }
        }

        LaunchedEffect(playbackEngine, playbackState, visualizerVisible, nowPlayingOpen) {
            val visualizerEngine = playbackEngine as? VisualizerPlaybackEngine
            if (visualizerEngine?.supportsVisualizer != true) {
                visualizerFrame = null
                return@LaunchedEffect
            }
            if (!visualizerVisible || !nowPlayingOpen) {
                visualizerFrame = null
                return@LaunchedEffect
            }
            while (visualizerVisible && nowPlayingOpen && (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)) {
                visualizerFrame = visualizerEngine.visualizerFrame()
                delay(DefaultVisualizerFrameIntervalMillis)
            }
            visualizerFrame = null
        }

        LaunchedEffect(openNowPlayingRequest) {
            if (openNowPlayingRequest > 0) {
                pendingOpenNowPlayingFromIntent = true
            }
        }

        LaunchedEffect(pendingOpenNowPlayingFromIntent, nowPlaying, nowPlayingStation) {
            if (pendingOpenNowPlayingFromIntent && (nowPlaying != null || nowPlayingStation != null)) {
                nowPlayingOpen = true
                editingConnection = false
                pendingOpenNowPlayingFromIntent = false
            }
        }

        LaunchedEffect(autoPlayMediaIdRequest) {
            if (!autoPlayMediaIdRequest.isNullOrBlank()) {
                pendingAutoPlayMediaId = autoPlayMediaIdRequest
            }
        }

        LaunchedEffect(autoCommandRequest) {
            if (!autoCommandRequest.isNullOrBlank()) {
                pendingAutoCommand = autoCommandRequest
            }
        }

        LaunchedEffect(playbackEngine, playbackSettings.crossfadeDurationSeconds) {
            (playbackEngine as? QueueAwarePlaybackEngine)
                ?.setCrossfadeDuration(playbackSettings.crossfadeDurationSeconds)
        }

        LaunchedEffect(playbackEngine, playbackSettings.equalizer) {
            (playbackEngine as? EqualizerPlaybackEngine)?.setEqualizer(playbackSettings.equalizer)
        }

        DisposableEffect(playbackEngine) {
            onDispose {
                // Playback intentionally outlives the activity so Android Auto and the notification
                // remain useful after the phone UI is swiped away.
            }
        }
    }
}

@Composable
fun AndroidAppPersistenceEffects(
    state: AndroidAppState,
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    savePlaybackSessionThrottled: (force: Boolean) -> Unit,
    checkAndroidLibraryFreshness: () -> Unit,
) {
    with(state) {
        LaunchedEffect(
            activeSourceId,
            playbackQueue,
            nowPlaying?.id,
            nowPlayingStation?.id,
        ) {
            savePlaybackSessionThrottled(true)
        }

        LaunchedEffect(
            activeSourceId,
            nowPlaying?.id,
            nowPlayingStation?.id,
            playbackProgress.positionSeconds,
        ) {
            savePlaybackSessionThrottled(false)
        }

        DisposableEffect(activeSourceId, nowPlaying?.id, nowPlayingStation?.id, playbackQueue) {
            onDispose {
                savePlaybackSessionThrottled(true)
            }
        }

        LaunchedEffect(selectedRoute, activeSourceId, nowPlaying?.id, nowPlayingStation?.id) {
            if (!shouldRefreshStorageStats(navigationState.route)) return@LaunchedEffect
            while (true) {
                storageStats = withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() }
                delay(StorageStatsRefreshIntervalMillis)
            }
        }

        LaunchedEffect(provider, activeSourceId) {
            if (provider == null || activeSourceId == null) return@LaunchedEffect
            checkAndroidLibraryFreshness()
            while (true) {
                delay(LibraryFreshnessCheckIntervalMillis)
                checkAndroidLibraryFreshness()
            }
        }

        LaunchedEffect(activeSourceId, selectedRoute, downloadRefreshToken) {
            val sourceId = activeSourceId ?: return@LaunchedEffect
            if (selectedRoute != SharedRoute.Downloads) return@LaunchedEffect
            downloadedTracks = withContext(Dispatchers.IO) {
                downloadRepository.downloadedTracks(sourceId)
            }
        }
    }
}
