package app.naviamp.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI

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
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var scrubberValue by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val effectiveProgressFraction = playbackProgress.fraction(effectiveDurationSeconds)
    val canSeek = supportsSeek && effectiveDurationSeconds != null && nowPlayingTrack != null
    val canTogglePause = supportsPause &&
        (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Paused)

    LaunchedEffect(effectiveProgressFraction) {
        if (!isScrubbing) {
            scrubberValue = effectiveProgressFraction.toFloat()
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(
                coverArtUrl = coverArtUrl,
                appColors = appColors,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    nowPlayingTrack?.title ?: "Nothing Playing",
                    color = appColors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(nowPlayingTrack?.artistName ?: "Queue will appear here after connection", color = appColors.secondaryText)
                Spacer(modifier = Modifier.height(8.dp))
                nowPlayingTrack?.playbackAudioLabel(playbackEngineName)?.let {
                    Text(it, color = appColors.mutedText)
                }
                Text("${playbackState.label()} via $playbackEngineName", color = appColors.mutedText)
                Text(playbackCapabilityLabel(supportsGapless, supportsCrossfade), color = appColors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            modifier = Modifier.fillMaxWidth(),
        )

        Text(playbackProgress.label(effectiveDurationSeconds), color = appColors.mutedText)

        if (nowPlayingTrack != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    enabled = hasPrevious,
                    onClick = onPrevious,
                ) {
                    Icon(
                        imageVector = TransportIcons.Previous,
                        contentDescription = "Previous",
                        tint = appColors.primaryText,
                    )
                }

                IconButton(
                    enabled = canTogglePause,
                    onClick = {
                        if (playbackState == PlaybackState.Playing) {
                            onPause()
                        } else {
                            onResume()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (playbackState == PlaybackState.Playing) {
                            TransportIcons.Pause
                        } else {
                            TransportIcons.Play
                        },
                        contentDescription = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
                        tint = appColors.primaryText,
                    )
                }

                IconButton(
                    enabled = hasNext,
                    onClick = onNext,
                ) {
                    Icon(
                        imageVector = TransportIcons.Next,
                        contentDescription = "Next",
                        tint = appColors.primaryText,
                    )
                }
            }
        }

        if (upNext.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Up Next", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            upNext.take(5).forEachIndexed { index, track ->
                Text(
                    "${index + 1}. ${track.title} - ${track.artistName}",
                    color = appColors.secondaryText,
                )
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
    coverArtUrl: String?,
    appColors: AppColors,
) {
    var image by remember(coverArtUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(coverArtUrl) {
        image = if (coverArtUrl == null) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = URI.create(coverArtUrl).toURL().openStream().use { it.readBytes() }
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .background(appColors.albumArtPlaceholder),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
