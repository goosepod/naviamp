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
import app.naviamp.domain.network.KtorSharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.util.LinkedHashMap

@Volatile
private var platformCoverArtByteLoader: suspend (String) -> ByteArray = ::defaultPlatformCoverArtBytes

fun setJvmPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray) {
    platformCoverArtByteLoader = loader
}

fun resetJvmPlatformCoverArtByteLoader() {
    platformCoverArtByteLoader = ::defaultPlatformCoverArtBytes
}

suspend fun preloadJvmPlatformCoverArt(urls: Iterable<String>) {
    urls.distinct().forEach { url ->
        runCatching {
            JvmCoverArtCache.image(url)
        }
    }
}

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    var image by remember(url) { mutableStateOf(url?.let { JvmCoverArtCache.cachedImage(it) }) }

    LaunchedEffect(url) {
        if (url == null) {
            image = null
            return@LaunchedEffect
        }
        image = runCatching {
            JvmCoverArtCache.image(url)
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
    var playerColors by remember(url, colors) {
        mutableStateOf(
            url?.let { JvmCoverArtCache.cachedPlayerColors(it, colors) }
                ?: NaviampPlayerColors.fallback(colors),
        )
    }

    LaunchedEffect(url) {
        playerColors = if (url == null) {
            NaviampPlayerColors.fallback(colors)
        } else {
            runCatching {
                JvmCoverArtCache.playerColors(url, colors)
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

private suspend fun defaultPlatformCoverArtBytes(url: String): ByteArray =
    withContext(Dispatchers.IO) {
        DefaultPlatformCoverArtHttpClient.getBytes(url) ?: ByteArray(0)
    }

private val DefaultPlatformCoverArtHttpClient = KtorSharedHttpClient()

private object JvmCoverArtCache {
    private const val MaxImages = 240
    private const val MaxShaderImages = 48
    private const val MaxPalettes = 240

    private val images = object : LinkedHashMap<String, ImageBitmap>(MaxImages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MaxImages
    }
    private val palettes = object : LinkedHashMap<String, List<NaviampRgbSample>>(MaxPalettes, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<NaviampRgbSample>>?): Boolean =
            size > MaxPalettes
    }
    private val shaderImages = object : LinkedHashMap<String, SkiaImage>(MaxShaderImages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SkiaImage>?): Boolean {
            val shouldRemove = size > MaxShaderImages
            if (shouldRemove) eldest?.value?.close()
            return shouldRemove
        }
    }

    fun cachedImage(url: String): ImageBitmap? =
        synchronized(images) { images[url] }

    fun cachedPlayerColors(url: String, colors: NaviampColors): NaviampPlayerColors? =
        synchronized(palettes) { palettes[url] }
            ?.let { naviampAlbumPalette(it) }
            ?.let { NaviampPlayerColors.from(it, colors) }

    suspend fun image(url: String): ImageBitmap =
        cachedImage(url) ?: withContext(Dispatchers.IO) {
            cachedImage(url) ?: SkiaImage.makeFromEncoded(platformCoverArtByteLoader(url))
                .toComposeImageBitmap()
                .also { image ->
                    synchronized(images) {
                        images[url] = image
                    }
                }
        }

    suspend fun shaderImage(url: String): SkiaImage =
        synchronized(shaderImages) { shaderImages[url] } ?: withContext(Dispatchers.IO) {
            synchronized(shaderImages) { shaderImages[url] } ?: SkiaImage.makeFromEncoded(platformCoverArtByteLoader(url))
                .also { image ->
                    synchronized(shaderImages) {
                        shaderImages[url] = image
                    }
                }
        }

    suspend fun playerColors(
        url: String,
        colors: NaviampColors = NaviampColors.Dark,
    ): NaviampPlayerColors {
        val samples = synchronized(palettes) { palettes[url] }
            ?: withContext(Dispatchers.IO) {
                synchronized(palettes) { palettes[url] } ?: jvmRgbSamples(platformCoverArtByteLoader(url))
                    .also { loadedSamples ->
                        synchronized(palettes) {
                            palettes[url] = loadedSamples
                        }
                    }
            }
        return naviampAlbumPalette(samples)
            ?.let { NaviampPlayerColors.from(it, colors) }
            ?: NaviampPlayerColors.fallback(colors)
    }
}

internal suspend fun jvmPlatformCoverArtShaderImage(url: String): SkiaImage =
    JvmCoverArtCache.shaderImage(url)
