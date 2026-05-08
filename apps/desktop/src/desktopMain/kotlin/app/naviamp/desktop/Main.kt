package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSettings

fun main() {
    configureDesktopAppearance()

    application {
        val playbackEngine = remember { PlaybackEngineFactory.createDefault() }
        Window(
            onCloseRequest = {
                playbackEngine.stop()
                exitApplication()
            },
            title = "Naviamp",
        ) {
            NaviampApp(playbackEngine)
        }
    }
}

private fun configureDesktopAppearance() {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", "system")
    }
}

@Composable
@Preview
fun NaviampApp(
    playbackEngine: PlaybackEngine = remember { PlaybackEngineFactory.createDefault() },
) {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) AppColors.Dark else AppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val settingsStore = remember { DesktopSettingsStore() }
    val playlistEngine = remember(playbackEngine) { PlaylistEngine(playbackEngine) }
    val coroutineScope = rememberCoroutineScope()
    var nowPlayingTrack by remember { mutableStateOf<Track?>(null) }
    var nowPlayingCoverArtUrl by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }
    var playbackSettings by remember { mutableStateOf(settingsStore.loadPlaybackSettings()) }

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.stop()
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = appColors.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ConnectionPanel(
                        appColors = appColors,
                        settingsStore = settingsStore,
                        playbackEngine = playbackEngine,
                        playlistEngine = playlistEngine,
                        playbackSettings = playbackSettings,
                        onPlaybackStarted = { track, coverArtUrl ->
                            nowPlayingTrack = track
                            nowPlayingCoverArtUrl = coverArtUrl
                        },
                        onQueueChanged = { queue ->
                            playbackQueue = queue
                        },
                        onPlaybackStateChanged = { state ->
                            playbackState = state
                        },
                        onPlaybackProgressChanged = { progress ->
                            playbackProgress = progress.mergeWith(playbackProgress)
                        },
                    )
                }
                PlaybackSettingsPanel(
                    appColors = appColors,
                    playbackSettings = playbackSettings,
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    onPlaybackSettingsChanged = { settings ->
                        playbackSettings = settings.forEngine(playbackEngine)
                        settingsStore.savePlaybackSettings(playbackSettings)
                    },
                )
                NowPlayingPanel(
                    appColors = appColors,
                    playbackEngineName = playbackEngine.name,
                    supportsPause = playbackEngine.supportsPause,
                    supportsSeek = playbackEngine.supportsSeek,
                    supportsGapless = playbackEngine.supportsGapless,
                    supportsCrossfade = playbackEngine.supportsCrossfade,
                    nowPlayingTrack = nowPlayingTrack,
                    coverArtUrl = nowPlayingCoverArtUrl,
                    upNext = playbackQueue.upNext(),
                    hasPrevious = playbackQueue.hasPrevious(),
                    hasNext = playbackQueue.hasNext(),
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    onPause = {
                        playbackEngine.pause()
                    },
                    onResume = {
                        playbackEngine.resume()
                    },
                    onSeek = { positionSeconds ->
                        playbackEngine.seek(positionSeconds)
                    },
                    onPrevious = {
                        playlistEngine.previous(coroutineScope)
                    },
                    onNext = {
                        playlistEngine.next(coroutineScope)
                    },
                    onStop = {
                        playlistEngine.clear()
                        playbackState = PlaybackState.Stopped
                        playbackProgress = PlaybackProgress.Unknown
                    },
                )
            }
        }
    }
}

private fun PlaybackSettings.forEngine(playbackEngine: PlaybackEngine): PlaybackSettings =
    if (playbackEngine.supportsReplayGain) {
        this
    } else {
        copy(replayGainMode = ReplayGainMode.Off)
    }
