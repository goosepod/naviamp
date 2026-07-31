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
import androidx.compose.ui.graphics.asImageBitmap
import app.naviamp.domain.network.KtorSharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap

@Volatile
private var androidPlatformCoverArtByteLoader: (suspend (String) -> ByteArray?)? = null

fun setAndroidPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) {
    androidPlatformCoverArtByteLoader = loader
}

fun resetAndroidPlatformCoverArtByteLoader() {
    androidPlatformCoverArtByteLoader = null
    resetNaviampCoverArtCache()
}

internal suspend fun androidPlatformCoverArtBytes(url: String): ByteArray? =
    generatedRadioTileBytes(url) ?: androidPlatformCoverArtByteLoader?.invoke(url) ?: AndroidCoverArtHttpClient.getBytes(url)

/** Resolves authenticated artwork for native Android surfaces that require a local file. */
suspend fun androidPlatformCoverArtFile(context: Context, url: String): File? =
    AndroidCoverArtCache.imageFile(context.applicationContext, url)

private val AndroidCoverArtHttpClient = KtorSharedHttpClient()

internal actual suspend fun platformCoverArtBytes(url: String): ByteArray? =
    androidPlatformCoverArtBytes(url)

internal actual fun decodePlatformCoverArt(
    bytes: ByteArray,
    targetSidePx: Int,
): NaviampDecodedCoverArt? = decodeSampledBitmap(bytes, targetSidePx)?.let { bitmap ->
    NaviampDecodedCoverArt(
        image = bitmap.asImageBitmap(),
        rgbSamples = bitmap.rgbSamples(),
    )
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

    suspend fun imageFile(context: Context, url: String): File? {
        val bytes = imageBytes(context, url) ?: return null
        val cacheFile = File(context.cacheDir, "cover-art/${url.sha256()}.img")
        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            }.getOrElse { return null }
        }
        return cacheFile.takeIf(File::isFile)
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

private fun generatedRadioTileBytes(url: String): ByteArray? {
    val spec = naviampRadioTileSpec(url) ?: return null

    val bitmap = Bitmap.createBitmap(spec.sidePx, spec.sidePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bounds = RectF(0f, 0f, spec.sidePx.toFloat(), spec.sidePx.toFloat())
    paint.shader = LinearGradient(
        0f,
        0f,
        spec.sidePx.toFloat(),
        spec.sidePx.toFloat(),
        AndroidColor.rgb(spec.fromRgb shr 16, spec.fromRgb shr 8 and 0xFF, spec.fromRgb and 0xFF),
        AndroidColor.rgb(spec.toRgb shr 16, spec.toRgb shr 8 and 0xFF, spec.toRgb and 0xFF),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRoundRect(bounds, spec.cornerRadiusPx, spec.cornerRadiusPx, paint)

    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = spec.ringStrokePx
    paint.color = AndroidColor.argb(spec.ringAlpha, 255, 255, 255)
    canvas.drawCircle(spec.centerPx, spec.centerPx, spec.ringRadiusPx, paint)

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(spec.centerAlpha, 255, 255, 255)
    canvas.drawCircle(spec.centerPx, spec.centerPx, spec.centerRadiusPx, paint)

    paint.color = AndroidColor.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.textSize = spec.textSizePx
    val textBounds = Rect()
    paint.getTextBounds(spec.label, 0, spec.label.length, textBounds)
    canvas.drawText(spec.label, spec.centerPx, spec.centerPx - textBounds.exactCenterY(), paint)

    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
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

private fun android.graphics.Bitmap.rgbSamples(): List<NaviampRgbSample> {
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
    return samples
}
