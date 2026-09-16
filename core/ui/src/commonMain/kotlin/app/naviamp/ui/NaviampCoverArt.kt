package app.naviamp.ui

import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    modifier: Modifier = Modifier,
    decodeSize: Dp = size,
    fallbackUrl: String? = null,
) {
    val targetSidePx = with(LocalDensity.current) {
        ceil(decodeSize.toPx()).toInt().coerceIn(MinCoverArtSidePx, MaxCoverArtSidePx)
    }
    // Keep the displayed bitmap while a new URL or decode size is loading. Resetting this
    // state by size exposes the placeholder again after the incoming cover has faded in.
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var outgoingImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val incomingAlpha = remember { Animatable(1f) }
    LaunchedEffect(url, fallbackUrl, targetSidePx) {
        if (url == null && fallbackUrl == null) {
            // Playback can briefly publish no artwork between adjacent queue items. Do not let that
            // transient state expose the placeholder in the middle of a song transition.
            delay(CurrentMediaEmptyGraceMillis)
            image = null
            outgoingImage = null
            incomingAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        val loadedImage = listOfNotNull(url, fallbackUrl)
            .distinct()
            .firstNotNullOfOrNull { candidate -> NaviampCoverArtCache.image(candidate, targetSidePx) }
        if (loadedImage == null) {
            image = null
            outgoingImage = null
            incomingAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        if (loadedImage == image) return@LaunchedEffect
        outgoingImage = image
        image = loadedImage
        if (outgoingImage == null) {
            incomingAlpha.snapTo(1f)
        } else {
            incomingAlpha.snapTo(0f)
            incomingAlpha.animateTo(1f, tween(CoverArtTransitionMillis))
            outgoingImage = null
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.albumArtPlaceholder),
    ) {
        outgoingImage?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                alpha = incomingAlpha.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun NaviampExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
    fallbackUrl: String? = null,
) {
    val targetSidePx = with(LocalDensity.current) {
        ceil(maxOf(maxWidth.toPx(), maxHeight.toPx())).toInt()
            .coerceIn(MinCoverArtSidePx, MaxCoverArtSidePx)
    }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, fallbackUrl, targetSidePx) {
        image = listOfNotNull(url, fallbackUrl)
            .distinct()
            .firstNotNullOfOrNull { candidate -> NaviampCoverArtCache.image(candidate, targetSidePx) }
    }
    val imageWidth = image?.width?.takeIf { it > 0 } ?: 1
    val imageHeight = image?.height?.takeIf { it > 0 } ?: 1
    val scale = min(maxWidth.value / imageWidth, maxHeight.value / imageHeight)
    val placeholder = if (image == null) {
        Modifier.background(colors.albumArtPlaceholder)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size((imageWidth * scale).dp, (imageHeight * scale).dp)
            .then(placeholder),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Enlarged image",
                // The parent already has the bitmap's exact aspect ratio. FillBounds avoids
                // density/rounding differences exposing the placeholder as letterbox bars.
                contentScale = ContentScale.FillBounds,
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
    var playerColors by remember(colors) {
        mutableStateOf(NaviampPlayerColors.fallback(colors))
    }
    LaunchedEffect(url, colors) {
        playerColors = if (url == null) {
            delay(CurrentMediaEmptyGraceMillis)
            NaviampPlayerColors.fallback(colors)
        } else {
            NaviampCoverArtCache.playerColors(url, colors)
        }
    }
    return playerColors
}

@Composable
internal fun PreloadNaviampNowPlayingArtwork(nowPlaying: NowPlayingUi?) {
    val upcomingUrls = remember(nowPlaying?.id, nowPlaying?.upNext) {
        nowPlaying?.upNext
            .orEmpty()
            .asSequence()
            .filter { it.id != nowPlaying?.id }
            .mapNotNull { it.coverArtUrl }
            .distinct()
            .take(2)
            .toList()
    }
    LaunchedEffect(upcomingUrls) {
        preloadNaviampCoverArt(upcomingUrls)
    }
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
    private const val MaxConcurrentLoads = 4
    private val images = linkedMapOf<String, ImageBitmap>()
    private val palettes = linkedMapOf<String, List<NaviampRgbSample>>()
    private val mutex = Mutex()
    private val loadPermits = Semaphore(MaxConcurrentLoads)
    private class PendingLoad(val lock: Mutex = Mutex(), var users: Int = 0)
    private val pendingLoads = mutableMapOf<String, PendingLoad>()

    private suspend fun cachedPlayerColors(url: String, colors: NaviampColors): NaviampPlayerColors =
        mutex.withLock { palettes[url] }
            ?.let(::naviampAlbumPalette)
            ?.let { NaviampPlayerColors.from(it, colors) }
            ?: NaviampPlayerColors.fallback(colors)

    suspend fun image(url: String, targetSidePx: Int): ImageBitmap? {
        val cacheKey = "$url#$targetSidePx"
        mutex.withLock { touchImage(cacheKey) }?.let { return it }
        // Join identical requests before taking a network/decode slot. A cancelled, off-screen
        // owner releases its lock so a still-visible waiter can finish the request itself.
        val pending = mutex.withLock {
            pendingLoads.getOrPut(cacheKey) { PendingLoad() }.also { it.users++ }
        }
        try {
            return pending.lock.withLock {
                mutex.withLock { touchImage(cacheKey) }?.let { return@withLock it }
                loadPermits.withPermit {
                    val decoded = withContext(Dispatchers.Default) {
                        try {
                            platformCoverArtBytes(url)
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { decodePlatformCoverArt(it, targetSidePx) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    } ?: return@withPermit null
                    mutex.withLock {
                        putBounded(images, cacheKey, decoded.image, MaxImages)
                        putBounded(palettes, url, decoded.rgbSamples, MaxPalettes)
                    }
                    decoded.image
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (--pending.users == 0) pendingLoads.remove(cacheKey)
                }
            }
        }
    }

    private fun touchImage(key: String): ImageBitmap? = images.remove(key)?.also { images[key] = it }

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

private const val CoverArtTransitionMillis = 280
private const val CurrentMediaEmptyGraceMillis = 750L

private const val MinCoverArtSidePx = 128
private const val MaxCoverArtSidePx = 1024
private const val PaletteCoverArtSidePx = 128
