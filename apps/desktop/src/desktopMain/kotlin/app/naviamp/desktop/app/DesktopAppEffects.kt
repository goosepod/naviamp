package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.DefaultNowPlayingHeartbeatIntervalMillis
import app.naviamp.domain.playback.DefaultDesktopVisualizerFrameIntervalMillis
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.SampleRateConverterPlaybackEngine
import app.naviamp.domain.playback.SampleRateMatchingPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.playbackVolumeCommand
import app.naviamp.domain.playback.shouldReportNowPlaying
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.settings.AudioOutputDeviceMode
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
    updateAudioCacheDirectory: (String?) -> Unit,
    updateDownloadDirectory: (String?) -> Unit,
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
                ?: connectedProvider?.takeIf { it.ownsUrl(url) }?.let { provider ->
                    imageCacheRepository.imageBytes(url) {
                        provider.bytes(url) ?: throw IllegalStateException("Could not download cover art.")
                    }
                }
                ?: imageCacheRepository.imageBytes(url)
        }
        onDispose {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    LaunchedEffect(
        playbackEngine,
        playbackSettings.gaplessEnabled,
        playbackSettings.crossfadeDurationSeconds,
        playbackSettings.removePlayedTracksFromQueue,
    ) {
        val crossfadeDurationSeconds = playbackSettings.crossfadeDurationSeconds.coerceIn(0, 12)
        playlistEngine.setPlaybackTransitionSettings(
            gaplessEnabled = playbackSettings.gaplessEnabled,
            crossfadeSettings = CrossfadeSettings(
                enabled = !playbackSettings.gaplessEnabled && crossfadeDurationSeconds > 0,
                durationSeconds = crossfadeDurationSeconds,
            ),
            removePlayedTracksFromQueue = playbackSettings.removePlayedTracksFromQueue,
        )
    }

    LaunchedEffect(playbackEngine, playbackSettings.volumePercent) {
        val command = playbackVolumeCommand(
            requestedPercent = playbackSettings.volumePercent,
            supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
        )
        if (command.shouldApplyToEngine) {
            playbackEngine.setVolume(command.volumePercent)
        }
    }

    LaunchedEffect(
        playbackEngine,
        playbackSettings.equalizer,
        playbackSettings.sampleRateConverter,
        playbackSettings.sampleRateMatching,
    ) {
        (playbackEngine as? EqualizerPlaybackEngine)?.setEqualizer(playbackSettings.equalizer)
        (playbackEngine as? SampleRateConverterPlaybackEngine)
            ?.setSampleRateConverter(playbackSettings.sampleRateConverter)
        (playbackEngine as? SampleRateMatchingPlaybackEngine)
            ?.setSampleRateMatching(playbackSettings.sampleRateMatching)
    }

    LaunchedEffect(playbackEngine, playbackSettings.outputDevice) {
        val outputEngine = playbackEngine as? AudioOutputDevicePlaybackEngine ?: return@LaunchedEffect
        val preference = playbackSettings.outputDevice.normalized()
        val deviceId = when (preference.mode) {
            AudioOutputDeviceMode.FollowSystem -> null
            AudioOutputDeviceMode.Pinned -> preference.deviceId
                ?.takeIf { id -> outputEngine.outputDevices().any { it.id == id && it.isEnabled } }
        }
        outputEngine.setAudioOutputDevice(deviceId)
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
            delay(DefaultDesktopVisualizerFrameIntervalMillis)
        }
        setNowPlayingVisualizerFrame(null)
    }

    LaunchedEffect(cacheSettings.maxAudioCacheBytes) {
        updateAudioCacheLimit(cacheSettings.maxAudioCacheBytes)
    }

    LaunchedEffect(cacheSettings.customAudioCacheDirectory) {
        updateAudioCacheDirectory(cacheSettings.customAudioCacheDirectory)
    }

    LaunchedEffect(cacheSettings.customDownloadDirectory) {
        updateDownloadDirectory(cacheSettings.customDownloadDirectory)
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
