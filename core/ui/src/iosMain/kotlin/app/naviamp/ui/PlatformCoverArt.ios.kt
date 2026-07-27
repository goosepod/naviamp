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
): List<Color> = remember(colors) {
    NaviampPlayerColors.fallback(colors).gradientColors
}

@Composable
actual fun rememberPlatformCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors = remember(colors) {
    NaviampPlayerColors.fallback(colors)
}

private object IosCoverArtCache {
    private const val MaxImages = 96
    private val images = mutableMapOf<String, ImageBitmap>()

    fun cached(url: String): ImageBitmap? = images[url]

    suspend fun image(url: String): ImageBitmap? = cached(url) ?: runCatching {
        withContext(Dispatchers.Default) {
            val bytes = iosPlatformCoverArtByteLoader?.invoke(url)
            bytes?.takeIf { it.isNotEmpty() }
                ?.let(SkiaImage::makeFromEncoded)
                ?.toComposeImageBitmap()
        }
    }.getOrNull()?.also { decoded ->
        if (images.size >= MaxImages) images.keys.firstOrNull()?.let(images::remove)
        images[url] = decoded
    }

    fun clear() = images.clear()
}
