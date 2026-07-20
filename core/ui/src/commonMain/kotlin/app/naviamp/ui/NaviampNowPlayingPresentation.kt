package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.waveform.AudioWaveform

data class NaviampNowPlayingPresentationUi(
    val nowPlaying: NowPlayingUi,
    val miniNowPlaying: NowPlayingUi?,
    val displaySettings: NowPlayingDisplaySettings,
    val visualizerFrame: PlaybackVisualizerFrame?,
    val selectedVisualizer: NaviampVisualizer,
    val visualizerColors: NaviampPlayerColors,
)

data class NaviampNowPlayingContentInput(
    val stateLabel: String,
    val playbackEngineName: String? = null,
    val capabilities: NowPlayingTrackCapabilities,
    val nowPlayingTrack: Track?,
    val nowPlayingWaveform: AudioWaveform?,
    val nowPlayingAudioTags: List<AudioTag>?,
    val nowPlayingLyrics: Lyrics?,
    val nowPlayingLyricsStatus: String?,
    val nowPlayingStreamMetadata: PlaybackStreamMetadata,
    val lyricsVisible: Boolean,
    val visualizerAvailable: Boolean,
    val visualizerVisible: Boolean,
    val coverArtUrl: String?,
    val playbackQueue: PlaybackQueue,
    val internetRadioStations: List<InternetRadioStation>,
    val currentInternetRadioStationId: String?,
    val radioTrackArtworkByKey: Map<String, String?>,
    val relatedTracks: List<Track>,
    val relatedTracksSource: RelatedTracksSource,
    val relatedSimilarityByTrackId: Map<TrackId, Double>,
    val coverArtUrlForTrack: (Track) -> String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val miniHasPrevious: Boolean = hasPrevious,
    val miniHasNext: Boolean = hasNext,
    val shuffleActive: Boolean,
    val repeatMode: RepeatMode,
    val playbackState: PlaybackState,
    val playbackProgress: PlaybackProgress,
    val durationSeconds: Double? = playbackProgress.durationSeconds,
    val volumePercent: Int,
    val sleepTimer: NaviampSleepTimerUi,
    val streamQuality: StreamQuality,
    val replayGainInspectorEnabled: Boolean,
    val replayGainMode: ReplayGainMode,
    val sonicSimilarityEnabled: Boolean,
    val radioDjs: List<RadioDjPreset>,
    val activeRadioDjId: String?,
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val useInlinePlaylistPicker: Boolean = true,
    val playlistActionStatus: String? = null,
)

data class NaviampNowPlayingPresentationInput(
    val content: NaviampNowPlayingContentInput,
    val displaySettings: NowPlayingDisplaySettings,
    val visualizerFrame: PlaybackVisualizerFrame?,
    val selectedVisualizer: NaviampVisualizer,
    val visualizerColors: NaviampPlayerColors,
)

fun NaviampNowPlayingContentInput.toNowPlayingUi(): NowPlayingUi =
    toNowPlayingUi(
        sections = resolveSections(),
        radioStations = resolveRadioStations(),
    )

fun NaviampNowPlayingPresentationInput.toPresentationUi(): NaviampNowPlayingPresentationUi =
    NaviampNowPlayingPresentationUi(
        nowPlaying = content.toNowPlayingUi(),
        miniNowPlaying = content.toMiniNowPlayingUi(),
        displaySettings = displaySettings,
        visualizerFrame = visualizerFrame,
        selectedVisualizer = selectedVisualizer,
        visualizerColors = visualizerColors,
    )

@Composable
fun rememberNaviampNowPlayingPresentation(
    input: NaviampNowPlayingPresentationInput,
): NaviampNowPlayingPresentationUi {
    val content = input.content
    val sections = remember(
        content.playbackQueue,
        content.relatedTracks,
        content.relatedTracksSource,
        content.relatedSimilarityByTrackId,
        content.coverArtUrlForTrack,
        content.sonicSimilarityEnabled,
        content.repeatMode,
    ) {
        content.resolveSections()
    }
    val radioStations = remember(content.internetRadioStations) {
        content.resolveRadioStations()
    }
    return NaviampNowPlayingPresentationUi(
        nowPlaying = content.toNowPlayingUi(sections, radioStations),
        miniNowPlaying = content.toMiniNowPlayingUi(),
        displaySettings = input.displaySettings,
        visualizerFrame = input.visualizerFrame,
        selectedVisualizer = input.selectedVisualizer,
        visualizerColors = input.visualizerColors,
    )
}

private fun NaviampNowPlayingContentInput.toMiniNowPlayingUi(): NowPlayingUi? =
    nowPlayingTrack?.toMiniNowPlayingUi(
        MiniNowPlayingUiConfig(
            stateLabel = stateLabel,
            coverArtUrl = coverArtUrl,
            isPlaying = playbackState == PlaybackState.Playing,
            isPaused = playbackState == PlaybackState.Paused,
            canPlayPause = capabilities.canPlayPause,
            hasPrevious = miniHasPrevious,
            hasNext = miniHasNext,
        ),
    )

private fun NaviampNowPlayingContentInput.resolveSections(): NowPlayingSectionsUi =
    playbackQueue.toNowPlayingSectionsUi(
        relatedTracks = relatedTracks,
        coverArtUrl = coverArtUrlForTrack,
        sonicSimilarityEnabled = sonicSimilarityEnabled,
        relatedTracksSource = relatedTracksSource,
        relatedSimilarityByTrackId = relatedSimilarityByTrackId,
        repeatMode = repeatMode,
    )

private fun NaviampNowPlayingContentInput.resolveRadioStations(): List<NaviampNowPlayingItemUi> =
    internetRadioStations
        .sortedBy { it.name.lowercase() }
        .map(InternetRadioStation::toNowPlayingStationUi)

private fun NaviampNowPlayingContentInput.toNowPlayingUi(
    sections: NowPlayingSectionsUi,
    radioStations: List<NaviampNowPlayingItemUi>,
): NowPlayingUi {
    val isLiveStream = currentInternetRadioStationId != null
    return nowPlayingTrack?.toTrackNowPlayingUi(
        stateLabel = stateLabel,
        coverArtUrl = coverArtUrl,
        playbackProgress = playbackProgress,
        playbackState = playbackState,
        capabilities = capabilities,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        shuffleEnabled = sections.shuffleEnabled,
        shuffleActive = shuffleActive,
        repeatMode = repeatMode,
        sleepTimer = sleepTimer,
        relatedLabels = sections.relatedLabels,
        playbackEngineName = playbackEngineName,
        waveform = nowPlayingWaveform,
        visualizerAvailable = visualizerAvailable,
        visualizerVisible = visualizerVisible,
        durationSeconds = durationSeconds,
        lyricsVisible = lyricsVisible,
        lyricsStatus = nowPlayingLyricsStatus,
        lyrics = nowPlayingLyrics,
        streamQuality = streamQuality,
        embeddedTags = nowPlayingAudioTags?.map { it.key to it.value },
        replayGainInspectorEnabled = replayGainInspectorEnabled,
        replayGainMode = replayGainMode,
        playlistChoices = playlistChoices,
        useInlinePlaylistPicker = useInlinePlaylistPicker,
        playlistActionStatus = playlistActionStatus,
        backTo = sections.backTo,
        upNext = sections.upNext,
        related = sections.related,
        volumePercent = volumePercent,
    )?.copy(
        isLive = isLiveStream,
        radioStations = radioStations,
        radioDjs = radioDjs,
        activeRadioDjId = activeRadioDjId,
    ) ?: internetRadioStations.firstOrNull { it.id == currentInternetRadioStationId }?.let { station ->
        station.toRadioNowPlayingUi(
            streamMetadata = nowPlayingStreamMetadata,
            playbackState = playbackState,
            volumePercent = volumePercent,
            radioStations = internetRadioStations,
            radioTrackArtworkByKey = radioTrackArtworkByKey,
            canPlayPause = capabilities.canPlayPause,
            canChangeVolume = capabilities.canChangeVolume,
        ).copy(
            radioDjs = radioDjs,
            activeRadioDjId = activeRadioDjId,
        )
    } ?: NowPlayingUi(
        title = "Queue will appear here after connection",
        subtitle = if (isLiveStream) "Internet radio" else "Nothing Playing",
        stateLabel = stateLabel,
        coverArtUrl = coverArtUrl,
        volumePercent = volumePercent,
        isPlaying = playbackState == PlaybackState.Playing,
        isPaused = playbackState == PlaybackState.Paused,
        canPlayPause = capabilities.canPlayPause,
        canChangeVolume = capabilities.canChangeVolume,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        radioStations = radioStations,
        radioDjs = radioDjs,
        activeRadioDjId = activeRadioDjId,
    )
}

@Composable
fun NaviampNowPlayingContent(
    presentation: NaviampNowPlayingPresentationUi,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    modifier: Modifier = Modifier,
) {
    NaviampNowPlayingPanel(
        modifier = modifier,
        nowPlaying = presentation.nowPlaying,
        colors = colors,
        displaySettings = presentation.displaySettings,
        visualizerBandsProvider = { presentation.visualizerFrame?.bands.orEmpty() },
        selectedVisualizer = presentation.selectedVisualizer,
        visualizerColors = presentation.visualizerColors,
        actions = actions,
    )
}

@Composable
fun NaviampMiniPlayerContent(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NaviampMiniNowPlaying(
        nowPlaying = nowPlaying,
        colors = colors,
        actions = actions,
        onOpen = onOpen,
        modifier = modifier,
    )
}
