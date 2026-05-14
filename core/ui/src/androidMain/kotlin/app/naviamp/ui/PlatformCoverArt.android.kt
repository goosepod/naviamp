package app.naviamp.ui

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.pow

@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    val context = LocalContext.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val elevated = size >= 180.dp
    val shadowMargin = if (elevated) 12.dp else 0.dp
    val shape = RoundedCornerShape(cornerRadius)

    LaunchedEffect(url) {
        image = url?.let { coverArtUrl ->
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidCoverArtCache.imageBytes(context, coverArtUrl)
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }

    Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = Modifier
            .size(size + shadowMargin * 2),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (elevated) {
                        Modifier
                            .shadow(24.dp, shape, clip = false)
                            .shadow(7.dp, shape, clip = false)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(colors.albumArtPlaceholder),
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
}

@Composable
actual fun rememberPlatformCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color> {
    val context = LocalContext.current
    var gradientColors by remember {
        mutableStateOf(listOf(colors.backgroundWarm, colors.background, colors.backgroundOlive))
    }

    LaunchedEffect(url) {
        gradientColors = if (url == null) {
            listOf(colors.backgroundWarm, colors.background, colors.backgroundOlive)
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidCoverArtCache.imageBytes(context, url)
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?.albumGradientColors(colors)
                }
            }.getOrNull() ?: listOf(colors.backgroundWarm, colors.background, colors.backgroundOlive)
        }
    }

    return gradientColors
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

private fun android.graphics.Bitmap.albumGradientColors(colors: NaviampColors): List<Color> {
    val buckets = mutableMapOf<Int, ColorBucket>()
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)
    val hsv = FloatArray(3)

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
                android.graphics.Color.RGBToHSV(red, green, blue, hsv)
                val saturation = hsv[1]
                val brightness = hsv[2]
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

    val primary = candidates.firstOrNull()?.color() ?: colors.albumArtPlaceholder
    val secondary = candidates.firstOrNull { primary.colorDistance(it.color()) > 0.045f }?.color()
        ?: candidates.firstOrNull { primary.hueDistance(it.color()) > 0.08f }?.color()
        ?: candidates.getOrNull(1)?.color()
        ?: primary
    val accent = candidates
        .map { it.color() }
        .filter { primary.colorDistance(it) > 0.025f || primary.hueDistance(it) > 0.06f }
        .maxByOrNull { it.saturationScore() + it.colorDistance(primary) }
        ?: primary

    val start = primary
        .mix(Color.White, 0.03f)
        .mix(Color.Black, 0.34f)
        .mix(colors.background, 0.16f)
    val middle = accent
        .mix(primary, 0.28f)
        .mix(Color.Black, 0.42f)
        .mix(colors.background, 0.10f)
    val end = secondary
        .mix(Color.Black, 0.66f)
        .mix(colors.background, 0.12f)
    return listOf(start, middle, end)
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
        val saturationAverage = (saturation / count).toFloat()
        val brightnessAverage = (brightness / count).toFloat()
        val brightnessScore = 1.0 - abs(brightnessAverage - 0.58f).coerceAtMost(0.58f) / 0.58
        return count.toDouble().pow(0.55) * (saturationAverage + 0.12) * (brightnessScore + 0.55)
    }
}

private fun Color.mix(other: Color, amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * clamped,
        green = green + (other.green - green) * clamped,
        blue = blue + (other.blue - blue) * clamped,
        alpha = alpha + (other.alpha - alpha) * clamped,
    )
}

private fun Color.saturationScore(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    return if (max <= 0f) 0f else (max - min) / max
}

private fun Color.hueDistance(other: Color): Float {
    val hsv = FloatArray(3)
    val otherHsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    android.graphics.Color.RGBToHSV((other.red * 255).toInt(), (other.green * 255).toInt(), (other.blue * 255).toInt(), otherHsv)
    val distance = abs(hsv[0] / 360f - otherHsv[0] / 360f)
    return minOf(distance, 1f - distance)
}

private fun Color.colorDistance(other: Color): Float {
    val redDistance = red - other.red
    val greenDistance = green - other.green
    val blueDistance = blue - other.blue
    return redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance
}
