package app.naviamp.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun NowPlayingPanel(
    appColors: AppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsSoftwareVolume: Boolean,
    supportsTrackFavorites: Boolean,
    supportsTrackRatings: Boolean,
    nowPlayingTrack: Track?,
    nowPlayingWaveform: AudioWaveform?,
    coverArtUrl: String?,
    upNext: List<Track>,
    firstUpNextQueueIndex: Int,
    upNextCoverArtUrl: (Track) -> String?,
    relatedTracks: List<Track>,
    relatedCoverArtUrl: (Track) -> String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    volumePercent: Int,
    onPlayerColorsChanged: (PlayerColors) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleTrackFavorite: (Track) -> Unit,
    onTrackRatingSelected: (Track, Int?) -> Unit,
    onArtistSelected: (Track) -> Unit,
    onAlbumSelected: (Track) -> Unit,
    onTrackRadioSelected: (Track) -> Unit,
    onQueueIndexSelected: (Int) -> Unit,
    onRelatedTrackSelected: (Int) -> Unit,
    onRelatedTrackRadioSelected: (Track) -> Unit,
    onCollapseToHome: () -> Unit,
) {
    val coverArtState = rememberCoverArtState(coverArtUrl, appColors)
    val playerColors = remember(coverArtState.palette, appColors) {
        PlayerColors.from(coverArtState.palette, appColors)
    }

    LaunchedEffect(coverArtState.palette) {
        onPlayerColorsChanged(playerColors)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .padding(0.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp
        val artSize = when {
            wideLayout -> 350.dp
            maxWidth < 380.dp -> 238.dp
            else -> 286.dp
        }

        if (wideLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CoverArt(
                    coverArtState = coverArtState,
                    appColors = appColors,
                    size = artSize,
                )
                PlayerDetails(
                    appColors = appColors,
                    playerColors = playerColors,
                    playbackEngineName = playbackEngineName,
                    supportsSoftwareVolume = supportsSoftwareVolume,
                    nowPlayingTrack = nowPlayingTrack,
                    nowPlayingWaveform = nowPlayingWaveform,
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    volumePercent = volumePercent,
                    supportsPause = supportsPause,
                    supportsSeek = supportsSeek,
                    supportsTrackFavorites = supportsTrackFavorites,
                    supportsTrackRatings = supportsTrackRatings,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPause = onPause,
                    onResume = onResume,
                    onPlayCurrent = onPlayCurrent,
                    onSeek = onSeek,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onVolumeChanged = onVolumeChanged,
                    onToggleTrackFavorite = onToggleTrackFavorite,
                    onTrackRatingSelected = onTrackRatingSelected,
                    onArtistSelected = onArtistSelected,
                    onAlbumSelected = onAlbumSelected,
                    onTrackRadioSelected = onTrackRadioSelected,
                    onCollapseToHome = onCollapseToHome,
                    modifier = Modifier.weight(0.9f),
                )
                UpNextPanel(
                    appColors = appColors,
                    upNext = upNext,
                    firstQueueIndex = firstUpNextQueueIndex,
                    coverArtUrl = upNextCoverArtUrl,
                    relatedTracks = relatedTracks,
                    relatedCoverArtUrl = relatedCoverArtUrl,
                    onQueueIndexSelected = onQueueIndexSelected,
                    onRelatedTrackSelected = onRelatedTrackSelected,
                    onRelatedTrackRadioSelected = onRelatedTrackRadioSelected,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(420.dp),
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CoverArt(
                    coverArtState = coverArtState,
                    appColors = appColors,
                    size = artSize,
                )
                PlayerDetails(
                    appColors = appColors,
                    playerColors = playerColors,
                    playbackEngineName = playbackEngineName,
                    supportsSoftwareVolume = supportsSoftwareVolume,
                    nowPlayingTrack = nowPlayingTrack,
                    nowPlayingWaveform = nowPlayingWaveform,
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    volumePercent = volumePercent,
                    supportsPause = supportsPause,
                    supportsSeek = supportsSeek,
                    supportsTrackFavorites = supportsTrackFavorites,
                    supportsTrackRatings = supportsTrackRatings,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPause = onPause,
                    onResume = onResume,
                    onPlayCurrent = onPlayCurrent,
                    onSeek = onSeek,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onVolumeChanged = onVolumeChanged,
                    onToggleTrackFavorite = onToggleTrackFavorite,
                    onTrackRatingSelected = onTrackRatingSelected,
                    onArtistSelected = onArtistSelected,
                    onAlbumSelected = onAlbumSelected,
                    onTrackRadioSelected = onTrackRadioSelected,
                    onCollapseToHome = onCollapseToHome,
                    modifier = Modifier.fillMaxWidth(),
                )
                UpNextPanel(
                    appColors = appColors,
                    upNext = upNext,
                    firstQueueIndex = firstUpNextQueueIndex,
                    coverArtUrl = upNextCoverArtUrl,
                    relatedTracks = relatedTracks,
                    relatedCoverArtUrl = relatedCoverArtUrl,
                    onQueueIndexSelected = onQueueIndexSelected,
                    onRelatedTrackSelected = onRelatedTrackSelected,
                    onRelatedTrackRadioSelected = onRelatedTrackRadioSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
            }
        }
    }
}

@Composable
fun MiniPlayerPanel(
    appColors: AppColors,
    nowPlayingTrack: Track?,
    coverArtUrl: String?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playbackState: PlaybackState,
    onPlayerColorsChanged: (PlayerColors) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val coverArtState = rememberCoverArtState(coverArtUrl, appColors)
    val playerColors = remember(coverArtState.palette, appColors) {
        PlayerColors.from(coverArtState.palette, appColors)
    }
    val canTogglePause = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error

    LaunchedEffect(coverArtState.palette) {
        onPlayerColorsChanged(playerColors)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        CoverArt(
            coverArtState = coverArtState,
            appColors = appColors,
            size = 40.dp,
            elevated = false,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                nowPlayingTrack?.artistName ?: "Nothing Playing",
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
            Text(
                nowPlayingTrack?.title ?: "Queue is empty",
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        TransportIconButton(
            enabled = hasPrevious,
            icon = TransportIcons.Previous,
            contentDescription = "Previous",
            appColors = appColors,
            onClick = onPrevious,
        )
        TransportIconButton(
            enabled = canTogglePause,
            icon = if (playbackState == PlaybackState.Playing) {
                TransportIcons.Pause
            } else {
                TransportIcons.Play
            },
            contentDescription = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
            appColors = appColors,
            onClick = {
                if (playbackState == PlaybackState.Playing) {
                    onPause()
                } else if (playbackState == PlaybackState.Paused) {
                    onResume()
                } else {
                    onPlayCurrent()
                }
            },
        )
        TransportIconButton(
            enabled = hasNext,
            icon = TransportIcons.Next,
            contentDescription = "Next",
            appColors = appColors,
            onClick = onNext,
        )
    }
}

@Composable
private fun PlayerDetails(
    appColors: AppColors,
    playerColors: PlayerColors,
    playbackEngineName: String,
    supportsSoftwareVolume: Boolean,
    nowPlayingTrack: Track?,
    nowPlayingWaveform: AudioWaveform?,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    volumePercent: Int,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsTrackFavorites: Boolean,
    supportsTrackRatings: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleTrackFavorite: (Track) -> Unit,
    onTrackRatingSelected: (Track, Int?) -> Unit,
    onArtistSelected: (Track) -> Unit,
    onAlbumSelected: (Track) -> Unit,
    onTrackRadioSelected: (Track) -> Unit,
    onCollapseToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubberValue by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var volumeValue by remember { mutableFloatStateOf(volumePercent.coerceIn(0, 100) / 100f) }
    var isChangingVolume by remember { mutableStateOf(false) }
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val effectiveProgressFraction = playbackProgress.fraction(effectiveDurationSeconds)
    val canSeek = supportsSeek && effectiveDurationSeconds != null && nowPlayingTrack != null
    val canTogglePause = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error &&
        (supportsPause || playbackState != PlaybackState.Playing)
    val metadataTextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 14.sp,
    )
    val audioInfo = nowPlayingTrack?.playbackAudioInfo(playbackEngineName)
    var actionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(effectiveProgressFraction) {
        if (!isScrubbing) {
            scrubberValue = effectiveProgressFraction.toFloat()
        }
    }

    LaunchedEffect(volumePercent) {
        if (!isChangingVolume) {
            volumeValue = volumePercent.coerceIn(0, 100) / 100f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                playbackProgress.positionLabel(),
                color = appColors.primaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(42.dp),
            )
            if (nowPlayingWaveform != null) {
                WaveformScrubber(
                    waveform = nowPlayingWaveform,
                    value = scrubberValue,
                    enabled = canSeek,
                    playerColors = playerColors,
                    appColors = appColors,
                    onValueChange = {
                        isScrubbing = true
                        scrubberValue = it
                    },
                    onValueChangeFinished = {
                        effectiveDurationSeconds?.let { duration ->
                            onSeek(scrubberValue * duration)
                        }
                        isScrubbing = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                )
            } else {
                Slider(
                    value = scrubberValue,
                    onValueChange = {
                        isScrubbing = true
                        scrubberValue = it
                    },
                    onValueChangeFinished = {
                        effectiveDurationSeconds?.let { duration ->
                            onSeek(scrubberValue * duration)
                        }
                        isScrubbing = false
                    },
                    enabled = canSeek,
                    colors = playerSliderColors(playerColors, appColors),
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                )
            }
            Text(
                effectiveDurationSeconds.durationLabel(),
                color = appColors.primaryText,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                modifier = Modifier.width(42.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, bottom = 5.dp),
        ) {
            Text(
                nowPlayingTrack?.title ?: "Queue will appear here after connection",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = metadataTextStyle.copy(fontSize = 15.sp, lineHeight = 16.sp),
            )
            Text(
                nowPlayingTrack?.artistName ?: "Nothing Playing",
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = metadataTextStyle,
                modifier = Modifier.clickable(enabled = nowPlayingTrack?.artistId != null) {
                    nowPlayingTrack?.let(onArtistSelected)
                },
            )
            Text(
                nowPlayingTrack?.albumTitleWithYear() ?: playbackState.label(),
                color = appColors.secondaryText.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = metadataTextStyle,
                modifier = Modifier.clickable(enabled = nowPlayingTrack?.albumId != null) {
                    nowPlayingTrack?.let(onAlbumSelected)
                },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            val canSetFavorite = nowPlayingTrack != null && supportsTrackFavorites
            Text(
                nowPlayingTrack?.favoriteGlyph() ?: "♡",
                color = if (nowPlayingTrack?.favoritedAtIso8601 != null) {
                    playerColors.accent
                } else {
                    Color.White.copy(alpha = 0.72f)
                },
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(24.dp)
                    .clickable(enabled = canSetFavorite) {
                        nowPlayingTrack?.let(onToggleTrackFavorite)
                    },
            )
            TrackRatingControl(
                track = nowPlayingTrack,
                enabled = supportsTrackRatings,
                activeColor = playerColors.accent,
                inactiveColor = Color.White.copy(alpha = 0.72f),
                onRatingSelected = onTrackRatingSelected,
                modifier = Modifier.width(82.dp),
            )
        }

        Text(
            listOfNotNull(audioInfo?.codec, audioInfo?.quality).joinToString("  "),
            color = appColors.secondaryText,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(22.dp),
            ) {
                Icon(
                    imageVector = TransportIcons.Volume,
                    contentDescription = "Volume",
                    tint = appColors.secondaryText,
                    modifier = Modifier.size(17.dp),
                )
            }
            VolumeLineControl(
                value = volumeValue,
                enabled = supportsSoftwareVolume,
                onValueChange = {
                    isChangingVolume = true
                    volumeValue = it
                    onVolumeChanged((it * 100).toInt().coerceIn(0, 100))
                },
                onValueChangeFinished = {
                    isChangingVolume = false
                },
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        ) {
            TransportIconButton(
                enabled = hasPrevious,
                icon = TransportIcons.Previous,
                contentDescription = "Previous",
                appColors = appColors,
                onClick = onPrevious,
            )
            TransportIconButton(
                enabled = canTogglePause,
                icon = if (playbackState == PlaybackState.Playing) {
                    TransportIcons.Pause
                } else {
                    TransportIcons.Play
                },
                contentDescription = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
                appColors = appColors,
                prominent = true,
                onClick = {
                    if (playbackState == PlaybackState.Playing) {
                        onPause()
                    } else if (playbackState == PlaybackState.Paused) {
                        onResume()
                    } else {
                        onPlayCurrent()
                    }
                },
            )
            TransportIconButton(
                enabled = hasNext,
                icon = TransportIcons.Next,
                contentDescription = "Next",
                appColors = appColors,
                onClick = onNext,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        ) {
            TransportIconButton(
                enabled = nowPlayingTrack != null,
                icon = TransportIcons.Radio,
                contentDescription = "Start track radio",
                appColors = appColors,
                onClick = {
                    nowPlayingTrack?.let(onTrackRadioSelected)
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
            IconButton(
                onClick = onCollapseToHome,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
            ) {
                Icon(
                    imageVector = NavigationIcons.ChevronDown,
                    contentDescription = "Back",
                    tint = appColors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                TransportIconButton(
                    enabled = nowPlayingTrack != null,
                    icon = TransportIcons.Menu,
                    contentDescription = "Track actions",
                    appColors = appColors,
                    onClick = { actionMenuExpanded = true },
                )
                NaviampDropdownMenu(
                    expanded = actionMenuExpanded,
                    onDismissRequest = { actionMenuExpanded = false },
                ) {
                    NaviampDropdownMenuItem(
                        label = "Start track radio",
                        onClick = {
                            actionMenuExpanded = false
                            nowPlayingTrack?.let(onTrackRadioSelected)
                        },
                    )
                    NaviampDropdownMenuItem(
                        label = "Go to artist",
                        enabled = nowPlayingTrack?.artistId != null,
                        onClick = {
                            actionMenuExpanded = false
                            nowPlayingTrack?.let(onArtistSelected)
                        },
                    )
                    NaviampDropdownMenuItem(
                        label = "Go to album",
                        enabled = nowPlayingTrack?.albumId != null,
                        onClick = {
                            actionMenuExpanded = false
                            nowPlayingTrack?.let(onAlbumSelected)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun playerSliderColors(
    playerColors: PlayerColors,
    appColors: AppColors,
) = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = playerColors.accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
    disabledThumbColor = appColors.mutedText,
    disabledActiveTrackColor = Color.White.copy(alpha = 0.18f),
    disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
)

@Composable
private fun VolumeLineControl(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun updateFromX(x: Float) {
        if (!enabled) return
        onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, widthPx) {
                detectTapGestures { offset ->
                    updateFromX(offset.x)
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled, widthPx) {
                detectDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x) },
                    onDrag = { change, _ -> updateFromX(change.position.x) },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished,
                )
            },
    ) {
        val centerY = size.height / 2f
        val endX = size.width * value.coerceIn(0f, 1f)
        val disabledAlpha = if (enabled) 1f else 0.42f

        drawLine(
            color = Color.White.copy(alpha = 0.24f * disabledAlpha),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.88f * disabledAlpha),
            start = Offset(0f, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WaveformScrubber(
    waveform: AudioWaveform,
    value: Float,
    enabled: Boolean,
    playerColors: PlayerColors,
    appColors: AppColors,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun updateFromX(x: Float) {
        if (!enabled) return
        onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, widthPx) {
                detectTapGestures { offset ->
                    updateFromX(offset.x)
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled, widthPx) {
                detectDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x) },
                    onDrag = { change, _ -> updateFromX(change.position.x) },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished,
                )
            },
    ) {
        val amplitudes = waveform.amplitudes
        if (amplitudes.isEmpty()) return@Canvas

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
            val x = index * step + step / 2f
            val color = when {
                !enabled -> appColors.mutedText.copy(alpha = 0.28f)
                ratio <= value -> waveformActiveColor(playerColors.backgroundMid)
                else -> waveformInactiveColor(playerColors.backgroundMid)
            }

            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun waveformActiveColor(background: Color): Color =
    if (background.luminance() < 0.42f) {
        Color.White.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.72f)
    }

private fun waveformInactiveColor(background: Color): Color =
    if (background.luminance() < 0.42f) {
        Color.White.copy(alpha = 0.30f)
    } else {
        Color.Black.copy(alpha = 0.24f)
    }

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun TransportIconButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    appColors: AppColors,
    prominent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(if (prominent) 44.dp else 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (prominent) {
                    Color.White.copy(alpha = if (enabled) 0.18f else 0.08f)
                } else {
                    Color.Transparent
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
                tint = if (enabled) appColors.primaryText else appColors.mutedText.copy(alpha = 0.55f),
                modifier = Modifier.size(if (prominent) 24.dp else 20.dp),
            )
        }
    }
}

@Composable
private fun TrackRatingControl(
    track: Track?,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onRatingSelected: (Track, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        (1..5).forEach { rating ->
            val selected = (track?.userRating ?: 0) >= rating
            Text(
                if (selected) "★" else "☆",
                color = if (selected) activeColor else inactiveColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(16.dp)
                    .clickable(enabled = track != null && enabled) {
                        track?.let {
                            onRatingSelected(it, if (it.userRating == rating) null else rating)
                        }
                    },
            )
        }
    }
}

@Composable
private fun UpNextPanel(
    appColors: AppColors,
    upNext: List<Track>,
    firstQueueIndex: Int,
    coverArtUrl: (Track) -> String?,
    relatedTracks: List<Track>,
    relatedCoverArtUrl: (Track) -> String?,
    onQueueIndexSelected: (Int) -> Unit,
    onRelatedTrackSelected: (Int) -> Unit,
    onRelatedTrackRadioSelected: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(PlayerListTab.UpNext) }
    val visibleTracks = when (selectedTab) {
        PlayerListTab.UpNext -> upNext
        PlayerListTab.Related -> relatedTracks
    }
    val activeCoverArtUrl = when (selectedTab) {
        PlayerListTab.UpNext -> coverArtUrl
        PlayerListTab.Related -> relatedCoverArtUrl
    }
    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("BACK TO", color = appColors.mutedText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            PlayerListTabLabel(
                label = "UP NEXT",
                selected = selectedTab == PlayerListTab.UpNext,
                appColors = appColors,
                onClick = { selectedTab = PlayerListTab.UpNext },
            )
            PlayerListTabLabel(
                label = "RELATED",
                selected = selectedTab == PlayerListTab.Related,
                appColors = appColors,
                onClick = { selectedTab = PlayerListTab.Related },
            )
        }
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.18f)),
        )
        if (visibleTracks.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
            ) {
                Text(
                    text = if (selectedTab == PlayerListTab.Related) "No related tracks" else "Queue is empty",
                    color = appColors.mutedText,
                    fontSize = 11.sp,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                visibleTracks.forEachIndexed { index, track ->
                    val trackCoverArtState = rememberCoverArtState(activeCoverArtUrl(track), appColors)
                    TrackRow(
                        appColors = appColors,
                        track = track,
                        subtitle = track.artistName,
                        background = false,
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp,
                        showDuration = false,
                        showMenu = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        onClick = {
                            when (selectedTab) {
                                PlayerListTab.UpNext -> onQueueIndexSelected(firstQueueIndex + index)
                                PlayerListTab.Related -> onRelatedTrackSelected(index)
                            }
                        },
                        onStartRadio = if (selectedTab == PlayerListTab.Related) {
                            { onRelatedTrackRadioSelected(track) }
                        } else {
                            null
                        },
                        titleStyle = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                        ),
                        subtitleStyle = TextStyle(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        ),
                        leadingContent = {
                            CoverArt(
                                coverArtState = trackCoverArtState,
                                appColors = appColors,
                                size = 32.dp,
                                elevated = false,
                            )
                        },
                    )
                }
            }
        }
    }
}

private enum class PlayerListTab {
    UpNext,
    Related,
}

@Composable
private fun PlayerListTabLabel(
    label: String,
    selected: Boolean,
    appColors: AppColors,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) appColors.primaryText else appColors.mutedText,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CoverArt(
    coverArtState: CoverArtState,
    appColors: AppColors,
    size: Dp,
    elevated: Boolean = true,
) {
    val shadowMargin = if (elevated) 12.dp else 0.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + shadowMargin * 2),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (elevated) {
                        Modifier
                            .shadow(24.dp, RoundedCornerShape(8.dp), clip = false)
                            .shadow(7.dp, RoundedCornerShape(7.dp), clip = false)
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(if (elevated) 7.dp else 4.dp))
                .background(appColors.albumArtPlaceholder),
        ) {
            coverArtState.image?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
private fun rememberCoverArtState(
    coverArtUrl: String?,
    appColors: AppColors,
): CoverArtState {
    var coverArtState by remember {
        mutableStateOf(CoverArtState(image = null, palette = AlbumPalette.fallback(appColors.albumArtPlaceholder)))
    }

    LaunchedEffect(coverArtUrl) {
        if (coverArtUrl == null) {
            coverArtState = CoverArtState(image = null, palette = AlbumPalette.fallback(appColors.albumArtPlaceholder))
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = DesktopCaches.session.imageBytes(coverArtUrl)
                CoverArtState(
                    image = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(),
                    palette = albumPalette(bytes) ?: AlbumPalette.fallback(appColors.albumArtPlaceholder),
                )
            }
        }.onSuccess {
            coverArtState = it
        }
    }

    return coverArtState
}

private data class CoverArtState(
    val image: ImageBitmap?,
    val palette: AlbumPalette,
)

data class PlayerColors(
    val backgroundStart: Color,
    val backgroundMid: Color,
    val backgroundEnd: Color,
    val accent: Color,
) {
    val backgroundBrush: Brush
        get() = Brush.linearGradient(colors = listOf(backgroundStart, backgroundMid, backgroundEnd))

    fun backgroundBrush(end: androidx.compose.ui.geometry.Offset): Brush =
        Brush.linearGradient(
            colors = listOf(backgroundStart, backgroundMid, backgroundEnd),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = end,
        )

    companion object {
        fun solid(color: Color): PlayerColors =
            PlayerColors(
                backgroundStart = color,
                backgroundMid = color,
                backgroundEnd = color,
                accent = color,
            )

        fun from(palette: AlbumPalette, appColors: AppColors): PlayerColors {
            val secondary = if (palette.primary.hueDistance(palette.secondary) < 0.08f) {
                palette.secondary.shiftHue(0.09f)
            } else {
                palette.secondary
            }
            val left = palette.primary
                .mix(Color.White, 0.03f)
                .mix(Color.Black, 0.34f)
                .mix(appColors.background, 0.16f)
            val middle = palette.accent
                .mix(palette.primary, 0.28f)
                .mix(Color.Black, 0.42f)
                .mix(appColors.background, 0.10f)
            val right = secondary
                .mix(Color.Black, 0.66f)
                .mix(appColors.background, 0.12f)
            return PlayerColors(
                backgroundStart = left,
                backgroundMid = middle,
                backgroundEnd = right,
                accent = palette.accent.mix(Color.White, 0.08f),
            )
        }
    }
}

data class AlbumPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
) {
    companion object {
        fun fallback(color: Color): AlbumPalette =
            AlbumPalette(
                primary = color,
                secondary = color.mix(Color(0xFF7C1232), 0.45f),
                accent = color,
            )
    }
}

private fun albumPalette(bytes: ByteArray): AlbumPalette? {
    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    val buckets = mutableMapOf<Int, ColorBucket>()
    val stepX = (image.width / 32).coerceAtLeast(1)
    val stepY = (image.height / 32).coerceAtLeast(1)

    var y = 0
    while (y < image.height) {
        var x = 0
        while (x < image.width) {
            val pixel = image.getRGB(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > 200) {
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val hsb = FloatArray(3)
                java.awt.Color.RGBtoHSB(red, green, blue, hsb)
                val saturation = hsb[1]
                val brightness = hsb[2]
                if (saturation > 0.06f && brightness in 0.12f..0.96f) {
                    val key = ((red / 32) shl 10) or ((green / 32) shl 5) or (blue / 32)
                    buckets.getOrPut(key) { ColorBucket() }.add(red, green, blue, saturation, brightness)
                }
            }
            x += stepX
        }
        y += stepY
    }

    val candidates = buckets.values
        .filter { it.count >= 2 }
        .sortedByDescending { it.score() }

    val primary = candidates.firstOrNull() ?: return null
    val secondary = candidates.firstOrNull { primary.colorDistance(it) > 0.045f }
        ?: candidates.firstOrNull { primary.hueDistance(it) > 0.08f }
        ?: candidates.getOrNull(1)
        ?: primary
    val accent = candidates
        .filter { primary.colorDistance(it) > 0.025f || primary.hueDistance(it) > 0.06f }
        .maxByOrNull { it.accentScore(primary) }
        ?: primary

    return AlbumPalette(
        primary = primary.color(),
        secondary = secondary.color(),
        accent = accent.color(),
    )
}

private class ColorBucket {
    var red = 0L
    var green = 0L
    var blue = 0L
    var saturation = 0.0
    var brightness = 0.0
    var count = 0

    fun add(red: Int, green: Int, blue: Int, saturation: Float, brightness: Float) {
        this.red += red
        this.green += green
        this.blue += blue
        this.saturation += saturation
        this.brightness += brightness
        count += 1
    }

    fun color(): Color =
        Color(
            red = (red / count).toInt(),
            green = (green / count).toInt(),
            blue = (blue / count).toInt(),
        )

    fun score(): Double {
        val saturationAverage = saturationAverage().toDouble()
        val brightnessAverage = brightnessAverage().toDouble()
        val brightnessScore = 1.0 - abs(brightnessAverage - 0.58).coerceAtMost(0.58) / 0.58
        return count.toDouble().pow(0.55) * (saturationAverage + 0.12) * (brightnessScore + 0.55)
    }

    fun accentScore(primary: ColorBucket): Double =
        score() * (1.0 + saturationAverage()) * (1.0 + colorDistance(primary))

    fun saturationAverage(): Float =
        (saturation / count).toFloat()

    private fun brightnessAverage(): Float =
        (brightness / count).toFloat()

    fun hueDistance(other: ColorBucket): Float {
        val hsb = FloatArray(3)
        val otherHsb = FloatArray(3)
        val color = color()
        val otherColor = other.color()
        java.awt.Color.RGBtoHSB(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            hsb,
        )
        java.awt.Color.RGBtoHSB(
            (otherColor.red * 255).toInt(),
            (otherColor.green * 255).toInt(),
            (otherColor.blue * 255).toInt(),
            otherHsb,
        )
        val distance = abs(hsb[0] - otherHsb[0])
        return minOf(distance, 1f - distance)
    }

    fun colorDistance(other: ColorBucket): Float =
        color().colorDistance(other.color())
}

fun Color.mix(other: Color, amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * clamped,
        green = green + (other.green - green) * clamped,
        blue = blue + (other.blue - blue) * clamped,
        alpha = alpha + (other.alpha - alpha) * clamped,
    )
}

private fun Color.shiftHue(amount: Float): Color {
    val hsb = FloatArray(3)
    java.awt.Color.RGBtoHSB(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsb,
    )
    val hue = ((hsb[0] + amount) % 1f + 1f) % 1f
    val rgb = java.awt.Color.HSBtoRGB(
        hue,
        (hsb[1] * 1.08f).coerceIn(0f, 1f),
        (hsb[2] * 0.9f).coerceIn(0f, 1f),
    )
    return Color(
        red = (rgb shr 16) and 0xFF,
        green = (rgb shr 8) and 0xFF,
        blue = rgb and 0xFF,
        alpha = (alpha * 255).toInt(),
    )
}

private fun Color.hueDistance(other: Color): Float {
    val hsb = FloatArray(3)
    val otherHsb = FloatArray(3)
    java.awt.Color.RGBtoHSB(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsb,
    )
    java.awt.Color.RGBtoHSB(
        (other.red * 255).toInt(),
        (other.green * 255).toInt(),
        (other.blue * 255).toInt(),
        otherHsb,
    )
    val distance = abs(hsb[0] - otherHsb[0])
    return minOf(distance, 1f - distance)
}

private fun Color.colorDistance(other: Color): Float {
    val redDistance = red - other.red
    val greenDistance = green - other.green
    val blueDistance = blue - other.blue
    return redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance
}
