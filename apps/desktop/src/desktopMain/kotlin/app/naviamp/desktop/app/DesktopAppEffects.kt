package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.DefaultNowPlayingHeartbeatIntervalMillis
import app.naviamp.domain.playback.DefaultVisualizerFrameIntervalMillis
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.shouldReportNowPlaying
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.jvmGeneratedCoverArtBytes
import app.naviamp.ui.resetJvmPlatformCoverArtByteLoader
import app.naviamp.ui.setJvmPlatformCoverArtByteLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DesktopAppEffects(
    playbackEngine: PlaybackEngine,
    playlistEngine: DesktopPlaylistEngine,
    imageCacheRepository: ImageCacheRepository,
    connectedProvider: NavidromeProvider?,
    nowPlayingTrack: Track?,
    playbackState: PlaybackState,
    nowPlayingVisualizerVisible: Boolean,
    appRoute: DesktopAppRoute,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    albumDetailBackRoute: DesktopAppRoute,
    artistDetailBackRoute: DesktopAppRoute,
    lastContentRoute: DesktopAppRoute,
    setLastContentRoute: (DesktopAppRoute) -> Unit,
    setNowPlayingVisualizerFrame: (PlaybackVisualizerFrame?) -> Unit,
    updateAudioCacheLimit: (Long) -> Unit,
    cancelAudioPrefetch: () -> Unit,
    saveNavigationSettings: (NavigationSettings) -> Unit,
) {
    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.stop()
        }
    }

    DisposableEffect(imageCacheRepository, connectedProvider) {
        setJvmPlatformCoverArtByteLoader { url ->
            jvmGeneratedCoverArtBytes(url)
                ?: connectedProvider
                ?.takeIf { it.ownsUrl(url) }
                ?.bytes(url)
                ?: imageCacheRepository.imageBytes(url)
        }
        onDispose {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    LaunchedEffect(playbackEngine, playbackSettings.gaplessEnabled, playbackSettings.crossfadeDurationSeconds) {
        val crossfadeDurationSeconds = playbackSettings.crossfadeDurationSeconds.coerceIn(0, 12)
        playlistEngine.setPlaybackTransitionSettings(
            gaplessEnabled = playbackSettings.gaplessEnabled,
            crossfadeSettings = CrossfadeSettings(
                enabled = !playbackSettings.gaplessEnabled && crossfadeDurationSeconds > 0,
                durationSeconds = crossfadeDurationSeconds,
            ),
        )
    }

    LaunchedEffect(playbackEngine, playbackSettings.volumePercent) {
        playbackEngine.setVolume(playbackSettings.volumePercent.coerceIn(0, 100))
    }

    LaunchedEffect(nowPlayingTrack?.id) {
        setNowPlayingVisualizerFrame(null)
    }

    LaunchedEffect(connectedProvider, nowPlayingTrack?.id, playbackState) {
        val provider = connectedProvider ?: return@LaunchedEffect
        val track = nowPlayingTrack ?: return@LaunchedEffect
        if (
            !shouldReportNowPlaying(
                supportsPlayReporting = provider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                playbackState = playbackState,
            )
        ) {
            return@LaunchedEffect
        }

        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    provider.reportNowPlaying(track.id)
                }
            }
            delay(DefaultNowPlayingHeartbeatIntervalMillis)
        }
    }

    LaunchedEffect(playbackEngine, playbackState, nowPlayingVisualizerVisible, appRoute) {
        val visualizerEngine = playbackEngine as? VisualizerPlaybackEngine
        if (visualizerEngine?.supportsVisualizer != true) {
            setNowPlayingVisualizerFrame(null)
            return@LaunchedEffect
        }
        if (!nowPlayingVisualizerVisible || appRoute != DesktopAppRoute.Player) {
            setNowPlayingVisualizerFrame(null)
            return@LaunchedEffect
        }
        while (
            nowPlayingVisualizerVisible &&
            appRoute == DesktopAppRoute.Player &&
            (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)
        ) {
            setNowPlayingVisualizerFrame(visualizerEngine.visualizerFrame())
            delay(DefaultVisualizerFrameIntervalMillis)
        }
        setNowPlayingVisualizerFrame(null)
    }

    LaunchedEffect(cacheSettings.maxAudioCacheBytes) {
        updateAudioCacheLimit(cacheSettings.maxAudioCacheBytes)
    }

    LaunchedEffect(cacheSettings.audioCachingEnabled, cacheSettings.audioPrefetchDepth) {
        if (!cacheSettings.audioCachingEnabled || cacheSettings.audioPrefetchDepth <= 0) {
            cancelAudioPrefetch()
        }
    }

    LaunchedEffect(appRoute, albumDetailBackRoute, artistDetailBackRoute) {
        if (
            appRoute == DesktopAppRoute.Player ||
            appRoute == DesktopAppRoute.AlbumDetail ||
            appRoute == DesktopAppRoute.ArtistDetail ||
            appRoute == DesktopAppRoute.PlaylistDetail
        ) {
            saveNavigationSettings(
                NavigationSettings(
                    route = appRoute.name,
                    lastContentRoute = if (appRoute == DesktopAppRoute.AlbumDetail) {
                        albumDetailBackRoute.name
                    } else if (appRoute == DesktopAppRoute.ArtistDetail) {
                        artistDetailBackRoute.name
                    } else {
                        lastContentRoute.name
                    },
                ),
            )
        } else {
            setLastContentRoute(appRoute)
            saveNavigationSettings(
                NavigationSettings(
                    route = appRoute.name,
                    lastContentRoute = appRoute.name,
                ),
            )
        }
    }
}
