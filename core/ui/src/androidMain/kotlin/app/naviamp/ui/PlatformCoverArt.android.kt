package app.naviamp.ui

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.naviamp.domain.network.KtorSharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlin.math.ceil

@Volatile
private var androidPlatformCoverArtByteLoader: (suspend (String) -> ByteArray?)? = null

fun setAndroidPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) {
    androidPlatformCoverArtByteLoader = loader
}

fun resetAndroidPlatformCoverArtByteLoader() {
    androidPlatformCoverArtByteLoader = null
}

internal suspend fun androidPlatformCoverArtBytes(url: String): ByteArray? =
    androidPlatformCoverArtByteLoader?.invoke(url) ?: AndroidCoverArtHttpClient.getBytes(url)

private val AndroidCoverArtHttpClient = KtorSharedHttpClient()

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    val context = LocalContext.current
    val targetImageSizePx = with(LocalDensity.current) {
        ceil(size.toPx()).toInt().coerceIn(MinCoverArtBitmapSidePx, MaxCoverArtBitmapSidePx)
    }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val shape = RoundedCornerShape(cornerRadius)

    LaunchedEffect(url, targetImageSizePx) {
        if (url == null) {
            image = null
            return@LaunchedEffect
        }
        image = runCatching {
            withContext(Dispatchers.IO) {
                AndroidCoverArtCache.imageBytes(context, url)
                    ?.let { decodeSampledBitmap(it, targetImageSizePx)?.asImageBitmap() }
            }
        }.getOrNull()
    }

    Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = Modifier.size(size),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
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
    val context = LocalContext.current
    var playerColors by remember {
        mutableStateOf(NaviampPlayerColors.fallback(colors))
    }

    LaunchedEffect(url) {
        playerColors = if (url == null) {
            NaviampPlayerColors.fallback(colors)
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidCoverArtCache.imageBytes(context, url)
                        ?.let { decodeSampledBitmap(it, PaletteBitmapSidePx) }
                        ?.albumPalette()
                        ?.let { NaviampPlayerColors.from(it, colors) }
                }
            }.getOrNull() ?: NaviampPlayerColors.fallback(colors)
        }
    }

    return playerColors
}

private object AndroidCoverArtCache {
    private const val MaxHotBytes = 16L * 1024L * 1024L
    private val hotImages = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var hotBytes = 0L

    suspend fun imageBytes(context: Context, url: String): ByteArray? {
        synchronized(this) {
            hotImages[url]?.let { bytes ->
                if (isDecodableImage(bytes)) return bytes
                hotImages.remove(url)
                hotBytes -= bytes.size
            }
        }

        val cacheFile = File(context.cacheDir, "cover-art/${url.sha256()}.img")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            runCatching { cacheFile.readBytes() }
                .getOrNull()
                ?.takeIf(::isDecodableImage)
                ?.also {
                    putHot(url, it)
                    return it
                }
            runCatching { cacheFile.delete() }
        }

        return androidPlatformCoverArtBytes(url)?.takeIf(::isDecodableImage)?.also { bytes ->
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            }
            putHot(url, bytes)
        }
    }

    private fun putHot(url: String, bytes: ByteArray) {
        synchronized(this) {
            hotImages.remove(url)?.let { hotBytes -= it.size }
            hotImages[url] = bytes
            hotBytes += bytes.size
            while (hotBytes > MaxHotBytes && hotImages.isNotEmpty()) {
                val eldest = hotImages.entries.iterator().next()
                hotBytes -= eldest.value.size
                hotImages.remove(eldest.key)
            }
        }
    }
}

private const val MinCoverArtBitmapSidePx = 128
private const val MaxCoverArtBitmapSidePx = 1024
private const val PaletteBitmapSidePx = 128

private fun isDecodableImage(bytes: ByteArray): Boolean =
    BitmapFactory.Options().let { options ->
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.outWidth > 0 && options.outHeight > 0
    }

internal fun decodeSampledBitmap(bytes: ByteArray, maxSidePx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun sampleSizeFor(width: Int, height: Int, maxSidePx: Int): Int {
    var sampleSize = 1
    val target = maxSidePx.coerceAtLeast(1)
    while ((width / sampleSize) > target || (height / sampleSize) > target) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun android.graphics.Bitmap.albumPalette(): NaviampAlbumPalette? {
    val samples = mutableListOf<NaviampRgbSample>()
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha > 200) {
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                samples += NaviampRgbSample(red, green, blue)
            }
            x += stepX
        }
        y += stepY
    }
    return naviampAlbumPalette(samples)
}
