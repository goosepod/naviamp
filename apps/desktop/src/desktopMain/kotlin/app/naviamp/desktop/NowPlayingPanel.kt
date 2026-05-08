package app.naviamp.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun NowPlayingPanel(
    appColors: AppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    nowPlayingTrack: Track?,
    coverArtUrl: String?,
    upNext: List<Track>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    onPlayerColorsChanged: (PlayerColors) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
            .padding(12.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp
        val artSize = when {
            wideLayout -> 250.dp
            maxWidth < 380.dp -> 178.dp
            else -> 215.dp
        }

        if (wideLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
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
                    supportsGapless = supportsGapless,
                    supportsCrossfade = supportsCrossfade,
                    nowPlayingTrack = nowPlayingTrack,
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    supportsPause = supportsPause,
                    supportsSeek = supportsSeek,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPause = onPause,
                    onResume = onResume,
                    onPlayCurrent = onPlayCurrent,
                    onSeek = onSeek,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    modifier = Modifier.weight(0.9f),
                )
                UpNextPanel(
                    appColors = appColors,
                    upNext = upNext,
                    coverArtState = coverArtState,
                    maxItems = 8,
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 300.dp),
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    supportsGapless = supportsGapless,
                    supportsCrossfade = supportsCrossfade,
                    nowPlayingTrack = nowPlayingTrack,
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    supportsPause = supportsPause,
                    supportsSeek = supportsSeek,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPause = onPause,
                    onResume = onResume,
                    onPlayCurrent = onPlayCurrent,
                    onSeek = onSeek,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
                UpNextPanel(
                    appColors = appColors,
                    upNext = upNext,
                    coverArtState = coverArtState,
                    maxItems = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 230.dp),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        CoverArt(
            coverArtState = coverArtState,
            appColors = appColors,
            size = 46.dp,
            elevated = false,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                nowPlayingTrack?.artistName ?: "Nothing Playing",
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
            Text(
                nowPlayingTrack?.title ?: "Queue is empty",
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
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
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    nowPlayingTrack: Track?,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubberValue by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val effectiveProgressFraction = playbackProgress.fraction(effectiveDurationSeconds)
    val canSeek = supportsSeek && effectiveDurationSeconds != null && nowPlayingTrack != null
    val canTogglePause = nowPlayingTrack != null &&
        playbackState != PlaybackState.Loading &&
        playbackState !is PlaybackState.Error &&
        (supportsPause || playbackState != PlaybackState.Playing)

    LaunchedEffect(effectiveProgressFraction) {
        if (!isScrubbing) {
            scrubberValue = effectiveProgressFraction.toFloat()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier,
    ) {
        Text(
            nowPlayingTrack?.artistName ?: "Nothing Playing",
            color = appColors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )
        Text(
            nowPlayingTrack?.title ?: "Queue will appear here after connection",
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
        Text(
            nowPlayingTrack?.albumTitle ?: playbackState.label(),
            color = appColors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            nowPlayingTrack?.playbackAudioLabel(playbackEngineName)?.let {
                Text(it, color = appColors.mutedText)
            }
            Text("☆☆☆☆☆", color = appColors.mutedText, fontSize = 13.sp)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
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
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = playerColors.accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    disabledThumbColor = appColors.mutedText,
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.18f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
            )
            Text(
                playbackProgress.label(effectiveDurationSeconds),
                color = appColors.secondaryText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
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

        Text(
            playbackCapabilityLabel(supportsGapless, supportsCrossfade),
            color = appColors.mutedText,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TransportIconButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    appColors: AppColors,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (prominent) 48.dp else 38.dp)
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
                modifier = Modifier.size(if (prominent) 26.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun UpNextPanel(
    appColors: AppColors,
    upNext: List<Track>,
    coverArtState: CoverArtState,
    maxItems: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("BACK TO", color = appColors.mutedText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("UP NEXT", color = appColors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("RELATED", color = appColors.mutedText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.18f)),
        )
        repeat(maxItems) { index ->
            val track = upNext.getOrNull(index)
            if (track == null) {
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    CoverArt(
                        coverArtState = coverArtState,
                        appColors = appColors,
                        size = 40.dp,
                        elevated = false,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = appColors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                        )
                        Text(
                            track.artistName,
                            color = appColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                    }
                    Text("⋮", color = appColors.mutedText)
                }
            }
        }
    }
}

private fun playbackCapabilityLabel(
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
): String {
    val gapless = if (supportsGapless) "Gapless" else "No gapless"
    val crossfade = if (supportsCrossfade) "Crossfade" else "No crossfade"
    return "$gapless • $crossfade"
}

@Composable
private fun CoverArt(
    coverArtState: CoverArtState,
    appColors: AppColors,
    size: Dp,
    elevated: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (elevated) 7.dp else 4.dp))
            .background(appColors.albumArtPlaceholder)
            .border(
                width = if (elevated) 3.dp else 0.dp,
                color = Color.White.copy(alpha = if (elevated) 0.65f else 0f),
                shape = RoundedCornerShape(if (elevated) 7.dp else 4.dp),
            ),
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
                val bytes = URI.create(coverArtUrl).toURL().openStream().use { it.readBytes() }
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

    companion object {
        fun from(palette: AlbumPalette, appColors: AppColors): PlayerColors {
            val secondary = if (palette.primary.hueDistance(palette.secondary) < 0.08f) {
                palette.secondary.shiftHue(0.09f)
            } else {
                palette.secondary
            }
            val left = palette.primary
                .mix(Color.White, 0.05f)
                .mix(Color.Black, 0.24f)
                .mix(appColors.background, 0.04f)
            val middle = palette.accent
                .mix(palette.primary, 0.22f)
                .mix(Color.Black, 0.32f)
            val right = secondary
                .mix(Color.Black, 0.58f)
                .mix(appColors.background, 0.05f)
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
    return (redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance).toFloat()
}
