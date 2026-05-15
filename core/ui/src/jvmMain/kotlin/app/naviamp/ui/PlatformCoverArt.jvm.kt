package app.naviamp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url == null) {
            image = null
            return@LaunchedEffect
        }
        image = runCatching {
            withContext(Dispatchers.IO) {
                URI(url).toURL().openStream().use { input ->
                    SkiaImage.makeFromEncoded(input.readBytes()).toComposeImageBitmap()
                }
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.albumArtPlaceholder),
    ) {
        Crossfade(
            targetState = image,
            animationSpec = tween(durationMillis = 180),
            label = "Cover art fade",
        ) { targetImage ->
            targetImage?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
actual fun rememberPlatformCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color> =
    rememberPlatformCoverArtPlayerColors(url, colors).gradientColors

@Composable
actual fun rememberPlatformCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors {
    var playerColors by remember { mutableStateOf(NaviampPlayerColors.fallback(colors)) }

    LaunchedEffect(url) {
        playerColors = if (url == null) {
            NaviampPlayerColors.fallback(colors)
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    URI(url).toURL().openStream().use { input ->
                        naviampAlbumPalette(jvmRgbSamples(input.readBytes()))
                            ?.let { NaviampPlayerColors.from(it, colors) }
                    }
                }
            }.getOrNull() ?: NaviampPlayerColors.fallback(colors)
        }
    }

    return playerColors
}

private fun jvmRgbSamples(bytes: ByteArray): List<NaviampRgbSample> {
    val image = javax.imageio.ImageIO.read(bytes.inputStream()) ?: return emptyList()
    val samples = mutableListOf<NaviampRgbSample>()
    val stepX = (image.width / 32).coerceAtLeast(1)
    val stepY = (image.height / 32).coerceAtLeast(1)
    var y = 0
    while (y < image.height) {
        var x = 0
        while (x < image.width) {
            val pixel = image.getRGB(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > 200) {
                samples += NaviampRgbSample(
                    red = (pixel shr 16) and 0xFF,
                    green = (pixel shr 8) and 0xFF,
                    blue = pixel and 0xFF,
                )
            }
            x += stepX
        }
        y += stepY
    }
    return samples
}
