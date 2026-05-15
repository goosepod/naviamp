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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedHashMap

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    val context = LocalContext.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val shape = RoundedCornerShape(cornerRadius)

    LaunchedEffect(url) {
        if (url == null) {
            image = null
            return@LaunchedEffect
        }
        image = runCatching {
            withContext(Dispatchers.IO) {
                AndroidCoverArtCache.imageBytes(context, url)
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
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
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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

    fun imageBytes(context: Context, url: String): ByteArray? {
        synchronized(this) {
            hotImages[url]?.let { return it }
        }

        val cacheFile = File(context.cacheDir, "cover-art/${url.sha256()}.img")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return runCatching { cacheFile.readBytes() }
                .getOrNull()
                ?.also { putHot(url, it) }
        }

        return runCatching {
            URL(url).openStream().use { input -> input.readBytes() }
        }.getOrNull()?.also { bytes ->
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
