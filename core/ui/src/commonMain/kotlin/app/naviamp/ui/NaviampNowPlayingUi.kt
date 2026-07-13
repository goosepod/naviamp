package app.naviamp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.waveform.playbackFraction
import app.naviamp.domain.waveform.cleanWaveformAmplitudes
import app.naviamp.domain.waveform.seekSecondsForFraction
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.settings.TrackSwipeAction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class NaviampRepeatMode {
    Off,
    Queue,
    Track,
}

enum class NaviampNowPlayingTab {
    BackTo,
    UpNext,
    Related,
}

data class NaviampNowPlayingItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val coverArtUrl: String? = null,
    val favoriteActive: Boolean = false,
    val hasAlbum: Boolean = false,
    val hasArtist: Boolean = false,
    val playNextPriority: Boolean = false,
)

data class NaviampNowPlayingActions(
    val onPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit = {},
    val onDisplayAction: (NowPlayingDisplayActionRequest) -> Unit = {},
    val onCurrentTrackAction: (NowPlayingCurrentTrackUiActionRequest) -> Unit = {},
    val onQueueAction: (NowPlayingQueueActionRequest) -> Unit = {},
    val onSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit = {},
    val onSelectionAction: (NowPlayingSelectionActionRequest) -> Unit = {},
    val onQueueItemAction: (NowPlayingItemActionRequest) -> Unit = { request ->
    },
) {
    fun playback(action: NowPlayingPlaybackAction) {
        onPlaybackAction(NowPlayingPlaybackActionRequest(action))
    }

    fun seek(seconds: Double) {
        onPlaybackAction(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Seek, seekSeconds = seconds))
    }

    fun changeVolume(volumePercent: Int) {
        onPlaybackAction(
            NowPlayingPlaybackActionRequest(
                NowPlayingPlaybackAction.ChangeVolume,
                volumePercent = volumePercent,
            ),
        )
    }

    fun display(action: NowPlayingDisplayAction) {
        onDisplayAction(NowPlayingDisplayActionRequest(action))
    }

    fun changeLyricsOffset(offsetMillis: Int) {
        onDisplayAction(
            NowPlayingDisplayActionRequest(
                NowPlayingDisplayAction.ChangeLyricsOffset,
                lyricsOffsetMillis = offsetMillis,
            ),
        )
    }

    fun selectVisualizer(visualizer: NaviampVisualizer) {
        onDisplayAction(
            NowPlayingDisplayActionRequest(
                NowPlayingDisplayAction.SelectVisualizer,
                visualizer = visualizer,
            ),
        )
    }

    fun selectRadioDj(djId: String?) {
        onDisplayAction(
            NowPlayingDisplayActionRequest(
                NowPlayingDisplayAction.SelectRadioDj,
                radioDjId = djId,
            ),
        )
    }

    fun currentTrack(
        action: NowPlayingCurrentTrackAction,
        playlistChoice: NaviampPlaylistChoiceUi? = null,
        playlistName: String? = null,
        rating: Int? = null,
    ) {
        onCurrentTrackAction(
            NowPlayingCurrentTrackUiActionRequest(
                action = action,
                playlistChoice = playlistChoice,
                playlistName = playlistName,
                rating = rating,
            ),
        )
    }

    fun saveQueueAsPlaylist(name: String) {
        onQueueAction(NowPlayingQueueActionRequest(NowPlayingQueueAction.SaveQueueAsPlaylist, playlistName = name))
    }

    fun removeFromQueue(index: Int) {
        onQueueAction(NowPlayingQueueActionRequest(NowPlayingQueueAction.RemoveFromQueue, queueIndex = index))
    }

    fun emptyQueue() {
        onQueueAction(NowPlayingQueueActionRequest(NowPlayingQueueAction.EmptyQueue))
    }

    fun selectSleepTimer(request: SleepTimerRequest) {
        onSleepTimerAction(NowPlayingSleepTimerActionRequest(NowPlayingSleepTimerAction.Select, request))
    }

    fun cancelSleepTimer() {
        onSleepTimerAction(NowPlayingSleepTimerActionRequest(NowPlayingSleepTimerAction.Cancel))
    }

    fun selectItem(item: NaviampNowPlayingItemUi, action: NowPlayingSelectionAction) {
        onSelectionAction(NowPlayingSelectionActionRequest(item, action))
    }
}

@Composable
fun NaviampNowPlayingPanel(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    displaySettings: NowPlayingDisplaySettings = NowPlayingDisplaySettings(),
    modifier: Modifier = Modifier,
    visualizerBandsProvider: () -> List<Float> = { nowPlaying.visualizerFrame?.bands.orEmpty() },
    selectedVisualizer: NaviampVisualizer = NaviampVisualizer.AudioSphere,
    visualizerColors: NaviampPlayerColors = NaviampPlayerColors.fallback(colors),
) {
    var selectedTab by remember(nowPlaying.id, nowPlaying.isLive) { mutableStateOf(NaviampNowPlayingTab.UpNext) }
    val showStationList = nowPlaying.isLive
    val progressStableNowPlaying = rememberProgressStableNowPlaying(nowPlaying)
    val artSizeDefault = 286.dp
    val sidePanelHeight = 340.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 300.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp
        val viewportMaxHeight = maxHeight
        val compactStackLayout = !wideLayout && viewportMaxHeight < CompactNowPlayingStackBreakpoint
        val stackedArtSize = ((viewportMaxHeight * 0.48f) - 18.dp)
            .coerceIn(150.dp, artSizeDefault)
        val splitArtSize = ((viewportMaxHeight / 2f) - 28.dp)
            .coerceIn(170.dp, artSizeDefault)
        val artSize = when {
            wideLayout -> {
                val preferredArtSize = ((viewportMaxHeight / 2f) - 28.dp).coerceIn(170.dp, artSizeDefault)
                val maximumArtSize = (
                    viewportMaxHeight -
                        WideNowPlayingDetailsMinHeight -
                        NowPlayingArtShadowMargin * 2 -
                        WideNowPlayingDetailsTopPadding
                    ).coerceAtLeast(WideNowPlayingArtMinHeight)
                preferredArtSize.coerceAtMost(maximumArtSize)
            }
            compactStackLayout -> stackedArtSize
            maxWidth < 380.dp -> splitArtSize
            !wideLayout -> splitArtSize
            else -> artSizeDefault
        }
        val wideDetailsHeight = if (wideLayout) {
            (
                viewportMaxHeight -
                    artSize -
                    NowPlayingArtShadowMargin * 2 -
                    WideNowPlayingDetailsTopPadding
                ).coerceAtLeast(WideNowPlayingDetailsMinHeight)
        } else {
            Dp.Unspecified
        }

        if (wideLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                ) {
                    NowPlayingArtSurface(
                        nowPlaying = if (nowPlaying.visualizerVisible) nowPlaying else progressStableNowPlaying,
                        coverArtUrl = nowPlaying.coverArtUrl,
                        colors = colors,
                        size = artSize,
                        cornerRadius = 8.dp,
                        visualizerVisible = nowPlaying.visualizerVisible,
                        visualizerAvailable = nowPlaying.visualizerAvailable,
                        visualizerBandsProvider = visualizerBandsProvider,
                        selectedVisualizer = selectedVisualizer,
                        visualizerColors = visualizerColors,
                        visualizerActive = nowPlaying.isPlaying,
                        tempoBpm = nowPlaying.bpm,
                        onToggleVisualizer = { actions.display(NowPlayingDisplayAction.ToggleVisualizer) },
                        onVisualizerSelected = actions::selectVisualizer,
                    )
                    NowPlayingDetails(
                        nowPlaying = nowPlaying,
                        colors = colors,
                        playerColors = visualizerColors,
                        actions = actions,
                        selectedVisualizer = selectedVisualizer,
                        displaySettings = displaySettings,
                        compactLayout = viewportMaxHeight < 640.dp,
                        availableHeight = (wideDetailsHeight - WideNowPlayingDetailsTopPadding)
                            .coerceAtLeast(WideNowPlayingDetailsMinHeight - WideNowPlayingDetailsTopPadding),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wideDetailsHeight)
                            .padding(top = WideNowPlayingDetailsTopPadding),
                    )
                }
                NowPlayingSidePanel(
                    nowPlaying = if (nowPlaying.lyricsVisible) nowPlaying else progressStableNowPlaying,
                    colors = colors,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    showStationList = showStationList,
                    showLyrics = nowPlaying.lyricsVisible,
                    actions = actions,
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            val viewportHeight = maxHeight
            val compactWidthSizing = maxWidth < 380.dp
            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            fun scrollToPlayer() {
                coroutineScope.launch { scrollState.animateScrollTo(0) }
            }
            val itemActions = actions.copy(
                onSelectionAction = {
                    actions.onSelectionAction(it)
                    scrollToPlayer()
                },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (compactStackLayout) Arrangement.spacedBy(3.dp) else Arrangement.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                if (compactStackLayout) {
                    val compactMediaSlotHeight = artSize + NowPlayingArtShadowMargin * 2
                    val compactDetailsHeight = (viewportHeight - compactMediaSlotHeight - CompactNowPlayingStackGap)
                        .coerceAtLeast(CompactNowPlayingDetailsMinHeight)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CompactNowPlayingStackGap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .padding(horizontal = 14.dp),
                    ) {
                        if (nowPlaying.lyricsVisible && !showStationList) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(compactMediaSlotHeight),
                            ) {
                                LyricsPanel(
                                    nowPlaying = nowPlaying,
                                    colors = colors,
                                    onSeek = actions::seek,
                                    onOffsetChanged = actions::changeLyricsOffset,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(artSize),
                                )
                            }
                        } else {
                            NowPlayingArtSurface(
                                nowPlaying = if (nowPlaying.visualizerVisible) nowPlaying else progressStableNowPlaying,
                                coverArtUrl = nowPlaying.coverArtUrl,
                                colors = colors,
                                size = artSize,
                                cornerRadius = 8.dp,
                                visualizerVisible = nowPlaying.visualizerVisible,
                                visualizerAvailable = nowPlaying.visualizerAvailable,
                                visualizerBandsProvider = visualizerBandsProvider,
                                selectedVisualizer = selectedVisualizer,
                                visualizerColors = visualizerColors,
                                visualizerActive = nowPlaying.isPlaying,
                                tempoBpm = nowPlaying.bpm,
                                onToggleVisualizer = { actions.display(NowPlayingDisplayAction.ToggleVisualizer) },
                                onVisualizerSelected = actions::selectVisualizer,
                            )
                        }
                        NowPlayingDetails(
                            nowPlaying = nowPlaying,
                            colors = colors,
                            playerColors = visualizerColors,
                            actions = actions,
                            selectedVisualizer = selectedVisualizer,
                            displaySettings = displaySettings,
                            compactLayout = true,
                            availableHeight = compactDetailsHeight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight / 2)
                            .padding(horizontal = 14.dp),
                    ) {
                        if (nowPlaying.lyricsVisible && !showStationList) {
                            LyricsPanel(
                                nowPlaying = nowPlaying,
                                colors = colors,
                                onSeek = actions::seek,
                                onOffsetChanged = actions::changeLyricsOffset,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(artSize),
                            )
                        } else {
                            NowPlayingArtSurface(
                                nowPlaying = if (nowPlaying.visualizerVisible) nowPlaying else progressStableNowPlaying,
                                coverArtUrl = nowPlaying.coverArtUrl,
                                colors = colors,
                                size = artSize,
                                cornerRadius = 8.dp,
                                visualizerVisible = nowPlaying.visualizerVisible,
                                visualizerAvailable = nowPlaying.visualizerAvailable,
                                visualizerBandsProvider = visualizerBandsProvider,
                                selectedVisualizer = selectedVisualizer,
                                visualizerColors = visualizerColors,
                                visualizerActive = nowPlaying.isPlaying,
                                tempoBpm = nowPlaying.bpm,
                                onToggleVisualizer = { actions.display(NowPlayingDisplayAction.ToggleVisualizer) },
                                onVisualizerSelected = actions::selectVisualizer,
                            )
                        }
                    }
                    NowPlayingDetails(
                        nowPlaying = nowPlaying,
                        colors = colors,
                        playerColors = visualizerColors,
                        actions = actions,
                        selectedVisualizer = selectedVisualizer,
                        displaySettings = displaySettings,
                        mobileLayout = true,
                        compactSizing = compactWidthSizing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight / 2)
                            .padding(horizontal = 14.dp),
                    )
                }
                NowPlayingSidePanel(
                    nowPlaying = progressStableNowPlaying,
                    colors = colors,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    showStationList = showStationList,
                    showLyrics = false,
                    actions = itemActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sidePanelHeight)
                        .padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberProgressStableNowPlaying(nowPlaying: NowPlayingUi): NowPlayingUi {
    val key = nowPlaying.copy(
        positionSeconds = null,
        visualizerFrame = null,
    )
    return remember(key) { key }
}

@Composable
private fun NowPlayingArtSurface(
    nowPlaying: NowPlayingUi,
    coverArtUrl: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
    visualizerVisible: Boolean,
    visualizerAvailable: Boolean,
    visualizerBandsProvider: () -> List<Float>,
    selectedVisualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    visualizerActive: Boolean,
    tempoBpm: Int?,
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val toggleModifier = Modifier.clickable(enabled = visualizerAvailable, onClick = onToggleVisualizer)
    var visualizerMenuExpanded by remember { mutableStateOf(false) }

    if (visualizerVisible && visualizerAvailable) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(size + NowPlayingArtShadowMargin * 2)
                .visualizerContextMenu { visualizerMenuExpanded = true }
                .then(toggleModifier),
        ) {
            LiveVisualizerSurface(
                coverArtUrl = coverArtUrl,
                bandsProvider = visualizerBandsProvider,
                visualizer = selectedVisualizer,
                visualizerColors = visualizerColors,
                active = visualizerActive,
                tempoBpm = tempoBpm,
                colors = colors,
                lyricStage = nowPlaying.currentLyricMirrorTunnelStage(),
                modifier = Modifier
                    .fillMaxSize(),
            )
            NaviampDropdownMenu(
                expanded = visualizerMenuExpanded,
                onDismissRequest = { visualizerMenuExpanded = false },
                offset = DpOffset(8.dp, 8.dp),
            ) {
                VisualizerDropdownMenuItems(
                    selectedVisualizer = selectedVisualizer,
                    onVisualizerSelected = {
                        visualizerMenuExpanded = false
                        onVisualizerSelected(it)
                    },
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size + NowPlayingArtShadowMargin * 2),
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .shadow(24.dp, shape, clip = false)
                    .shadow(7.dp, shape, clip = false),
            ) {
                Box(modifier = toggleModifier) {
                    PlatformCoverArt(coverArtUrl, colors, size, cornerRadius)
                }
            }
        }
    }
}

@Composable
private fun VisualizerDropdownMenuItems(
    selectedVisualizer: NaviampVisualizer,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
) {
    NaviampVisualizer.entries.sortedBy { it.label }.forEach { visualizer ->
        NaviampDropdownMenuItem(
            label = if (visualizer == selectedVisualizer) "${visualizer.label} ✓" else visualizer.label,
            enabled = visualizer != selectedVisualizer,
            onClick = { onVisualizerSelected(visualizer) },
        )
    }
}

@Composable
private fun NowPlayingDetails(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors,
    actions: NaviampNowPlayingActions,
    selectedVisualizer: NaviampVisualizer,
    displaySettings: NowPlayingDisplaySettings,
    mobileLayout: Boolean = false,
    compactLayout: Boolean = false,
    compactSizing: Boolean = compactLayout,
    availableHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var visualizerMenuExpanded by remember { mutableStateOf(false) }
    var radioDjMenuExpanded by remember { mutableStateOf(false) }
    var trackDetailsOpen by remember { mutableStateOf(false) }
    var playlistDialogOpen by remember { mutableStateOf<NaviampNowPlayingItemUi?>(null) }
    var saveQueueDialogOpen by remember { mutableStateOf(false) }
    var sleepTimerDialogOpen by remember { mutableStateOf(false) }
    var emptyQueueDialogOpen by remember { mutableStateOf(false) }
    var scrubberValue by remember(nowPlaying.id) { mutableFloatStateOf(nowPlaying.progressFraction.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var volumeValue by remember { mutableFloatStateOf(nowPlaying.volumePercent.coerceIn(0, 100) / 100f) }
    var isChangingVolume by remember { mutableStateOf(false) }
    val canSeek = nowPlaying.canSeek && nowPlaying.durationSeconds != null && !nowPlaying.isLive
    val canTogglePlayback = nowPlaying.canPlayPause
    val height = availableHeight ?: Dp.Unspecified
    val pinBottomActions = !mobileLayout && height != Dp.Unspecified
    val showVolume = !compactLayout || height == Dp.Unspecified || height >= 220.dp
    val showRating = !compactLayout || height == Dp.Unspecified || height >= 150.dp
    val showAudioInfo = displaySettings.showAudioInfo && (!compactLayout || height == Dp.Unspecified || height >= 150.dp)
    val showTrackIdentity = !compactLayout || height == Dp.Unspecified || height >= 135.dp
    val volumeVisible = displaySettings.showVolumeBar && nowPlaying.canChangeVolume && showVolume
    val compactMetadataRow = compactLayout && !mobileLayout
    val controlColors = colors.copy(accent = playerColors.accent)
    val useLargeSizing = mobileLayout && !compactSizing
    val titleFontSize = if (useLargeSizing) 19 else 15
    val titleTextHeight = if (useLargeSizing) 23.dp else 18.dp
    val metadataFontSize = if (useLargeSizing) 16 else 13
    val metadataLineHeight = if (useLargeSizing) 18.sp else 14.sp
    val audioInfoFontSize = if (useLargeSizing) 14.sp else 11.sp
    val ratingIconSize = if (useLargeSizing) 21.dp else 17.dp
    val ratingFavoriteSlotWidth = if (useLargeSizing) 30.dp else 24.dp
    val ratingStarSlotWidth = if (useLargeSizing) 23.dp else 18.dp
    val ratingStarsWidth = if (useLargeSizing) 116.dp else 92.dp
    val scrubberTimeFontSize = if (useLargeSizing) 14.sp else 11.sp
    val scrubberTimeWidth = if (useLargeSizing) 52.dp else 42.dp
    val secondaryTransportButtonSize = if (useLargeSizing) 36.dp else 28.dp
    val secondaryTransportIconSize = if (useLargeSizing) 21.dp else 16.dp
    val transportButtonSize = if (useLargeSizing) 42.dp else 34.dp
    val transportIconSize = if (useLargeSizing) 25.dp else 20.dp
    val prominentTransportButtonSize = if (useLargeSizing) 56.dp else 44.dp
    val prominentTransportIconSize = if (useLargeSizing) 30.dp else 24.dp

    LaunchedEffect(nowPlaying.progressFraction) {
        if (!isScrubbing) {
            scrubberValue = nowPlaying.progressFraction.toFloat()
        }
    }
    LaunchedEffect(nowPlaying.volumePercent) {
        if (!isChangingVolume) {
            volumeValue = nowPlaying.volumePercent.coerceIn(0, 100) / 100f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if ((mobileLayout && !compactLayout) || pinBottomActions) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(if (compactLayout) 5.dp else 6.dp)
        },
        modifier = modifier.padding(
            bottom = when {
                pinBottomActions -> 8.dp
                mobileLayout -> 4.dp
                else -> 0.dp
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (nowPlaying.isLive) "LIVE" else secondsLabel(nowPlaying.positionSeconds),
                color = colors.primaryText,
                fontSize = scrubberTimeFontSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(scrubberTimeWidth),
            )
            if (nowPlaying.isLive) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(controlColors.accent.copy(alpha = 0.7f)),
                    )
                }
            } else {
                WaveformScrubber(
                    amplitudes = nowPlaying.waveform?.amplitudes.orEmpty(),
                    value = scrubberValue.coerceIn(0f, 1f),
                    enabled = canSeek,
                    colors = controlColors,
                    onValueChange = {
                        isScrubbing = true
                        scrubberValue = it
                    },
                    onValueChangeFinished = { seekFraction ->
                        scrubberValue = seekFraction
                        seekSecondsForFraction(seekFraction, nowPlaying.durationSeconds)?.let(actions::seek)
                        isScrubbing = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                )
            }
            Text(
                if (nowPlaying.isLive) "Radio" else secondsLabel(nowPlaying.durationSeconds),
                color = colors.primaryText,
                textAlign = TextAlign.Center,
                fontSize = scrubberTimeFontSize,
                modifier = Modifier.width(scrubberTimeWidth),
            )
        }
        if (showTrackIdentity) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (mobileLayout) 2.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (mobileLayout) 1.dp else 0.dp),
                ) {
                    NowPlayingIdentityText(
                        text = nowPlaying.title,
                        color = colors.primaryText,
                        fontSize = titleFontSize,
                        height = titleTextHeight,
                        bold = true,
                        marqueeEnabled = displaySettings.scrollTrackTitle && nowPlaying.id.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NowPlayingIdentityText(
                        text = nowPlaying.subtitle,
                        color = colors.secondaryText,
                        fontSize = metadataFontSize,
                        height = metadataLineHeight.value.dp,
                        marqueeEnabled = displaySettings.scrollArtistName && !nowPlaying.isLive,
                        modifier = Modifier.clickable(
                            enabled = !nowPlaying.isLive,
                            onClick = { actions.currentTrack(NowPlayingCurrentTrackAction.GoToArtist) },
                        ),
                    )
                    val albumText = when {
                        nowPlaying.isLive -> "Live stream"
                        nowPlaying.albumTitle.isNotBlank() && displaySettings.showAlbumYear && nowPlaying.albumYear != null ->
                            "${nowPlaying.albumTitle} (${nowPlaying.albumYear})"
                        nowPlaying.albumTitle.isNotBlank() -> nowPlaying.albumTitle
                        else -> nowPlaying.albumLine.ifBlank { nowPlaying.stateLabel }
                    }
                    NowPlayingIdentityText(
                        text = albumText,
                        color = colors.secondaryText.copy(alpha = 0.84f),
                        fontSize = metadataFontSize,
                        height = metadataLineHeight.value.dp,
                        marqueeEnabled = displaySettings.scrollAlbumName && !nowPlaying.isLive,
                        modifier = Modifier.clickable(
                            enabled = !nowPlaying.isLive && nowPlaying.albumLine.isNotBlank(),
                            onClick = { actions.currentTrack(NowPlayingCurrentTrackAction.GoToAlbum) },
                        ),
                    )
                }
        }

        if (compactMetadataRow && (showRating || showAudioInfo)) {
                CompactMetadataRow(
                    nowPlaying = nowPlaying,
                    colors = colors,
                    showRating = showRating,
                    showAudioInfo = showAudioInfo,
                    onToggleFavorite = { actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite) },
                    onRatingSelected = { rating ->
                        actions.currentTrack(NowPlayingCurrentTrackAction.SetRating, rating = rating)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
        } else if (showRating || showAudioInfo) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showRating && !nowPlaying.isLive && (nowPlaying.canFavorite || nowPlaying.canRate || nowPlaying.favoriteActive || nowPlaying.userRating != null)) {
                        RatingRow(
                            favoriteActive = nowPlaying.favoriteActive,
                            canFavorite = nowPlaying.canFavorite,
                            rating = nowPlaying.userRating,
                            canRate = nowPlaying.canRate,
                            colors = colors,
                            onToggleFavorite = { actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite) },
                            onRatingSelected = { rating ->
                                actions.currentTrack(NowPlayingCurrentTrackAction.SetRating, rating = rating)
                            },
                            iconSize = ratingIconSize,
                            favoriteSlotWidth = ratingFavoriteSlotWidth,
                            starSlotWidth = ratingStarSlotWidth,
                            starsWidth = ratingStarsWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (showAudioInfo && !nowPlaying.isLive && nowPlaying.audioInfo.isNotBlank()) {
                        Text(
                            nowPlaying.audioInfo,
                            color = colors.secondaryText,
                            fontSize = audioInfoFontSize,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
        }

        if (volumeVisible) {
            VolumeRow(
                value = volumeValue,
                isChangingVolume = { isChangingVolume = it },
                onValueChanged = {
                    volumeValue = it
                    actions.changeVolume((it * 100).toInt().coerceIn(0, 100))
                },
                colors = controlColors,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    top = if (mobileLayout) 0.dp else 6.dp,
                    bottom = if (mobileLayout) 0.dp else 4.dp,
                ),
        ) {
            NaviampTransportIconButton(
                enabled = nowPlaying.shuffleEnabled,
                icon = NaviampTransportIcons.Shuffle,
                contentDescription = if (nowPlaying.shuffleActive) "Turn shuffle off" else "Shuffle Up Next",
                colors = controlColors,
                selected = nowPlaying.shuffleActive,
                buttonSize = secondaryTransportButtonSize,
                iconSize = secondaryTransportIconSize,
                onClick = { actions.playback(NowPlayingPlaybackAction.ToggleShuffle) },
            )
            NaviampTransportIconButton(
                enabled = nowPlaying.hasPrevious,
                icon = NaviampTransportIcons.Previous,
                contentDescription = "Previous",
                colors = controlColors,
                buttonSize = transportButtonSize,
                iconSize = transportIconSize,
                onClick = { actions.playback(NowPlayingPlaybackAction.Previous) },
            )
            NaviampTransportIconButton(
                enabled = canTogglePlayback,
                icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                colors = controlColors,
                prominent = true,
                buttonSize = prominentTransportButtonSize,
                iconSize = prominentTransportIconSize,
                onClick = {
                    when {
                        nowPlaying.isPlaying -> actions.playback(NowPlayingPlaybackAction.Pause)
                        nowPlaying.isPaused -> actions.playback(NowPlayingPlaybackAction.Resume)
                        else -> actions.playback(NowPlayingPlaybackAction.PlayCurrent)
                    }
                },
            )
            NaviampTransportIconButton(
                enabled = nowPlaying.hasNext,
                icon = NaviampTransportIcons.Next,
                contentDescription = "Next",
                colors = controlColors,
                buttonSize = transportButtonSize,
                iconSize = transportIconSize,
                onClick = { actions.playback(NowPlayingPlaybackAction.Next) },
            )
            NaviampTransportIconButton(
                enabled = nowPlaying.canRepeat,
                icon = NaviampTransportIcons.Repeat,
                contentDescription = when (nowPlaying.repeatMode) {
                    NaviampRepeatMode.Off -> "Repeat off"
                    NaviampRepeatMode.Queue -> "Repeat queue"
                    NaviampRepeatMode.Track -> "Repeat current track"
                },
                colors = controlColors,
                selected = nowPlaying.repeatMode != NaviampRepeatMode.Off,
                buttonSize = secondaryTransportButtonSize,
                iconSize = secondaryTransportIconSize,
                centerText = if (nowPlaying.repeatMode == NaviampRepeatMode.Track) "1" else null,
                onClick = { actions.playback(NowPlayingPlaybackAction.CycleRepeatMode) },
            )
        }

        if (compactLayout && mobileLayout) {
            Box(modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(if (mobileLayout) 46.dp else 44.dp)
                .padding(horizontal = if (pinBottomActions) 8.dp else 0.dp),
        ) {
            val bottomActionButtonSize = 33.dp
            val bottomActionIconSize = 20.dp
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                NaviampTransportIconButton(
                    enabled = nowPlaying.canStartRadio,
                    icon = NaviampTransportIcons.Radio,
                    contentDescription = "Start track radio",
                    colors = colors,
                    buttonSize = bottomActionButtonSize,
                    iconSize = bottomActionIconSize,
                    onClick = { actions.currentTrack(NowPlayingCurrentTrackAction.StartRadio) },
                )
                Box(modifier = Modifier.requiredSize(bottomActionButtonSize), contentAlignment = Alignment.Center) {
                    NaviampTransportIconButton(
                        enabled = true,
                        icon = NaviampIcons.Turntable,
                        contentDescription = "DJs",
                        colors = colors,
                        selected = nowPlaying.activeRadioDjId != null,
                        buttonSize = bottomActionButtonSize,
                        iconSize = bottomActionIconSize,
                        onClick = { radioDjMenuExpanded = true },
                    )
                    NaviampDropdownMenu(
                        expanded = radioDjMenuExpanded,
                        onDismissRequest = { radioDjMenuExpanded = false },
                        offset = DpOffset(0.dp, 6.dp),
                    ) {
                        NaviampDropdownMenuItem(
                            label = "Default radio",
                            icon = NaviampIcons.Turntable,
                            enabled = true,
                            selected = nowPlaying.activeRadioDjId == null,
                            onClick = {
                                radioDjMenuExpanded = false
                                actions.selectRadioDj(null)
                            },
                        )
                        if (nowPlaying.radioDjs.isEmpty()) {
                            NaviampDropdownMenuItem(
                                label = "No DJs saved",
                                icon = NaviampIcons.Turntable,
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            nowPlaying.radioDjs.forEach { dj ->
                                val selected = dj.id == nowPlaying.activeRadioDjId
                                NaviampDropdownMenuItem(
                                    label = dj.name,
                                    icon = NaviampIcons.Turntable,
                                    enabled = true,
                                    selected = selected,
                                    onClick = {
                                        radioDjMenuExpanded = false
                                        actions.selectRadioDj(if (selected) null else dj.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = { actions.display(NowPlayingDisplayAction.Collapse) },
                modifier = Modifier
                    .requiredSize(bottomActionButtonSize)
                    .align(Alignment.Center),
            ) {
                Icon(
                    imageVector = NaviampIcons.ChevronDown,
                    contentDescription = "Back",
                    tint = colors.secondaryText,
                    modifier = Modifier.requiredSize(bottomActionIconSize),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                NaviampTransportIconButton(
                    enabled = nowPlaying.lyricsAvailable,
                    icon = NaviampTransportIcons.Lyrics,
                    contentDescription = if (nowPlaying.lyricsVisible) "Hide lyrics" else "Show lyrics",
                    colors = colors,
                    selected = nowPlaying.lyricsVisible,
                    buttonSize = bottomActionButtonSize,
                    iconSize = bottomActionIconSize,
                    onClick = { actions.display(NowPlayingDisplayAction.ToggleLyrics) },
                )
                Box(modifier = Modifier.requiredSize(bottomActionButtonSize), contentAlignment = Alignment.Center) {
                    NaviampTransportIconButton(
                        enabled = nowPlaying.menuEnabled,
                        icon = NaviampTransportIcons.MoreVertical,
                        contentDescription = "Track actions",
                        colors = colors,
                        buttonSize = bottomActionButtonSize,
                        iconSize = bottomActionIconSize,
                        onClick = { actionMenuExpanded = true },
                    )
                    NaviampDropdownMenu(
                        expanded = actionMenuExpanded,
                        onDismissRequest = { actionMenuExpanded = false },
                        offset = DpOffset(0.dp, 6.dp),
                    ) {
                        nowPlayingTrackMenuActions(
                            visualizerAvailable = nowPlaying.visualizerAvailable,
                            isLive = nowPlaying.isLive,
                            hasDetails = nowPlaying.detailSections.isNotEmpty(),
                            canAddToPlaylist = nowPlaying.canAddToPlaylist,
                            canSaveQueueAsPlaylist = nowPlaying.canSaveQueueAsPlaylist,
                            canEmptyQueue = nowPlaying.upNext.isNotEmpty(),
                            sleepTimerLabel = nowPlaying.sleepTimer.label,
                        ).forEach { action ->
                            NaviampDropdownMenuItem(
                                label = action.label,
                                icon = action.icon,
                                enabled = action.enabled,
                                onClick = {
                                    actionMenuExpanded = false
                                    when (action.action) {
                                        NaviampAction.ChangeVisualizer -> visualizerMenuExpanded = true
                                        NaviampAction.DownloadTrack ->
                                            actions.currentTrack(NowPlayingCurrentTrackAction.Download)
                                        NaviampAction.TrackDetails -> trackDetailsOpen = true
                                        NaviampAction.GoToAlbum ->
                                            actions.currentTrack(NowPlayingCurrentTrackAction.GoToAlbum)
                                        NaviampAction.GoToArtist ->
                                            actions.currentTrack(NowPlayingCurrentTrackAction.GoToArtist)
                                        NaviampAction.AddToPlaylist -> {
                                            if (nowPlaying.useInlinePlaylistPicker) {
                                                playlistDialogOpen = null
                                                playlistDialogOpen = NaviampNowPlayingItemUi(
                                                    id = nowPlaying.id,
                                                    title = nowPlaying.title,
                                                    subtitle = nowPlaying.subtitle,
                                                    coverArtUrl = nowPlaying.coverArtUrl,
                                                )
                                            } else {
                                                actions.currentTrack(NowPlayingCurrentTrackAction.AddToPlaylist)
                                            }
                                        }
                                        NaviampAction.SaveQueueAsPlaylist -> saveQueueDialogOpen = true
                                        NaviampAction.EmptyQueue -> emptyQueueDialogOpen = true
                                        NaviampAction.SleepTimer -> sleepTimerDialogOpen = true
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    }
                    NaviampDropdownMenu(
                        expanded = visualizerMenuExpanded,
                        onDismissRequest = { visualizerMenuExpanded = false },
                        offset = DpOffset(0.dp, 6.dp),
                    ) {
                        VisualizerDropdownMenuItems(
                            selectedVisualizer = selectedVisualizer,
                            onVisualizerSelected = {
                                visualizerMenuExpanded = false
                                actions.selectVisualizer(it)
                            },
                        )
                    }
                }
            }
        }
    }

    if (trackDetailsOpen) {
        TrackDetailsDialog(
            sections = nowPlaying.detailSections,
            colors = colors,
            onDismissRequest = { trackDetailsOpen = false },
        )
    }
    if (saveQueueDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = colors,
            status = nowPlaying.playlistActionStatus,
            onDismissRequest = { saveQueueDialogOpen = false },
            onSave = { name ->
                saveQueueDialogOpen = false
                actions.saveQueueAsPlaylist(name)
            },
        )
    }
    if (sleepTimerDialogOpen) {
        SleepTimerDialog(
            colors = colors,
            timer = nowPlaying.sleepTimer,
            onDismissRequest = { sleepTimerDialogOpen = false },
            onTimerSelected = { request ->
                sleepTimerDialogOpen = false
                actions.selectSleepTimer(request)
            },
            onCancelTimer = {
                sleepTimerDialogOpen = false
                actions.cancelSleepTimer()
            },
        )
    }
    if (emptyQueueDialogOpen) {
        ConfirmActionDialog(
            title = "Empty queue?",
            message = "Remove all tracks from Up Next.",
            confirmLabel = "Empty queue",
            colors = colors,
            onDismissRequest = { emptyQueueDialogOpen = false },
            onConfirm = {
                emptyQueueDialogOpen = false
                actions.emptyQueue()
            },
        )
    }
    playlistDialogOpen?.let { item ->
        AddToPlaylistDialog(
            title = item.title,
            colors = colors,
            playlists = nowPlaying.playlistChoices,
            status = nowPlaying.playlistActionStatus,
            onDismissRequest = { playlistDialogOpen = null },
            onAddToExisting = { playlist ->
                playlistDialogOpen = null
                if (item.id == nowPlaying.id) {
                    actions.currentTrack(
                        NowPlayingCurrentTrackAction.AddToPlaylist,
                        playlistChoice = playlist,
                    )
                } else {
                    actions.onQueueItemAction(
                        nowPlayingItemActionRequest(
                            item,
                            NowPlayingItemAction.AddToPlaylist,
                            playlistChoice = playlist,
                        ),
                    )
                }
            },
            onCreateAndAdd = { name ->
                playlistDialogOpen = null
                if (item.id == nowPlaying.id) {
                    actions.currentTrack(
                        NowPlayingCurrentTrackAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    )
                } else {
                    actions.onQueueItemAction(
                        nowPlayingItemActionRequest(
                            item,
                            NowPlayingItemAction.CreatePlaylistAndAdd,
                            playlistName = name,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun CompactMetadataRow(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    showRating: Boolean,
    showAudioInfo: Boolean,
    onToggleFavorite: () -> Unit,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nowPlaying.isLive) return

    val showRatingControls = showRating &&
        (nowPlaying.canFavorite || nowPlaying.canRate || nowPlaying.favoriteActive || nowPlaying.userRating != null)
    val showAudioText = showAudioInfo && nowPlaying.audioInfo.isNotBlank()
    if (!showRatingControls && !showAudioText) return
    val (audioType, audioQuality) = nowPlaying.audioInfo.compactAudioInfoParts()

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (showAudioText) {
            Text(
                audioType,
                color = colors.secondaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(36.dp),
            )
        }
        if (showRatingControls) {
            RatingRow(
                favoriteActive = nowPlaying.favoriteActive,
                canFavorite = nowPlaying.canFavorite,
                rating = nowPlaying.userRating,
                canRate = nowPlaying.canRate,
                colors = colors,
                onToggleFavorite = onToggleFavorite,
                onRatingSelected = onRatingSelected,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (showAudioText) {
            Text(
                audioQuality,
                color = colors.secondaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(58.dp),
            )
        }
    }
}

private fun String.compactAudioInfoParts(): Pair<String, String> {
    val parts = trim().split(Regex("\\s{2,}"), limit = 2)
    if (parts.size == 2) return parts[0] to parts[1]
    val firstSpace = indexOf(' ')
    return if (firstSpace > 0) {
        substring(0, firstSpace) to substring(firstSpace + 1).trim()
    } else {
        this to ""
    }
}

private fun NowPlayingUi.currentLyricMirrorTunnelStage(): LyricMirrorTunnelStage {
    val positionMillis = positionSeconds?.times(1000)?.toLong() ?: return EmptyLyricMirrorTunnelStage
    if (lyricsLines.isEmpty()) return EmptyLyricMirrorTunnelStage
    val activeIndex = lyricsLines.indexOfLast { line ->
        val startMillis = line.startMillis?.plus(lyricsOffsetMillis)
        startMillis != null && startMillis <= positionMillis + LyricsAutoScrollLeadMillis
    }
    if (activeIndex < 0) return EmptyLyricMirrorTunnelStage
    val activeLine = lyricsLines.getOrNull(activeIndex)?.takeIf { it.text.isNotBlank() }
        ?: return EmptyLyricMirrorTunnelStage
    val activeStart = activeLine.startMillis?.plus(lyricsOffsetMillis)
    val nextIndex = lyricsLines.indexOfFirstAfter(activeIndex) { it.startMillis != null && it.text.isNotBlank() }
    val nextStart = lyricsLines.getOrNull(nextIndex)?.startMillis?.plus(lyricsOffsetMillis)
    val progress = if (activeStart != null && nextStart != null && nextStart > activeStart) {
        ((positionMillis - activeStart).toFloat() / (nextStart - activeStart).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    if (activeStart != null &&
        positionMillis - activeStart > LyricMirrorTunnelLineHoldMillis &&
        (nextStart == null || nextStart - positionMillis > LyricMirrorTunnelUpcomingLeadMillis)
    ) {
        return EmptyLyricMirrorTunnelStage
    }
    val lines = mutableListOf<LyricMirrorTunnelLine>()
    val previousIndex = lyricsLines.indexOfLastBefore(activeIndex) { it.startMillis != null && it.text.isNotBlank() }
    if (previousIndex >= 0 && activeStart != null) {
        val previous = lyricsLines[previousIndex]
        val exitProgress = ((positionMillis - activeStart).toFloat() / LyricMirrorTunnelFallMillis).coerceIn(0f, 1f)
        if (exitProgress < 1f) {
            lines += LyricMirrorTunnelLine(
                key = previous.lyricMirrorTunnelKey(),
                text = previous.text,
                progressToNext = 1f,
                enterProgress = 1f,
                exitProgress = exitProgress,
                verticalSlot = lyricMirrorTunnelSlot(previousIndex),
                role = LyricMirrorTunnelLineRole.Previous,
            )
        }
    }
    lines += LyricMirrorTunnelLine(
        key = activeLine.lyricMirrorTunnelKey(),
        text = activeLine.text,
        progressToNext = progress,
        enterProgress = if (activeStart != null) {
            ((positionMillis - activeStart).toFloat() / LyricMirrorTunnelEnterMillis).coerceIn(0f, 1f)
        } else {
            1f
        },
        exitProgress = 0f,
        verticalSlot = lyricMirrorTunnelSlot(activeIndex),
        role = LyricMirrorTunnelLineRole.Active,
    )
    return LyricMirrorTunnelStage(lines)
}

private inline fun <T> List<T>.indexOfFirstAfter(index: Int, predicate: (T) -> Boolean): Int {
    for (candidate in index + 1 until size) {
        if (predicate(this[candidate])) return candidate
    }
    return -1
}

private inline fun <T> List<T>.indexOfLastBefore(index: Int, predicate: (T) -> Boolean): Int {
    for (candidate in index - 1 downTo 0) {
        if (predicate(this[candidate])) return candidate
    }
    return -1
}

private fun lyricMirrorTunnelSlot(index: Int): Float {
    val slots = floatArrayOf(-0.36f, 0.18f, -0.08f, 0.42f, -0.58f, 0.02f)
    return slots[index.floorMod(slots.size)]
}

private fun NaviampLyricLineUi.lyricMirrorTunnelKey(): String =
    "lyric-$startMillis-$text"

private fun Int.floorMod(modulus: Int): Int =
    ((this % modulus) + modulus) % modulus

private const val LyricMirrorTunnelEnterMillis = 820f
private const val LyricMirrorTunnelFallMillis = 3800f
private const val LyricMirrorTunnelUpcomingLeadMillis = 1320L
private const val LyricMirrorTunnelLineHoldMillis = 5000L

@Composable
private fun LiveVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    tempoBpm: Int?,
    colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage,
    modifier: Modifier = Modifier,
) {
    PlatformLiveVisualizerSurface(
        coverArtUrl = coverArtUrl,
        bandsProvider = bandsProvider,
        visualizer = visualizer,
        visualizerColors = visualizerColors,
        active = active,
        tempoBpm = tempoBpm,
        colors = colors,
        lyricStage = lyricStage,
        modifier = modifier,
    )
}

@Composable
internal expect fun PlatformLiveVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    tempoBpm: Int?,
    colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage,
    modifier: Modifier = Modifier,
)

private fun Modifier.visualizerContextMenu(onOpen: () -> Unit): Modifier =
    pointerInput(onOpen) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onOpen()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

@Composable
private fun WaveformScrubber(
    amplitudes: List<Float>,
    value: Float,
    enabled: Boolean,
    colors: NaviampColors,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var latestValue by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    val displayAmplitudes = remember(amplitudes) { cleanWaveformAmplitudes(amplitudes) }
    val readableAccent = colors.accent.mix(colors.primaryText, 0.48f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (displayAmplitudes.isEmpty()) {
                drawFallbackScrubLine(value, enabled, colors)
                return@Canvas
            }

            val centerY = size.height / 2f
            val visibleBars = minOf(displayAmplitudes.size, size.width.toInt().coerceAtLeast(24))
            val step = size.width / visibleBars.toFloat()
            val strokeWidth = (step * 0.72f).coerceIn(0.75f, 2.4f)
            val minBarHeight = 2.5f
            val maxBarHeight = size.height * 0.92f

            repeat(visibleBars) { index ->
                val sourceIndex = if (visibleBars == 1) {
                    0
                } else {
                    ((index / (visibleBars - 1f)) * (displayAmplitudes.size - 1)).toInt()
                }
                val amplitude = displayAmplitudes[sourceIndex].coerceIn(0f, 1f)
                val barHeight = (minBarHeight + amplitude * (maxBarHeight - minBarHeight))
                    .coerceAtMost(size.height)
                val ratio = if (visibleBars == 1) 0f else index / (visibleBars - 1f)
                val color = when {
                    !enabled -> colors.mutedText.copy(alpha = 0.28f)
                    ratio <= value -> readableAccent.copy(alpha = 0.98f)
                    else -> colors.primaryText.copy(alpha = 0.42f)
                }
                val x = index * step + step / 2f
                drawLine(
                    color = color,
                    start = Offset(x, centerY - barHeight / 2f),
                    end = Offset(x, centerY + barHeight / 2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { nextValue ->
                latestValue = nextValue.coerceIn(0f, 1f)
                onValueChange(latestValue)
            },
            onValueChangeFinished = {
                onValueChangeFinished(latestValue)
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            )
        )
    }
}

private fun DrawScope.drawFallbackScrubLine(
    value: Float,
    enabled: Boolean,
    colors: NaviampColors,
) {
    val centerY = size.height / 2f
    val endX = size.width * value.coerceIn(0f, 1f)
    val disabledAlpha = if (enabled) 1f else 0.42f
    drawLine(
        color = colors.primaryText.copy(alpha = 0.42f * disabledAlpha),
        start = Offset.Zero.copy(y = centerY),
        end = Offset(size.width, centerY),
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = colors.accent.mix(colors.primaryText, 0.48f).copy(alpha = 0.98f * disabledAlpha),
        start = Offset.Zero.copy(y = centerY),
        end = Offset(endX, centerY),
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )
}

@Composable
private fun RatingRow(
    favoriteActive: Boolean,
    canFavorite: Boolean,
    rating: Int?,
    canRate: Boolean,
    colors: NaviampColors,
    onToggleFavorite: () -> Unit,
    onRatingSelected: (Int?) -> Unit,
    iconSize: Dp = 17.dp,
    favoriteSlotWidth: Dp = 24.dp,
    starSlotWidth: Dp = 18.dp,
    starsWidth: Dp = 92.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 1.dp),
    ) {
        val inactiveColor = colors.primaryText.copy(alpha = 0.72f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(favoriteSlotWidth)
                .clickable(enabled = canFavorite, onClick = onToggleFavorite),
        ) {
            Icon(
                imageVector = if (favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
                contentDescription = if (favoriteActive) "Remove favorite" else "Favorite",
                tint = if (favoriteActive) colors.primaryText else inactiveColor,
                modifier = Modifier.size(iconSize),
            )
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.width(starsWidth)) {
            (1..5).forEach { value ->
                val selected = (rating ?: 0) >= value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(starSlotWidth)
                        .clickable(enabled = canRate) {
                            onRatingSelected(if (rating == value) null else value)
                        },
                ) {
                    Icon(
                        imageVector = if (selected) NaviampTransportIcons.StarFilled else NaviampTransportIcons.Star,
                        contentDescription = "$value star rating",
                        tint = if (selected) colors.primaryText else inactiveColor,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(
    value: Float,
    isChangingVolume: (Boolean) -> Unit,
    onValueChanged: (Float) -> Unit,
    colors: NaviampColors,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { VolumeThumbRadius.toPx() }

    fun updateFromX(x: Float) {
        val trackWidth = (widthPx - thumbRadiusPx * 2f).coerceAtLeast(1f)
        onValueChanged(((x - thumbRadiusPx) / trackWidth).coerceIn(0f, 1f))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 42.dp),
    ) {
        Icon(
            imageVector = NaviampTransportIcons.Volume,
            contentDescription = "Volume",
            tint = colors.primaryText.copy(alpha = 0.86f),
            modifier = Modifier.size(17.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(widthPx, thumbRadiusPx) {
                    detectTapGestures { offset ->
                        isChangingVolume(true)
                        updateFromX(offset.x)
                        isChangingVolume(false)
                    }
                }
                .pointerInput(widthPx, thumbRadiusPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isChangingVolume(true)
                            updateFromX(offset.x)
                        },
                        onDrag = { change, _ -> updateFromX(change.position.x) },
                        onDragEnd = { isChangingVolume(false) },
                        onDragCancel = { isChangingVolume(false) },
                    )
                },
        ) {
            val centerY = size.height / 2f
            val thumbRadius = VolumeThumbRadius.toPx()
            val startX = thumbRadius
            val trackEndX = (size.width - thumbRadius).coerceAtLeast(startX)
            val endX = startX + (trackEndX - startX) * value.coerceIn(0f, 1f)
            val volumeColor = colors.accent.copy(alpha = 0.92f)
            drawLine(
                color = colors.primaryText.copy(alpha = 0.24f),
                start = Offset(startX, centerY),
                end = Offset(trackEndX, centerY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = volumeColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.32f),
                radius = thumbRadius + 2f,
                center = Offset(endX, centerY),
            )
            drawCircle(
                color = volumeColor,
                radius = thumbRadius,
                center = Offset(endX, centerY),
            )
            drawCircle(
                color = colors.primaryText.copy(alpha = 0.86f),
                radius = thumbRadius * 0.42f,
                center = Offset(endX, centerY),
            )
        }
    }
}

private val NowPlayingArtShadowMargin = 12.dp
private val CompactNowPlayingStackBreakpoint = 640.dp
private val CompactNowPlayingStackGap = 3.dp
private val CompactNowPlayingDetailsMinHeight = 132.dp
private val WideNowPlayingArtMinHeight = 112.dp
private val WideNowPlayingDetailsMinHeight = 232.dp
private val WideNowPlayingDetailsTopPadding = 8.dp
private const val LyricsActiveLineTargetIndex = 2
private val VolumeThumbRadius = 6.dp

@Composable
private fun NowPlayingSidePanel(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    selectedTab: NaviampNowPlayingTab,
    onTabSelected: (NaviampNowPlayingTab) -> Unit,
    showStationList: Boolean,
    showLyrics: Boolean,
    actions: NaviampNowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .padding(8.dp),
    ) {
        if (showStationList) {
            Text("STATIONS", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            NowPlayingItemList(
                items = nowPlaying.radioStations,
                colors = colors,
                currentId = nowPlaying.id,
                showActions = false,
                onClick = { item ->
                    actions.selectItem(item, NowPlayingSelectionAction.SelectRadioStation)
                },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        val backToListState = remember(nowPlaying.id, nowPlaying.isLive) { LazyListState() }
        val upNextListState = remember(nowPlaying.id, nowPlaying.isLive) { LazyListState() }
        val relatedListState = remember(nowPlaying.id, nowPlaying.isLive) { LazyListState() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                NowPlayingTabButton("BACK TO", selectedTab == NaviampNowPlayingTab.BackTo, colors) {
                    onTabSelected(NaviampNowPlayingTab.BackTo)
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NowPlayingTabButton("UP NEXT", selectedTab == NaviampNowPlayingTab.UpNext, colors) {
                    onTabSelected(NaviampNowPlayingTab.UpNext)
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                NowPlayingTabButton(nowPlaying.relatedTabLabel, selectedTab == NaviampNowPlayingTab.Related, colors) {
                    onTabSelected(NaviampNowPlayingTab.Related)
                }
            }
        }

        val items = when (selectedTab) {
            NaviampNowPlayingTab.BackTo -> nowPlaying.backTo
            NaviampNowPlayingTab.UpNext -> nowPlaying.upNext
            NaviampNowPlayingTab.Related -> nowPlaying.related
        }
        val onClick = when (selectedTab) {
            NaviampNowPlayingTab.Related -> { item: NaviampNowPlayingItemUi ->
                actions.selectItem(item, NowPlayingSelectionAction.SelectRelatedItem)
            }
            else -> { item: NaviampNowPlayingItemUi ->
                actions.selectItem(item, NowPlayingSelectionAction.SelectQueueItem)
            }
        }
        val rowActions = when (selectedTab) {
            NaviampNowPlayingTab.Related -> relatedTrackRowActions()
            NaviampNowPlayingTab.UpNext -> upNextQueueRowActions()
            else -> queueRowActions()
        }
        val listState = when (selectedTab) {
            NaviampNowPlayingTab.BackTo -> backToListState
            NaviampNowPlayingTab.UpNext -> upNextListState
            NaviampNowPlayingTab.Related -> relatedListState
        }
        val swipeSettings = LocalTrackSwipeSettings.current
        NowPlayingItemList(
            items = items,
            colors = colors,
            listState = listState,
            emptyLabel = when (selectedTab) {
                NaviampNowPlayingTab.BackTo -> "Playback history will appear here."
                NaviampNowPlayingTab.UpNext -> "Queue is empty."
                NaviampNowPlayingTab.Related -> nowPlaying.relatedEmptyLabel
            },
            playlistChoices = nowPlaying.playlistChoices,
            useInlinePlaylistPicker = nowPlaying.useInlinePlaylistPicker,
            onClick = onClick,
            rowActions = rowActions,
            onAction = actions.onQueueItemAction,
            canToggleFavorite = nowPlaying.canFavorite,
            swipeRightAction = if (selectedTab == NaviampNowPlayingTab.Related) {
                swipeSettings.relatedRight
            } else {
                swipeSettings.queueRight
            },
            swipeLeftAction = if (selectedTab == NaviampNowPlayingTab.Related) {
                swipeSettings.relatedLeft
            } else {
                swipeSettings.queueLeft
            },
            queueContext = selectedTab != NaviampNowPlayingTab.Related,
            modifier = Modifier.weight(if (showLyrics) 0.62f else 1f),
        )
        if (showLyrics) {
            LyricsPanel(
                nowPlaying = nowPlaying,
                colors = colors,
                onSeek = actions::seek,
                onOffsetChanged = actions::changeLyricsOffset,
                modifier = Modifier.weight(0.38f),
            )
        }
    }
}

@Composable
private fun LyricsPanel(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    onSeek: (Double) -> Unit,
    onOffsetChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val basePositionMillis = nowPlaying.positionSeconds?.times(1000)?.toLong()
    var localPositionAnchor by remember { mutableStateOf<LyricPositionAnchor?>(null) }
    var localNowMillis by remember { mutableStateOf(currentTimeMillis()) }

    LaunchedEffect(basePositionMillis, nowPlaying.isPlaying, nowPlaying.lyricsLines) {
        localPositionAnchor = basePositionMillis?.let { positionMillis ->
            LyricPositionAnchor(
                positionMillis = positionMillis,
                capturedAtMillis = currentTimeMillis(),
            )
        }
        localNowMillis = currentTimeMillis()
    }

    LaunchedEffect(nowPlaying.isPlaying, nowPlaying.lyricsLines, basePositionMillis) {
        if (!nowPlaying.isPlaying || basePositionMillis == null || nowPlaying.lyricsLines.none { it.startMillis != null }) {
            return@LaunchedEffect
        }
        while (true) {
            localNowMillis = currentTimeMillis()
            kotlinx.coroutines.delay(LyricsPositionTickMillis)
        }
    }

    val positionMillis = remember(localPositionAnchor, localNowMillis, nowPlaying.isPlaying) {
        localPositionAnchor?.currentPositionMillis(
            nowMillis = localNowMillis,
            playing = nowPlaying.isPlaying,
        )
    }
    val activeLineIndex = remember(nowPlaying.lyricsLines, positionMillis, nowPlaying.lyricsOffsetMillis) {
        nowPlaying.lyricsLines.indexOfLast { line ->
            val startMillis = line.startMillis?.plus(nowPlaying.lyricsOffsetMillis)
            startMillis != null && positionMillis != null && startMillis <= positionMillis + LyricsAutoScrollLeadMillis
        }
    }

    LaunchedEffect(activeLineIndex, nowPlaying.lyricsLines.size) {
        if (activeLineIndex < 0) return@LaunchedEffect
        listState.animateScrollToItem((activeLineIndex - LyricsActiveLineTargetIndex).coerceAtLeast(0))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LyricsOffsetControls(
            offsetMillis = nowPlaying.lyricsOffsetMillis,
            enabled = nowPlaying.lyricsLines.any { it.startMillis != null },
            colors = colors,
            onOffsetChanged = onOffsetChanged,
        )
        when {
            nowPlaying.lyricsStatus != null -> Text(
                nowPlaying.lyricsStatus,
                color = colors.mutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            nowPlaying.lyricsLines.isEmpty() -> Text(
                "Lyrics unavailable",
                color = colors.mutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(nowPlaying.lyricsLines.size) { index ->
                    val line = nowPlaying.lyricsLines[index]
                    val active = index == activeLineIndex
                    Text(
                        text = line.text,
                        color = if (active) colors.primaryText else colors.secondaryText.copy(alpha = 0.72f),
                        fontSize = if (active) 15.sp else 13.sp,
                        lineHeight = if (active) 18.sp else 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.startMillis != null) {
                                line.startMillis
                                    ?.plus(nowPlaying.lyricsOffsetMillis)
                                    ?.coerceAtLeast(0L)
                                    ?.let { onSeek(it / 1000.0) }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsOffsetControls(
    offsetMillis: Int,
    enabled: Boolean,
    colors: NaviampColors,
    onOffsetChanged: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Offset ${offsetMillis.offsetSecondsLabel()}",
            color = colors.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        NaviampTransportIconButton(
            enabled = enabled,
            icon = NaviampIcons.Minus,
            contentDescription = "Decrease lyrics offset",
            colors = colors,
            buttonSize = 28.dp,
            iconSize = 16.dp,
            onClick = { onOffsetChanged(offsetMillis - LyricsOffsetStepMillis) },
        )
        NaviampTransportIconButton(
            enabled = enabled,
            icon = NaviampIcons.Plus,
            contentDescription = "Increase lyrics offset",
            colors = colors,
            buttonSize = 28.dp,
            iconSize = 16.dp,
            onClick = { onOffsetChanged(offsetMillis + LyricsOffsetStepMillis) },
        )
    }
}

private fun Int.offsetSecondsLabel(): String {
    if (this == 0) return "0.0s"
    val sign = if (this > 0) "+" else "-"
    val absoluteTenths = kotlin.math.abs(this) / 100
    return "$sign${absoluteTenths / 10}.${absoluteTenths % 10}s"
}

private const val LyricsAutoScrollLeadMillis = 100L
private const val LyricsPositionTickMillis = 100L
private const val LyricsOffsetStepMillis = 100

private data class LyricPositionAnchor(
    val positionMillis: Long,
    val capturedAtMillis: Long,
) {
    fun currentPositionMillis(nowMillis: Long, playing: Boolean): Long =
        if (playing) positionMillis + (nowMillis - capturedAtMillis).coerceAtLeast(0L) else positionMillis
}

@Composable
fun TrackDetailsDialog(
    sections: List<NaviampDetailSectionUi>,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Track details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(420.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                sections.forEach { section ->
                    if (section.rows.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(section.title, color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            section.rows.forEach { (label, value) ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(label, color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                                    Text(
                                        value,
                                        color = colors.primaryText.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.secondaryText, fontSize = 12.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AddToPlaylistDialog(
    title: String,
    colors: NaviampColors,
    playlists: List<NaviampPlaylistChoiceUi>,
    status: String?,
    onDismissRequest: () -> Unit,
    onAddToExisting: (NaviampPlaylistChoiceUi) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var createNew by remember(playlists) { mutableStateOf(playlists.isEmpty()) }
    var playlistName by remember { mutableStateOf("") }
    val playlistListState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add to playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add $title to a server playlist.", color = colors.secondaryText, fontSize = 12.sp)
                status?.let { Text(it, color = colors.secondaryText, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NowPlayingTabButton("EXISTING", !createNew, colors) { if (playlists.isNotEmpty()) createNew = false }
                    NowPlayingTabButton("NEW", createNew, colors) { createNew = true }
                }
                if (createNew) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    ) {
                        LazyColumn(
                            state = playlistListState,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(playlists.sortedBy { it.name.lowercase() }, key = { it.id }) { playlist ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .clickable { onAddToExisting(playlist) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                ) {
                                    Icon(NaviampIcons.Playlist, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(17.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, color = colors.primaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (playlist.subtitle.isNotBlank()) {
                                            Text(playlist.subtitle, color = colors.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        if (playlistListState.canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to colors.primaryText.copy(alpha = 0.10f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                            )
                        }
                        if (playlistListState.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1f to colors.primaryText.copy(alpha = 0.10f),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (createNew) {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = { onCreateAndAdd(playlistName.trim()) },
                ) {
                    Text("Create and add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun SaveQueueAsPlaylistDialog(
    colors: NaviampColors,
    status: String?,
    title: String = "Save queue as playlist",
    description: String = "Save the current queue in order as a server playlist.",
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, color = colors.secondaryText, fontSize = 12.sp)
                status?.let { Text(it, color = colors.secondaryText, fontSize = 12.sp) }
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = playlistName.isNotBlank(),
                onClick = { onSave(playlistName.trim()) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun SleepTimerDialog(
    colors: NaviampColors,
    timer: NaviampSleepTimerUi,
    onDismissRequest: () -> Unit,
    onTimerSelected: (SleepTimerRequest) -> Unit,
    onCancelTimer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Sleep timer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.active) {
                    Text(timer.label, color = colors.secondaryText, fontSize = 12.sp)
                }
                SleepTimerOptionRow(
                    labels = listOf("15 min", "30 min", "45 min", "60 min"),
                    onClick = { label ->
                        val minutes = label.substringBefore(" ").toIntOrNull() ?: 15
                        onTimerSelected(SleepTimerRequest.DurationMinutes(minutes))
                    },
                )
                SleepTimerOptionRow(
                    labels = listOf("Track end", "Album end", "Queue end"),
                    onClick = { label ->
                        when (label) {
                            "Track end" -> onTimerSelected(SleepTimerRequest.TrackEnd)
                            "Album end" -> onTimerSelected(SleepTimerRequest.AlbumEnd)
                            "Queue end" -> onTimerSelected(SleepTimerRequest.QueueEnd)
                        }
                    },
                )
            }
        },
        confirmButton = {
            if (timer.active) {
                TextButton(onClick = onCancelTimer) {
                    Text("Cancel timer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun SleepTimerOptionRow(
    labels: List<String>,
    onClick: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEach { label ->
            TextButton(
                onClick = { onClick(label) },
                modifier = Modifier.weight(1f),
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NowPlayingTabButton(label: String, selected: Boolean, colors: NaviampColors, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) colors.primaryText else colors.secondaryText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun NowPlayingItemList(
    items: List<NaviampNowPlayingItemUi>,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    currentId: String? = null,
    emptyLabel: String = "Nothing here yet.",
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    useInlinePlaylistPicker: Boolean = true,
    showActions: Boolean = true,
    onClick: (NaviampNowPlayingItemUi) -> Unit,
    rowActions: List<NaviampActionSpec> = queueRowActions(),
    onAction: (NowPlayingItemActionRequest) -> Unit = {},
    swipeRightAction: TrackSwipeAction = TrackSwipeAction.None,
    swipeLeftAction: TrackSwipeAction = TrackSwipeAction.None,
    queueContext: Boolean = false,
    canToggleFavorite: Boolean = false,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(emptyLabel, color = colors.secondaryText, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            if (item.playNextPriority && index == 0) {
                QueueSectionHeader("PLAY NEXT", colors)
            } else if (!item.playNextPriority && index > 0 && items[index - 1].playNextPriority) {
                QueueSectionHeader("QUEUE", colors)
            }
            val selected = item.id == currentId
            var menuExpanded by remember { mutableStateOf(false) }
            var playlistDialogOpen by remember { mutableStateOf(false) }
            val visibleRowActions = rowActions.filter { action ->
                when (action.action) {
                    NaviampAction.GoToAlbum -> item.hasAlbum
                    NaviampAction.GoToArtist -> item.hasArtist
                    else -> true
                }
            } + listOfNotNull(
                NaviampAction.ToggleFavorite.takeIf {
                    canToggleFavorite && rowActions.none { action -> action.action == NaviampAction.ToggleFavorite }
                }?.let { action ->
                    NaviampActionSpec(
                        action = action,
                        label = if (item.favoriteActive) "Unfavorite" else "Favorite",
                    )
                },
            )
            SwipeActionContainer(
                swipeRight = nowPlayingSwipeActionVisual(
                    swipeRightAction,
                    item,
                    queueContext,
                    canToggleFavorite,
                    onAddToPlaylist = {
                        if (useInlinePlaylistPicker) playlistDialogOpen = true
                        else onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddToPlaylist))
                    },
                    onAction = onAction,
                ),
                swipeLeft = nowPlayingSwipeActionVisual(
                    swipeLeftAction,
                    item,
                    queueContext,
                    canToggleFavorite,
                    onAddToPlaylist = {
                        if (useInlinePlaylistPicker) playlistDialogOpen = true
                        else onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddToPlaylist))
                    },
                    onAction = onAction,
                ),
            ) { swipeModifier ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = swipeModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f))
                    .clickable { onClick(item) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                PlatformCoverArt(item.coverArtUrl, colors, 32.dp, 4.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.meta.isNotBlank()) {
                    Text(item.meta, color = colors.mutedText, fontSize = 10.sp)
                }
                if (showActions) {
                    Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = NaviampTransportIcons.MoreVertical,
                            contentDescription = "More actions",
                            tint = colors.secondaryText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    NaviampDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        visibleRowActions.forEach { action ->
                            NaviampDropdownMenuItem(
                                label = action.label,
                                icon = action.icon,
                                enabled = action.enabled,
                                onClick = {
                                    menuExpanded = false
                                    when (action.action) {
                                        NaviampAction.PlayNext ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.PlayNext))
                                        NaviampAction.AddToQueue ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddToQueue))
                                        NaviampAction.StartTrackRadio ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.StartRadio))
                                        NaviampAction.PlayTrackRadioNext ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.PlayTrackRadioNext))
                                        NaviampAction.AddTrackRadioToQueue ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddTrackRadioToQueue))
                                        NaviampAction.RemoveFromQueue ->
                                            nowPlayingQueueIndex(item)?.let { index ->
                                                onAction(
                                                    NowPlayingItemActionRequest(
                                                        item = item,
                                                        target = NowPlayingItemTarget.QueueIndex(index),
                                                        action = NowPlayingItemAction.RemoveFromQueue,
                                                    ),
                                                )
                                            }
                                        NaviampAction.DownloadTrack ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.Download))
                                        NaviampAction.GoToAlbum ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.GoToAlbum))
                                        NaviampAction.GoToArtist ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.GoToArtist))
                                        NaviampAction.ToggleFavorite ->
                                            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.ToggleFavorite))
                                        NaviampAction.AddToPlaylist -> {
                                            if (useInlinePlaylistPicker) {
                                                playlistDialogOpen = true
                                            } else {
                                                onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddToPlaylist))
                                            }
                                        }
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    }
                    }
                }
            }
            }
            if (playlistDialogOpen) {
                AddToPlaylistDialog(
                    title = item.title,
                    colors = colors,
                    playlists = playlistChoices,
                    status = null,
                    onDismissRequest = { playlistDialogOpen = false },
                    onAddToExisting = { playlist ->
                        playlistDialogOpen = false
                        onAction(
                            nowPlayingItemActionRequest(
                                item = item,
                                action = NowPlayingItemAction.AddToPlaylist,
                                playlistChoice = playlist,
                            ),
                        )
                    },
                    onCreateAndAdd = { name ->
                        playlistDialogOpen = false
                        onAction(
                            nowPlayingItemActionRequest(
                                item = item,
                                action = NowPlayingItemAction.CreatePlaylistAndAdd,
                                playlistName = name,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(label: String, colors: NaviampColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.primaryText.copy(alpha = 0.32f)),
        )
    }
}

internal fun nowPlayingSwipeActionVisual(
    action: TrackSwipeAction,
    item: NaviampNowPlayingItemUi,
    queueContext: Boolean,
    canToggleFavorite: Boolean,
    onAddToPlaylist: () -> Unit,
    onAction: (NowPlayingItemActionRequest) -> Unit,
): TrackSwipeActionVisual? = when (action) {
    TrackSwipeAction.None -> null
    TrackSwipeAction.PlayNext -> swipeVisual("Play next", NaviampIcons.Queue) {
        onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.PlayNext))
    }
    TrackSwipeAction.AddToQueue -> swipeVisual("Add to queue", NaviampIcons.Queue) {
        onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.AddToQueue))
    }
    TrackSwipeAction.AddToPlaylist -> swipeVisual("Add to playlist", NaviampIcons.Playlist, onTriggered = onAddToPlaylist)
    TrackSwipeAction.Download -> swipeVisual("Download", NaviampIcons.Downloads) {
        onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.Download))
    }
    TrackSwipeAction.StartRadio -> swipeVisual("Start radio", NaviampTransportIcons.Radio) {
        onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.StartRadio))
    }
    TrackSwipeAction.ToggleFavorite -> if (canToggleFavorite) {
        swipeVisual(
            if (item.favoriteActive) "Unfavorite" else "Favorite",
            if (item.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
        ) {
            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.ToggleFavorite))
        }
    } else null
    TrackSwipeAction.GoToAlbum -> if (item.hasAlbum) {
        swipeVisual("Go to album", NaviampIcons.Album) {
            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.GoToAlbum))
        }
    } else null
    TrackSwipeAction.GoToArtist -> if (item.hasArtist) {
        swipeVisual("Go to artist", NaviampIcons.Artist) {
            onAction(nowPlayingItemActionRequest(item, NowPlayingItemAction.GoToArtist))
        }
    } else null
    TrackSwipeAction.Remove -> {
        val index = nowPlayingQueueIndex(item)
        if (!queueContext || index == null) {
            null
        } else {
            swipeVisual("Remove", NaviampIcons.Trash, destructive = true) {
                onAction(
                    NowPlayingItemActionRequest(
                        item = item,
                        target = NowPlayingItemTarget.QueueIndex(index),
                        action = NowPlayingItemAction.RemoveFromQueue,
                    ),
                )
            }
        }
    }
}

private fun swipeVisual(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onTriggered: () -> Unit,
): TrackSwipeActionVisual = TrackSwipeActionVisual(
    label = label,
    icon = icon,
    background = if (destructive) Color(0xFFC62828) else Color(0xFF2E7D32),
    onTriggered = onTriggered,
)

@Composable
fun NaviampTransportIconButton(
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    colors: NaviampColors,
    prominent: Boolean = false,
    selected: Boolean = false,
    buttonSize: Dp = if (prominent) 44.dp else 34.dp,
    iconSize: Dp = if (prominent) 24.dp else 20.dp,
    centerText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .requiredSize(buttonSize)
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    prominent -> colors.accent.copy(alpha = if (enabled) 0.28f else 0.10f)
                    selected -> colors.accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.primaryText else colors.mutedText.copy(alpha = 0.55f),
            modifier = Modifier.requiredSize(iconSize),
        )
        centerText?.let {
            Text(
                it,
                color = colors.primaryText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun BouncingTitleText(
    text: String,
    color: Color,
    fontSize: Int,
    height: Dp = 18.dp,
    marqueeEnabled: Boolean,
    modifier: Modifier = Modifier,
    bold: Boolean = true,
) {
    val offset = remember(text) { Animatable(0f) }
    var containerWidth by remember { mutableStateOf(0) }
    var textWidth by remember(text, fontSize) { mutableStateOf(0) }
    val overflow = (textWidth - containerWidth).coerceAtLeast(0)

    LaunchedEffect(text, overflow, marqueeEnabled) {
        offset.snapTo(0f)
        if (marqueeEnabled && overflow > 0) {
            while (true) {
                kotlinx.coroutines.delay(800)
                offset.animateTo(
                    targetValue = -overflow.toFloat(),
                    animationSpec = tween(
                        durationMillis = (overflow * 24).coerceAtLeast(1800),
                        easing = LinearEasing,
                    ),
                )
                kotlinx.coroutines.delay(800)
                offset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (overflow * 24).coerceAtLeast(1800),
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .onSizeChanged { containerWidth = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Layout(
            content = {
                Text(
                    text,
                    color = color,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 1).sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.onSizeChanged { textWidth = it.width },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                ),
            )
            val width = constraints.maxWidth
            val height = if (constraints.maxHeight == Constraints.Infinity) {
                placeable.height.coerceAtLeast(constraints.minHeight)
            } else {
                placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
            }
            layout(width, height) {
                val x = if (overflow > 0) {
                    offset.value.roundToInt()
                } else {
                    (width - placeable.width) / 2
                }
                placeable.placeRelative(x, (height - placeable.height) / 2)
            }
        }
    }
}

@Composable
private fun NowPlayingIdentityText(
    text: String,
    color: Color,
    fontSize: Int,
    height: Dp,
    marqueeEnabled: Boolean,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    if (marqueeEnabled) {
        BouncingTitleText(
            text = text,
            color = color,
            fontSize = fontSize,
            height = height,
            marqueeEnabled = true,
            modifier = modifier.fillMaxWidth(),
            bold = bold,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(height),
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 1).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val NowPlayingUi.progressFraction: Double
    get() = playbackFraction(positionSeconds, durationSeconds)

private fun secondsLabel(seconds: Double?): String {
    val total = seconds?.roundToInt() ?: return "--:--"
    val minutes = total / 60
    val remainder = total % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
