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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

internal data class NaviampDecodedCoverArt(
    val image: ImageBitmap,
    val rgbSamples: List<NaviampRgbSample>,
)

/** Loads encoded artwork through the host/provider boundary. */
internal expect suspend fun platformCoverArtBytes(url: String): ByteArray?

/** Decodes bytes through the target graphics ABI while returning platform-neutral color samples. */
internal expect fun decodePlatformCoverArt(
    bytes: ByteArray,
    targetSidePx: Int,
): NaviampDecodedCoverArt?

@Composable
fun NaviampCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    val targetSidePx = with(LocalDensity.current) {
        ceil(size.toPx()).toInt().coerceIn(MinCoverArtSidePx, MaxCoverArtSidePx)
    }
    var image by remember(url, targetSidePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, targetSidePx) {
        image = url?.let { NaviampCoverArtCache.image(it, targetSidePx) }
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
fun NaviampExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val targetSidePx = with(LocalDensity.current) {
        ceil(maxOf(maxWidth.toPx(), maxHeight.toPx())).toInt()
            .coerceIn(MinCoverArtSidePx, MaxCoverArtSidePx)
    }
    var image by remember(url, targetSidePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, targetSidePx) {
        image = url?.let { NaviampCoverArtCache.image(it, targetSidePx) }
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
fun rememberNaviampCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color> = rememberNaviampCoverArtPlayerColors(url, colors).gradientColors

@Composable
fun rememberNaviampCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors {
    var playerColors by remember(url, colors) {
        mutableStateOf(NaviampPlayerColors.fallback(colors))
    }
    LaunchedEffect(url, colors) {
        playerColors = if (url == null) {
            NaviampPlayerColors.fallback(colors)
        } else {
            NaviampCoverArtCache.playerColors(url, colors)
        }
    }
    return playerColors
}

internal suspend fun preloadNaviampCoverArt(urls: Iterable<String>) {
    urls.distinct().forEach { url -> NaviampCoverArtCache.image(url, MaxCoverArtSidePx) }
}

internal suspend fun naviampCoverArtPlayerColors(
    url: String,
    colors: NaviampColors = NaviampColors.Dark,
): NaviampPlayerColors = NaviampCoverArtCache.playerColors(url, colors)

internal fun resetNaviampCoverArtCache() = NaviampCoverArtCache.clear()

private object NaviampCoverArtCache {
    private const val MaxImages = 240
    private const val MaxPalettes = 240
    private val images = linkedMapOf<String, ImageBitmap>()
    private val palettes = linkedMapOf<String, List<NaviampRgbSample>>()
    private val mutex = Mutex()

    private suspend fun cachedPlayerColors(url: String, colors: NaviampColors): NaviampPlayerColors =
        mutex.withLock { palettes[url] }
            ?.let(::naviampAlbumPalette)
            ?.let { NaviampPlayerColors.from(it, colors) }
            ?: NaviampPlayerColors.fallback(colors)

    suspend fun image(url: String, targetSidePx: Int): ImageBitmap? {
        val cacheKey = "$url#$targetSidePx"
        mutex.withLock { images[cacheKey] }?.let { return it }
        val decoded = withContext(Dispatchers.Default) {
            platformCoverArtBytes(url)
                ?.takeIf { it.isNotEmpty() }
                ?.let { decodePlatformCoverArt(it, targetSidePx) }
        } ?: return null
        mutex.withLock {
            putBounded(images, cacheKey, decoded.image, MaxImages)
            putBounded(palettes, url, decoded.rgbSamples, MaxPalettes)
        }
        return decoded.image
    }

    suspend fun playerColors(url: String, colors: NaviampColors): NaviampPlayerColors {
        if (mutex.withLock { palettes[url] } == null) image(url, PaletteCoverArtSidePx)
        return cachedPlayerColors(url, colors)
    }

    fun clear() {
        images.clear()
        palettes.clear()
    }

    private fun <T> putBounded(map: MutableMap<String, T>, key: String, value: T, maximum: Int) {
        map.remove(key)
        map[key] = value
        while (map.size > maximum) map.remove(map.keys.first())
    }
}

private const val MinCoverArtSidePx = 128
private const val MaxCoverArtSidePx = 1024
private const val PaletteCoverArtSidePx = 128
