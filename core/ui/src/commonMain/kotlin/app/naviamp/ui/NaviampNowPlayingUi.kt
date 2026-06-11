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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.naviamp.domain.waveform.seekSecondsForFraction
import app.naviamp.domain.playback.SleepTimerRequest
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
)

data class NaviampNowPlayingActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onPlayCurrent: () -> Unit = {},
    val onSeek: (Double) -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onCycleRepeatMode: () -> Unit = {},
    val onVolumeChanged: (Int) -> Unit = {},
    val onToggleLyrics: () -> Unit = {},
    val onLyricsOffsetChanged: (Int) -> Unit = {},
    val onTrackRadio: () -> Unit = {},
    val onAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit = {},
    val onCreatePlaylistAndAdd: (String) -> Unit = {},
    val onSaveQueueAsPlaylist: (String) -> Unit = {},
    val onSleepTimerSelected: (SleepTimerRequest) -> Unit = {},
    val onCancelSleepTimer: () -> Unit = {},
    val onDownloadTrack: () -> Unit = {},
    val onToggleVisualizer: () -> Unit = {},
    val onGoToAlbum: () -> Unit = {},
    val onGoToArtist: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onRatingSelected: (Int?) -> Unit = {},
    val onCollapse: () -> Unit = {},
    val onQueueItemSelected: (NaviampNowPlayingItemUi) -> Unit = {},
    val onRelatedItemSelected: (NaviampNowPlayingItemUi) -> Unit = {},
    val onRadioStationSelected: (NaviampNowPlayingItemUi) -> Unit = {},
    val onQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit = {},
    val onQueueItemAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    val onQueueItemCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit = { _, _ -> },
    val onQueueItemDownload: (NaviampNowPlayingItemUi) -> Unit = {},
    val onVisualizerSelected: (NaviampVisualizer) -> Unit = {},
)

@Composable
fun NaviampNowPlayingPanel(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    modifier: Modifier = Modifier,
    visualizerBandsProvider: () -> List<Float> = { nowPlaying.visualizerFrame?.bands.orEmpty() },
    selectedVisualizer: NaviampVisualizer = NaviampVisualizer.AudioSphere,
    visualizerColors: NaviampPlayerColors = NaviampPlayerColors.fallback(colors),
) {
    var selectedTab by remember(nowPlaying.id, nowPlaying.isLive) { mutableStateOf(NaviampNowPlayingTab.UpNext) }
    val showStationList = nowPlaying.isLive
    val artSizeDefault = 286.dp
    val sidePanelHeight = 340.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 300.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp
        val viewportMaxHeight = maxHeight
        val compactStackLayout = !wideLayout && viewportMaxHeight < 600.dp
        val stackedArtSize = ((viewportMaxHeight * 0.45f) - 24.dp)
            .coerceIn(150.dp, artSizeDefault)
        val splitArtSize = ((viewportMaxHeight / 2f) - 28.dp)
            .coerceIn(170.dp, artSizeDefault)
        val artSize = when {
            wideLayout -> ((viewportMaxHeight * 0.58f) - 24.dp).coerceIn(170.dp, 350.dp)
            compactStackLayout -> stackedArtSize
            maxWidth < 380.dp -> splitArtSize.coerceAtMost(238.dp)
            !wideLayout -> splitArtSize
            else -> artSizeDefault
        }
        val wideDetailsHeight = if (wideLayout) {
            (viewportMaxHeight - artSize - 28.dp).coerceAtLeast(148.dp)
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
                        onToggleVisualizer = actions.onToggleVisualizer,
                        onVisualizerSelected = actions.onVisualizerSelected,
                    )
                    NowPlayingDetails(
                        nowPlaying = nowPlaying,
                        colors = colors,
                        playerColors = visualizerColors,
                        actions = actions,
                        selectedVisualizer = selectedVisualizer,
                        compactLayout = viewportMaxHeight < 640.dp,
                        availableHeight = wideDetailsHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wideDetailsHeight)
                            .padding(top = 8.dp),
                    )
                }
                NowPlayingSidePanel(
                    nowPlaying = nowPlaying,
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
            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            fun scrollToPlayer() {
                coroutineScope.launch { scrollState.animateScrollTo(0) }
            }
            val itemActions = actions.copy(
                onQueueItemSelected = {
                    actions.onQueueItemSelected(it)
                    scrollToPlayer()
                },
                onRelatedItemSelected = {
                    actions.onRelatedItemSelected(it)
                    scrollToPlayer()
                },
                onRadioStationSelected = {
                    actions.onRadioStationSelected(it)
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .padding(horizontal = 14.dp),
                    ) {
                        if (nowPlaying.lyricsVisible && !showStationList) {
                            LyricsPanel(
                                nowPlaying = nowPlaying,
                                colors = colors,
                                onSeek = actions.onSeek,
                                onOffsetChanged = actions.onLyricsOffsetChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(artSize),
                            )
                        } else {
                            NowPlayingArtSurface(
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
                                onToggleVisualizer = actions.onToggleVisualizer,
                                onVisualizerSelected = actions.onVisualizerSelected,
                            )
                        }
                        NowPlayingDetails(
                            nowPlaying = nowPlaying,
                            colors = colors,
                            playerColors = visualizerColors,
                            actions = actions,
                            selectedVisualizer = selectedVisualizer,
                            compactLayout = true,
                            availableHeight = viewportHeight,
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
                                onSeek = actions.onSeek,
                                onOffsetChanged = actions.onLyricsOffsetChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(artSize),
                            )
                        } else {
                            NowPlayingArtSurface(
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
                                onToggleVisualizer = actions.onToggleVisualizer,
                                onVisualizerSelected = actions.onVisualizerSelected,
                            )
                        }
                    }
                    NowPlayingDetails(
                        nowPlaying = nowPlaying,
                        colors = colors,
                        playerColors = visualizerColors,
                        actions = actions,
                        selectedVisualizer = selectedVisualizer,
                        mobileLayout = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight / 2)
                            .padding(horizontal = 14.dp),
                    )
                }
                NowPlayingSidePanel(
                    nowPlaying = nowPlaying,
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
private fun NowPlayingArtSurface(
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
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
) {
    val shadowMargin = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val toggleModifier = Modifier.clickable(enabled = visualizerAvailable, onClick = onToggleVisualizer)
    var visualizerMenuExpanded by remember { mutableStateOf(false) }

    if (visualizerVisible && visualizerAvailable) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(size + shadowMargin * 2)
                .visualizerContextMenu { visualizerMenuExpanded = true }
                .then(toggleModifier),
        ) {
            LiveVisualizerSurface(
                coverArtUrl = coverArtUrl,
                bandsProvider = visualizerBandsProvider,
                visualizer = selectedVisualizer,
                visualizerColors = visualizerColors,
                active = visualizerActive,
                colors = colors,
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
            modifier = Modifier.size(size + shadowMargin * 2),
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
    NaviampVisualizer.entries.forEach { visualizer ->
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
    mobileLayout: Boolean = false,
    compactLayout: Boolean = false,
    availableHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var visualizerMenuExpanded by remember { mutableStateOf(false) }
    var trackDetailsOpen by remember { mutableStateOf(false) }
    var playlistDialogOpen by remember { mutableStateOf<NaviampNowPlayingItemUi?>(null) }
    var saveQueueDialogOpen by remember { mutableStateOf(false) }
    var sleepTimerDialogOpen by remember { mutableStateOf(false) }
    var scrubberValue by remember(nowPlaying.id) { mutableFloatStateOf(nowPlaying.progressFraction.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var volumeValue by remember { mutableFloatStateOf(nowPlaying.volumePercent.coerceIn(0, 100) / 100f) }
    var isChangingVolume by remember { mutableStateOf(false) }
    val canSeek = nowPlaying.canSeek && nowPlaying.durationSeconds != null && !nowPlaying.isLive
    val canTogglePlayback = nowPlaying.canPlayPause
    val height = availableHeight ?: Dp.Unspecified
    val pinBottomActions = !mobileLayout && height != Dp.Unspecified
    val showVolume = !compactLayout || height == Dp.Unspecified || height >= 225.dp
    val showTrackExtras = !compactLayout || height == Dp.Unspecified || height >= 195.dp
    val showTrackIdentity = !compactLayout || height == Dp.Unspecified || height >= 165.dp
    val canSetTrackPreference = !nowPlaying.isLive && (nowPlaying.canFavorite || nowPlaying.canRate)
    val controlColors = colors.copy(accent = playerColors.accent)
    val trackPreferenceLabel = when {
        nowPlaying.favoriteActive && nowPlaying.canRate -> "Dislike track"
        nowPlaying.favoriteActive -> "Remove favorite"
        nowPlaying.userRating == 1 && nowPlaying.canRate -> "Clear track preference"
        else -> "Favorite track"
    }

    fun cycleTrackPreference() {
        when {
            nowPlaying.favoriteActive -> {
                if (nowPlaying.canFavorite) actions.onToggleFavorite()
                if (nowPlaying.canRate) actions.onRatingSelected(1)
            }
            nowPlaying.userRating == 1 && nowPlaying.canRate -> {
                actions.onRatingSelected(null)
            }
            nowPlaying.canFavorite -> {
                actions.onToggleFavorite()
            }
            nowPlaying.canRate -> {
                actions.onRatingSelected(5)
            }
        }
    }

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
        verticalArrangement = if (mobileLayout && !compactLayout) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(if (compactLayout) 5.dp else 6.dp)
        },
        modifier = modifier.padding(bottom = if (mobileLayout) 4.dp else 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (nowPlaying.isLive) "LIVE" else secondsLabel(nowPlaying.positionSeconds),
                color = colors.primaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(42.dp),
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
                        seekSecondsForFraction(seekFraction, nowPlaying.durationSeconds)?.let(actions.onSeek)
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
                fontSize = 11.sp,
                modifier = Modifier.width(42.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (mobileLayout) 8.dp else 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (mobileLayout) 8.dp else 3.dp, bottom = if (mobileLayout) 2.dp else 5.dp),
        ) {
            if (showTrackIdentity) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (mobileLayout) 2.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = if (mobileLayout && !compactLayout) (-14).dp else 0.dp)
                        .padding(bottom = if (mobileLayout) 1.dp else 0.dp),
                ) {
                    BouncingTitleText(
                        text = nowPlaying.title,
                        color = colors.primaryText,
                        fontSize = 15,
                        marqueeEnabled = nowPlaying.id.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        nowPlaying.subtitle,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.clickable(enabled = !nowPlaying.isLive, onClick = actions.onGoToArtist),
                    )
                    Text(
                        if (nowPlaying.isLive) "Live stream" else nowPlaying.albumLine.ifBlank { nowPlaying.stateLabel },
                        color = colors.secondaryText.copy(alpha = 0.84f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                    )
                }
            }

            if (showTrackExtras) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!nowPlaying.isLive && (nowPlaying.canFavorite || nowPlaying.canRate || nowPlaying.favoriteActive || nowPlaying.userRating != null)) {
                        RatingRow(
                            favoriteActive = nowPlaying.favoriteActive,
                            canFavorite = nowPlaying.canFavorite,
                            rating = nowPlaying.userRating,
                            canRate = nowPlaying.canRate,
                            colors = colors,
                            onToggleFavorite = actions.onToggleFavorite,
                            onRatingSelected = actions.onRatingSelected,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!nowPlaying.isLive && nowPlaying.audioInfo.isNotBlank()) {
                        Text(
                            nowPlaying.audioInfo,
                            color = colors.secondaryText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (nowPlaying.canChangeVolume && showVolume) {
            VolumeRow(
                value = volumeValue,
                isChangingVolume = { isChangingVolume = it },
                onValueChanged = {
                    volumeValue = it
                    actions.onVolumeChanged((it * 100).toInt().coerceIn(0, 100))
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
                buttonSize = 28.dp,
                iconSize = 16.dp,
                onClick = actions.onToggleShuffle,
            )
            NaviampTransportIconButton(
                enabled = nowPlaying.hasPrevious,
                icon = NaviampTransportIcons.Previous,
                contentDescription = "Previous",
                colors = controlColors,
                onClick = actions.onPrevious,
            )
            NaviampTransportIconButton(
                enabled = canTogglePlayback,
                icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                colors = controlColors,
                prominent = true,
                onClick = {
                    when {
                        nowPlaying.isPlaying -> actions.onPause()
                        nowPlaying.isPaused -> actions.onResume()
                        else -> actions.onPlayCurrent()
                    }
                },
            )
            NaviampTransportIconButton(
                enabled = nowPlaying.hasNext,
                icon = NaviampTransportIcons.Next,
                contentDescription = "Next",
                colors = controlColors,
                onClick = actions.onNext,
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
                buttonSize = 28.dp,
                iconSize = 16.dp,
                centerText = if (nowPlaying.repeatMode == NaviampRepeatMode.Track) "1" else null,
                onClick = actions.onCycleRepeatMode,
            )
        }

        if ((compactLayout && mobileLayout) || pinBottomActions) {
            Box(modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (mobileLayout) 46.dp else 44.dp)
                .padding(bottom = 0.dp),
        ) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                NaviampTransportIconButton(
                    enabled = nowPlaying.canStartRadio,
                    icon = NaviampTransportIcons.Radio,
                    contentDescription = "Start track radio",
                    colors = colors,
                    buttonSize = 44.dp,
                    iconSize = 26.dp,
                    onClick = actions.onTrackRadio,
                )
                NaviampTransportIconButton(
                    enabled = nowPlaying.canAddToPlaylist,
                    icon = NaviampIcons.Playlist,
                    contentDescription = "Add track to playlist",
                    colors = colors,
                    buttonSize = 44.dp,
                    iconSize = 26.dp,
                    onClick = {
                        if (nowPlaying.useInlinePlaylistPicker) {
                            playlistDialogOpen = NaviampNowPlayingItemUi(
                                id = nowPlaying.id,
                                title = nowPlaying.title,
                                subtitle = nowPlaying.subtitle,
                                coverArtUrl = nowPlaying.coverArtUrl,
                            )
                        } else {
                            actions.onAddToPlaylist(null)
                        }
                    },
                )
            }
            IconButton(
                onClick = actions.onCollapse,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center),
            ) {
                Icon(
                    imageVector = NaviampIcons.ChevronDown,
                    contentDescription = "Back",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(26.dp),
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
                    buttonSize = 44.dp,
                    iconSize = 26.dp,
                    onClick = actions.onToggleLyrics,
                )
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    NaviampTransportIconButton(
                        enabled = nowPlaying.menuEnabled,
                        icon = NaviampTransportIcons.Menu,
                        contentDescription = "Track actions",
                        colors = colors,
                        buttonSize = 44.dp,
                        iconSize = 26.dp,
                        onClick = { actionMenuExpanded = true },
                    )
                    NaviampDropdownMenu(
                        expanded = actionMenuExpanded,
                        onDismissRequest = { actionMenuExpanded = false },
                        offset = DpOffset(0.dp, 6.dp),
                    ) {
                        nowPlayingTrackMenuActions(
                            lyricsVisible = nowPlaying.lyricsVisible,
                            lyricsAvailable = nowPlaying.lyricsAvailable,
                            visualizerVisible = nowPlaying.visualizerVisible,
                            visualizerAvailable = nowPlaying.visualizerAvailable,
                            isLive = nowPlaying.isLive,
                            hasDetails = nowPlaying.detailSections.isNotEmpty(),
                            trackPreferenceLabel = trackPreferenceLabel,
                            canSetTrackPreference = canSetTrackPreference,
                            canStartRadio = nowPlaying.canStartRadio,
                            canAddToPlaylist = nowPlaying.canAddToPlaylist,
                            canSaveQueueAsPlaylist = nowPlaying.canSaveQueueAsPlaylist,
                            sleepTimerLabel = nowPlaying.sleepTimer.label,
                        ).forEach { action ->
                            NaviampDropdownMenuItem(
                                label = action.label,
                                icon = action.icon,
                                enabled = action.enabled,
                                onClick = {
                                    actionMenuExpanded = false
                                    when (action.action) {
                                        NaviampAction.ShowLyrics,
                                        NaviampAction.HideLyrics -> actions.onToggleLyrics()
                                        NaviampAction.ShowVisualizer,
                                        NaviampAction.HideVisualizer -> actions.onToggleVisualizer()
                                        NaviampAction.ChangeVisualizer -> visualizerMenuExpanded = true
                                        NaviampAction.DownloadTrack -> actions.onDownloadTrack()
                                        NaviampAction.TrackDetails -> trackDetailsOpen = true
                                        NaviampAction.TrackPreference -> cycleTrackPreference()
                                        NaviampAction.StartTrackRadio -> actions.onTrackRadio()
                                        NaviampAction.GoToAlbum -> actions.onGoToAlbum()
                                        NaviampAction.GoToArtist -> actions.onGoToArtist()
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
                                                actions.onAddToPlaylist(null)
                                            }
                                        }
                                        NaviampAction.SaveQueueAsPlaylist -> saveQueueDialogOpen = true
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
                                actions.onVisualizerSelected(it)
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
                actions.onSaveQueueAsPlaylist(name)
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
                actions.onSleepTimerSelected(request)
            },
            onCancelTimer = {
                sleepTimerDialogOpen = false
                actions.onCancelSleepTimer()
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
                    actions.onAddToPlaylist(playlist)
                } else {
                    actions.onQueueItemAddToPlaylist(item, playlist)
                }
            },
            onCreateAndAdd = { name ->
                playlistDialogOpen = null
                if (item.id == nowPlaying.id) {
                    actions.onCreatePlaylistAndAdd(name)
                } else {
                    actions.onQueueItemCreatePlaylistAndAdd(item, name)
                }
            },
        )
    }
}

@Composable
private fun LiveVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    PlatformLiveVisualizerSurface(
        coverArtUrl = coverArtUrl,
        bandsProvider = bandsProvider,
        visualizer = visualizer,
        visualizerColors = visualizerColors,
        active = active,
        colors = colors,
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
    colors: NaviampColors,
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (amplitudes.isEmpty()) {
                drawFallbackScrubLine(value, enabled, colors)
                return@Canvas
            }

            val centerY = size.height / 2f
            val visibleBars = minOf(amplitudes.size, (size.width / 3f).toInt().coerceAtLeast(24))
            val step = size.width / visibleBars.toFloat()
            val strokeWidth = (step * 0.62f).coerceIn(1.2f, 2.4f)
            val minBarHeight = 2.5f
            val maxBarHeight = size.height * 0.92f

            repeat(visibleBars) { index ->
                val sourceIndex = if (visibleBars == 1) {
                    0
                } else {
                    ((index / (visibleBars - 1f)) * (amplitudes.size - 1)).toInt()
                }
                val amplitude = amplitudes[sourceIndex].coerceIn(0f, 1f)
                val barHeight = (minBarHeight + amplitude * (maxBarHeight - minBarHeight))
                    .coerceAtMost(size.height)
                val ratio = if (visibleBars == 1) 0f else index / (visibleBars - 1f)
                val color = when {
                    !enabled -> colors.mutedText.copy(alpha = 0.28f)
                    ratio <= value -> colors.accent.copy(alpha = 0.94f)
                    else -> colors.primaryText.copy(alpha = 0.30f)
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
        color = colors.primaryText.copy(alpha = 0.24f * disabledAlpha),
        start = Offset.Zero.copy(y = centerY),
        end = Offset(size.width, centerY),
        strokeWidth = 5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = colors.accent.copy(alpha = 0.88f * disabledAlpha),
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
                .width(24.dp)
                .clickable(enabled = canFavorite, onClick = onToggleFavorite),
        ) {
            Icon(
                imageVector = if (favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
                contentDescription = if (favoriteActive) "Remove favorite" else "Favorite",
                tint = if (favoriteActive) colors.primaryText else inactiveColor,
                modifier = Modifier.size(17.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.width(92.dp)) {
            (1..5).forEach { value ->
                val selected = (rating ?: 0) >= value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(18.dp)
                        .clickable(enabled = canRate) {
                            onRatingSelected(if (rating == value) null else value)
                        },
                ) {
                    Icon(
                        imageVector = if (selected) NaviampTransportIcons.StarFilled else NaviampTransportIcons.Star,
                        contentDescription = "$value star rating",
                        tint = if (selected) colors.primaryText else inactiveColor,
                        modifier = Modifier.size(17.dp),
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
                onClick = actions.onRadioStationSelected,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

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
                NowPlayingTabButton("RELATED", selectedTab == NaviampNowPlayingTab.Related, colors) {
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
            NaviampNowPlayingTab.Related -> actions.onRelatedItemSelected
            else -> actions.onQueueItemSelected
        }
        NowPlayingItemList(
            items = items,
            colors = colors,
            emptyLabel = when (selectedTab) {
                NaviampNowPlayingTab.BackTo -> "Playback history will appear here."
                NaviampNowPlayingTab.UpNext -> "Queue is empty."
                NaviampNowPlayingTab.Related -> "Related tracks are not loaded."
            },
            playlistChoices = nowPlaying.playlistChoices,
            useInlinePlaylistPicker = nowPlaying.useInlinePlaylistPicker,
            onClick = onClick,
            onRadio = actions.onQueueItemRadio,
            onAddToPlaylist = actions.onQueueItemAddToPlaylist,
            onCreatePlaylistAndAdd = actions.onQueueItemCreatePlaylistAndAdd,
            onDownload = actions.onQueueItemDownload,
            modifier = Modifier.weight(if (showLyrics) 0.62f else 1f),
        )
        if (showLyrics) {
            LyricsPanel(
                nowPlaying = nowPlaying,
                colors = colors,
                onSeek = actions.onSeek,
                onOffsetChanged = actions.onLyricsOffsetChanged,
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
    val positionMillis = nowPlaying.positionSeconds?.times(1000)?.toLong()
    val activeLineIndex = remember(nowPlaying.lyricsLines, positionMillis, nowPlaying.lyricsOffsetMillis) {
        nowPlaying.lyricsLines.indexOfLast { line ->
            val startMillis = line.startMillis?.plus(nowPlaying.lyricsOffsetMillis)
            startMillis != null && positionMillis != null && startMillis <= positionMillis + LyricsAutoScrollLeadMillis
        }
    }

    LaunchedEffect(activeLineIndex, nowPlaying.lyricsLines.size) {
        if (activeLineIndex < 0) return@LaunchedEffect
        listState.animateScrollToItem((activeLineIndex - 4).coerceAtLeast(0))
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
private const val LyricsOffsetStepMillis = 100

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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
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
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Save queue as playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save the current queue in order as a server playlist.", color = colors.secondaryText, fontSize = 12.sp)
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
    currentId: String? = null,
    emptyLabel: String = "Nothing here yet.",
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    useInlinePlaylistPicker: Boolean = true,
    showActions: Boolean = true,
    onClick: (NaviampNowPlayingItemUi) -> Unit,
    onRadio: (NaviampNowPlayingItemUi) -> Unit = {},
    onAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit = { _, _ -> },
    onDownload: (NaviampNowPlayingItemUi) -> Unit = {},
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(emptyLabel, color = colors.secondaryText, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { item ->
            val selected = item.id == currentId
            var menuExpanded by remember { mutableStateOf(false) }
            var playlistDialogOpen by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
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
                        Text("⋮", color = colors.secondaryText, fontSize = 15.sp)
                    }
                    NaviampDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        queueRowActions().forEach { action ->
                            NaviampDropdownMenuItem(
                                label = action.label,
                                icon = action.icon,
                                enabled = action.enabled,
                                onClick = {
                                    menuExpanded = false
                                    when (action.action) {
                                        NaviampAction.StartTrackRadio -> onRadio(item)
                                        NaviampAction.DownloadTrack -> onDownload(item)
                                        NaviampAction.AddToPlaylist -> {
                                            if (useInlinePlaylistPicker) {
                                                playlistDialogOpen = true
                                            } else {
                                                onAddToPlaylist(item, null)
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
            if (playlistDialogOpen) {
                AddToPlaylistDialog(
                    title = item.title,
                    colors = colors,
                    playlists = playlistChoices,
                    status = null,
                    onDismissRequest = { playlistDialogOpen = false },
                    onAddToExisting = { playlist ->
                        playlistDialogOpen = false
                        onAddToPlaylist(item, playlist)
                    },
                    onCreateAndAdd = { name ->
                        playlistDialogOpen = false
                        onCreatePlaylistAndAdd(item, name)
                    },
                )
            }
        }
    }
}

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
            .size(buttonSize)
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    prominent -> colors.accent.copy(alpha = if (enabled) 0.28f else 0.10f)
                    selected -> colors.accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            ),
    ) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) colors.primaryText else colors.mutedText.copy(alpha = 0.55f),
                modifier = Modifier.size(iconSize),
            )
        }
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
    marqueeEnabled: Boolean,
    modifier: Modifier = Modifier,
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
            .height(18.dp)
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
                    fontWeight = FontWeight.Bold,
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

private val NowPlayingUi.progressFraction: Double
    get() = playbackFraction(positionSeconds, durationSeconds)

private fun secondsLabel(seconds: Double?): String {
    val total = seconds?.roundToInt() ?: return "--:--"
    val minutes = total / 60
    val remainder = total % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
