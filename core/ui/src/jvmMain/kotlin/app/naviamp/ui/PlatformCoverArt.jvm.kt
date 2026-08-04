package app.naviamp.ui

import androidx.compose.ui.graphics.toComposeImageBitmap
import app.naviamp.domain.network.KtorSharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import javax.imageio.ImageIO

@Volatile
private var platformCoverArtByteLoader: suspend (String) -> ByteArray = ::defaultPlatformCoverArtBytes

fun setJvmPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray) {
    platformCoverArtByteLoader = loader
}

fun resetJvmPlatformCoverArtByteLoader() {
    platformCoverArtByteLoader = ::defaultPlatformCoverArtBytes
    resetNaviampCoverArtCache()
}

fun jvmGeneratedCoverArtBytes(url: String): ByteArray? =
    generatedRadioTileBytes(url)

suspend fun preloadJvmPlatformCoverArt(urls: Iterable<String>) {
    preloadNaviampCoverArt(urls)
}

internal actual suspend fun platformCoverArtBytes(url: String): ByteArray? =
    platformCoverArtByteLoader(url)

internal actual fun decodePlatformCoverArt(
    bytes: ByteArray,
    targetSidePx: Int,
): NaviampDecodedCoverArt? = runCatching {
    val source = ImageIO.read(bytes.inputStream()) ?: return@runCatching null
    val longestSide = maxOf(source.width, source.height)
    val scale = (targetSidePx.toDouble() / longestSide).coerceAtMost(1.0)
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    val decoded = if (targetWidth == source.width && targetHeight == source.height) {
        source
    } else {
        BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB).also { target ->
            target.createGraphics().use { graphics ->
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
            }
        }
    }
    val encoded = ByteArrayOutputStream().use { output ->
        check(ImageIO.write(decoded, "png", output))
        output.toByteArray()
    }
    NaviampDecodedCoverArt(
        image = SkiaImage.makeFromEncoded(encoded).toComposeImageBitmap(),
        rgbSamples = jvmRgbSamples(decoded),
    )
}.getOrNull()

private fun jvmRgbSamples(image: BufferedImage): List<NaviampRgbSample> {
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

private inline fun <T : java.awt.Graphics> T.use(block: (T) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}

private suspend fun defaultPlatformCoverArtBytes(url: String): ByteArray =
    withContext(Dispatchers.IO) {
        generatedRadioTileBytes(url)?.let { return@withContext it }
        DefaultPlatformCoverArtHttpClient.getBytes(url) ?: ByteArray(0)
    }

private val DefaultPlatformCoverArtHttpClient = KtorSharedHttpClient()

private fun generatedRadioTileBytes(url: String): ByteArray? {
    val spec = naviampRadioTileSpec(url) ?: return null
    val from = AwtColor(spec.fromRgb)
    val to = AwtColor(spec.toRgb)
    val image = BufferedImage(spec.sidePx, spec.sidePx, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.paint = GradientPaint(
            0f,
            0f,
            from,
            spec.sidePx.toFloat(),
            spec.sidePx.toFloat(),
            to,
        )
        graphics.fill(RoundRectangle2D.Float(0f, 0f, spec.sidePx.toFloat(), spec.sidePx.toFloat(), spec.cornerRadiusPx, spec.cornerRadiusPx))
        graphics.color = AwtColor(255, 255, 255, spec.ringAlpha)
        graphics.stroke = java.awt.BasicStroke(spec.ringStrokePx)
        val ringDiameter = (spec.ringRadiusPx * 2).toInt()
        graphics.drawOval((spec.centerPx - spec.ringRadiusPx).toInt(), (spec.centerPx - spec.ringRadiusPx).toInt(), ringDiameter, ringDiameter)
        graphics.color = AwtColor(255, 255, 255, spec.centerAlpha)
        val centerDiameter = (spec.centerRadiusPx * 2).toInt()
        graphics.fillOval((spec.centerPx - spec.centerRadiusPx).toInt(), (spec.centerPx - spec.centerRadiusPx).toInt(), centerDiameter, centerDiameter)
        graphics.color = AwtColor.WHITE
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, spec.textSizePx.toInt())
        val metrics = graphics.fontMetrics
        val x = (spec.sidePx - metrics.stringWidth(spec.label)) / 2
        val y = ((spec.sidePx - metrics.height) / 2) + metrics.ascent
        graphics.drawString(spec.label, x, y)
    } finally {
        graphics.dispose()
    }
    return ByteArrayOutputStream().use { output ->
        ImageIO.write(image, "png", output)
        output.toByteArray()
    }
}

private object JvmCoverArtCache {
    private const val MaxShaderImages = 48
    private val shaderImages = object : LinkedHashMap<String, SkiaImage>(MaxShaderImages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SkiaImage>?): Boolean {
            val shouldRemove = size > MaxShaderImages
            if (shouldRemove) eldest?.value?.close()
            return shouldRemove
        }
    }
    private val shaderBitmaps = object : LinkedHashMap<String, SkiaBitmap>(MaxShaderImages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SkiaBitmap>?): Boolean {
            val shouldRemove = size > MaxShaderImages
            if (shouldRemove) eldest?.value?.close()
            return shouldRemove
        }
    }

    private fun cachedShaderImage(url: String): SkiaImage? =
        synchronized(shaderImages) {
            synchronized(shaderBitmaps) {
                shaderBitmaps[url]
            }
            shaderImages[url]
        }

    suspend fun shaderImage(url: String): SkiaImage =
        cachedShaderImage(url) ?: withContext(Dispatchers.IO) {
            cachedShaderImage(url) ?: run {
                val decoded = SkiaImage.makeFromEncoded(platformCoverArtByteLoader(url))
                val bitmap = SkiaBitmap.makeFromImage(decoded).setImmutable()
                val image = SkiaImage.makeFromBitmap(bitmap)
                decoded.close()
                image.also {
                    synchronized(shaderImages) {
                        shaderBitmaps[url] = bitmap
                        shaderImages[url] = it
                    }
                }
            }
        }

}

internal suspend fun jvmPlatformCoverArtShaderImage(url: String): SkiaImage =
    JvmCoverArtCache.shaderImage(url)

internal suspend fun jvmPlatformCoverArtPlayerColors(
    url: String,
    colors: NaviampColors = NaviampColors.Dark,
): NaviampPlayerColors =
    naviampCoverArtPlayerColors(url, colors)
