package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NaviampSleepTimerExpiryEffect
import kotlinx.coroutines.CoroutineScope

internal data class DesktopPlaybackGraphBindings(
    val provider: () -> NavidromeProvider?,
    val sourceId: () -> String?,
    val playbackSettings: () -> PlaybackSettings,
    val cacheSettings: () -> CacheSettings,
    val route: () -> NaviampRoute,
    val lyricsVisible: () -> Boolean,
    val playbackQueue: () -> PlaybackQueue,
    val playbackProgress: () -> PlaybackProgress,
    val setPlaybackProgress: (PlaybackProgress) -> Unit,
    val playbackState: () -> PlaybackState,
    val nowPlayingTrack: () -> Track?,
    val nowPlayingCoverArtUrl: () -> String?,
    val playReportSessionId: () -> Int,
    val setRepeatMode: (RepeatMode) -> Unit,
    val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    val setSleepTimer: (SleepTimerState?) -> Unit,
    val setSleepTimerNowEpochMillis: (Long) -> Unit,
    val setStatus: (String?) -> Unit,
)

internal data class DesktopNowPlayingSnapshot(
    val initialVisualizerSettings: VisualizerSettings,
    val appColors: DesktopAppColors,
    val interfaceSettings: app.naviamp.domain.settings.InterfaceSettings,
    val currentCoverArtUrl: String?,
    val track: Track?,
    val station: InternetRadioStation?,
    val streamMetadata: PlaybackStreamMetadata,
    val provider: NavidromeProvider?,
    val playbackState: PlaybackState,
    val sleepTimer: SleepTimerState?,
)

internal data class DesktopPlaybackControllerGraph(
    val presentation: DesktopNowPlayingPresentationState,
    val playback: DesktopPlaybackController,
    val sleepTimer: SleepTimerController,
    val nowPlaying: DesktopNowPlayingController,
    val visualizerVisible: Boolean,
)

@Composable
internal fun rememberDesktopPlaybackControllerGraph(
    dependencies: DesktopAppDependencies,
    scope: CoroutineScope,
    playbackSessions: NaviampPlaybackSessionController,
    applicationControllers: NaviampApplicationControllers,
    livePlayback: NaviampLivePlaybackController,
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    playlistEngine: DesktopPlaylistEngine,
    bindings: DesktopPlaybackGraphBindings,
    snapshot: DesktopNowPlayingSnapshot,
): DesktopPlaybackControllerGraph {
    val playbackEngine = dependencies.playbackEngine
    val presentation = rememberDesktopNowPlayingPresentationState(
        initialVisualizerSettings = snapshot.initialVisualizerSettings,
        appColors = snapshot.appColors,
        interfaceSettings = snapshot.interfaceSettings,
        currentCoverArtUrl = snapshot.currentCoverArtUrl,
        nowPlayingTrack = snapshot.track,
        nowPlayingStation = snapshot.station,
        streamMetadata = snapshot.streamMetadata,
        provider = snapshot.provider,
    )
    val reporting = remember {
        DesktopPlaybackReportingAdapter(
            scope = scope,
            provider = bindings.provider,
            sourceId = bindings.sourceId,
            providerActions = applicationControllers.providerActions,
            reporting = applicationControllers.playbackReporting,
            playbackProgress = bindings.playbackProgress,
            nowPlayingTrack = bindings.nowPlayingTrack,
            playReportSessionId = bindings.playReportSessionId,
        )
    }
    val playback = remember {
        DesktopPlaybackController(
            scope = scope,
            playbackSessions = playbackSessions,
            livePlayback = livePlayback,
            queueCoordinator = queueCoordinator,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            sourceId = bindings.sourceId,
            playbackSettings = bindings.playbackSettings,
            playbackQueue = bindings.playbackQueue,
            playbackProgress = bindings.playbackProgress,
            setPlaybackProgress = bindings.setPlaybackProgress,
            nowPlayingTrack = bindings.nowPlayingTrack,
            setRepeatMode = bindings.setRepeatMode,
            setOpenPlayerOnTrackStart = bindings.setOpenPlayerOnTrackStart,
            reporting = reporting,
        )
    }
    val sleepTimer = remember {
        SleepTimerController(
            nowPlaying = bindings.nowPlayingTrack,
            playbackQueue = bindings.playbackQueue,
            playbackProgress = bindings.playbackProgress,
            playbackState = bindings.playbackState,
            setSleepTimer = bindings.setSleepTimer,
            setSleepTimerNowEpochMillis = bindings.setSleepTimerNowEpochMillis,
            setStatus = bindings.setStatus,
            stopPlayback = playback::stop,
            nowEpochMillis = DesktopSystemClock::nowEpochMillis,
        )
    }
    NaviampSleepTimerExpiryEffect(
        sleepTimer = snapshot.sleepTimer,
        snapshot = sleepTimer.snapshot(),
        onTick = sleepTimer::tick,
        onExpired = sleepTimer::expire,
    )
    val nowPlaying = remember {
        DesktopNowPlayingController(
            audioWaveformService = dependencies.audioWaveformService,
            lyricsSidecarService = dependencies.lyricsSidecarService,
            audioMetadataSidecarService = dependencies.audioMetadataSidecarService,
            localLibraryIndexRepository = dependencies.storage,
            lyricsOffsetRepository = dependencies.storage,
            playbackAudioAssets = dependencies.playbackAudioAssets,
            playbackEngine = playbackEngine,
            provider = bindings.provider,
            sourceId = bindings.sourceId,
            playbackSettings = bindings.playbackSettings,
            cacheSettings = bindings.cacheSettings,
            appRoute = bindings.route,
            lyricsVisible = bindings.lyricsVisible,
            selectedVisualizer = { presentation.selectedVisualizer },
            playbackQueue = bindings.playbackQueue,
            nowPlayingTrack = bindings.nowPlayingTrack,
            nowPlayingCoverArtUrl = { presentation.effectiveCoverArtUrl },
        )
    }
    return DesktopPlaybackControllerGraph(
        presentation = presentation,
        playback = playback,
        sleepTimer = sleepTimer,
        nowPlaying = nowPlaying,
        visualizerVisible = presentation.isVisualizerVisible(snapshot.playbackState),
    )
}
