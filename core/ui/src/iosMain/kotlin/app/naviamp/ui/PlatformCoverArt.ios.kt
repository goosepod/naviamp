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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.min

private var iosPlatformCoverArtByteLoader: (suspend (String) -> ByteArray?)? = null

fun setIosPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) {
    iosPlatformCoverArtByteLoader = loader
}

fun resetIosPlatformCoverArtByteLoader() {
    iosPlatformCoverArtByteLoader = null
    IosCoverArtCache.clear()
}

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    var image by remember(url) { mutableStateOf(url?.let(IosCoverArtCache::cached)) }
    LaunchedEffect(url) {
        image = url?.let { IosCoverArtCache.image(it) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.albumArtPlaceholder),
    ) {
        Crossfade(image, animationSpec = tween(180), label = "Cover art fade") { target ->
            target?.let {
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
actual fun PlatformExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    var image by remember(url) { mutableStateOf(url?.let(IosCoverArtCache::cached)) }
    LaunchedEffect(url) {
        image = url?.let { IosCoverArtCache.image(it) }
    }
    val imageWidth = image?.width?.takeIf { it > 0 } ?: 1
    val imageHeight = image?.height?.takeIf { it > 0 } ?: 1
    val scale = min(maxWidth.value / imageWidth, maxHeight.value / imageHeight)
    Box(
        modifier = Modifier
            .size((imageWidth * scale).dp, (imageHeight * scale).dp)
            .background(colors.albumArtPlaceholder),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Enlarged image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
actual fun rememberPlatformCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color> = rememberPlatformCoverArtPlayerColors(url, colors).gradientColors

@Composable
actual fun rememberPlatformCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors {
    var playerColors by remember(url, colors) {
        mutableStateOf(
            url?.let { IosCoverArtCache.cachedPlayerColors(it, colors) }
                ?: NaviampPlayerColors.fallback(colors),
        )
    }
    LaunchedEffect(url, colors) {
        if (url == null) return@LaunchedEffect
        IosCoverArtCache.playerColors(url, colors)?.let { loadedColors ->
            playerColors = loadedColors
        }
    }
    return playerColors
}

private object IosCoverArtCache {
    private const val MaxImages = 96
    private val images = mutableMapOf<String, ImageBitmap>()
    private val palettes = mutableMapOf<String, List<NaviampRgbSample>>()

    fun cached(url: String): ImageBitmap? = images[url]

    fun cachedPlayerColors(url: String, colors: NaviampColors): NaviampPlayerColors? =
        palettes[url]
            ?.let(::naviampAlbumPalette)
            ?.let { NaviampPlayerColors.from(it, colors) }

    suspend fun image(url: String): ImageBitmap? = cached(url) ?: runCatching {
        withContext(Dispatchers.Default) {
            val bytes = iosPlatformCoverArtByteLoader?.invoke(url)
            bytes?.takeIf { it.isNotEmpty() }
                ?.let { encoded ->
                    val decoded = SkiaImage.makeFromEncoded(encoded)
                    palettes[url] = decoded.rgbSamples()
                    decoded.toComposeImageBitmap()
                }
        }
    }.getOrNull()?.also { decoded ->
        if (images.size >= MaxImages) images.keys.firstOrNull()?.let(images::remove)
        images[url] = decoded
    }

    suspend fun playerColors(url: String, colors: NaviampColors): NaviampPlayerColors? {
        cachedPlayerColors(url, colors)?.let { return it }
        image(url) ?: return null
        return cachedPlayerColors(url, colors)
    }

    fun clear() {
        images.clear()
        palettes.clear()
    }
}

private fun SkiaImage.rgbSamples(): List<NaviampRgbSample> {
    val bitmap = SkiaBitmap.makeFromImage(this)
    return try {
        val samples = mutableListOf<NaviampRgbSample>()
        val stepX = (width / 32).coerceAtLeast(1)
        val stepY = (height / 32).coerceAtLeast(1)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getColor(x, y)
                if ((pixel ushr 24) and 0xFF > 200) {
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
        samples
    } finally {
        bitmap.close()
    }
}
