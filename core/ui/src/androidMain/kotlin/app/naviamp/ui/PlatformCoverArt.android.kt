package app.naviamp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.unit.dp
import app.naviamp.domain.network.KtorSharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.min

@Volatile
private var androidPlatformCoverArtByteLoader: (suspend (String) -> ByteArray?)? = null

fun setAndroidPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) {
    androidPlatformCoverArtByteLoader = loader
}

fun resetAndroidPlatformCoverArtByteLoader() {
    androidPlatformCoverArtByteLoader = null
}

internal suspend fun androidPlatformCoverArtBytes(url: String): ByteArray? =
    generatedRadioTileBytes(url) ?: androidPlatformCoverArtByteLoader?.invoke(url) ?: AndroidCoverArtHttpClient.getBytes(url)

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
actual fun PlatformExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val context = LocalContext.current
    val targetImageSizePx = with(LocalDensity.current) {
        ceil(maxOf(maxWidth.toPx(), maxHeight.toPx())).toInt()
            .coerceIn(MinCoverArtBitmapSidePx, MaxCoverArtBitmapSidePx)
    }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url, targetImageSizePx) {
        image = url?.let {
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidCoverArtCache.imageBytes(context, it)
                        ?.let { bytes -> decodeSampledBitmap(bytes, targetImageSizePx)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }

    val imageWidth = image?.width?.takeIf { it > 0 } ?: 1
    val imageHeight = image?.height?.takeIf { it > 0 } ?: 1
    val scale = min(maxWidth.value / imageWidth, maxHeight.value / imageHeight)
    val width = (imageWidth * scale).dp
    val height = (imageHeight * scale).dp
    Box(
        modifier = Modifier
            .size(width, height)
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

        return withContext(Dispatchers.IO + NonCancellable) {
            androidPlatformCoverArtBytes(url)?.takeIf(::isDecodableImage)?.also { bytes ->
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeBytes(bytes)
                }
                putHot(url, bytes)
            }
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
private const val RadioTileSidePx = 512
private const val RadioTileScheme = "naviamp-radio-tile://"

private fun generatedRadioTileBytes(url: String): ByteArray? {
    if (!url.startsWith(RadioTileScheme)) return null
    val params = url.substringAfter("?", "")
        .split("&")
        .mapNotNull { part ->
            val key = part.substringBefore("=", "")
            val value = part.substringAfter("=", "")
            if (key.isBlank()) null else key to value
        }
        .toMap()
    val label = params["label"]?.urlDecode()?.takeIf { it.isNotBlank() } ?: "RAD"
    val from = "#${params["from"] ?: "465d7a"}"
    val to = "#${params["to"] ?: "161f2c"}"

    val bitmap = Bitmap.createBitmap(RadioTileSidePx, RadioTileSidePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bounds = RectF(0f, 0f, RadioTileSidePx.toFloat(), RadioTileSidePx.toFloat())
    paint.shader = LinearGradient(
        0f,
        0f,
        RadioTileSidePx.toFloat(),
        RadioTileSidePx.toFloat(),
        AndroidColor.parseColor(from),
        AndroidColor.parseColor(to),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRoundRect(bounds, 48f, 48f, paint)

    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 22f
    paint.color = AndroidColor.argb(54, 255, 255, 255)
    canvas.drawCircle(256f, 256f, 118f, paint)

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(42, 255, 255, 255)
    canvas.drawCircle(256f, 256f, 56f, paint)

    paint.color = AndroidColor.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.textSize = if (label.length <= 2) 126f else 104f
    val textBounds = Rect()
    paint.getTextBounds(label, 0, label.length, textBounds)
    canvas.drawText(label, 256f, 256f - textBounds.exactCenterY(), paint)

    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
}

private fun String.urlDecode(): String =
    replace("+", " ").replace(Regex("%([0-9A-Fa-f]{2})")) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }

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
