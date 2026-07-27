package app.naviamp.ui

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

private var iosPlatformCoverArtByteLoader: (suspend (String) -> ByteArray?)? = null

fun setIosPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) {
    iosPlatformCoverArtByteLoader = loader
}

fun resetIosPlatformCoverArtByteLoader() {
    iosPlatformCoverArtByteLoader = null
    resetNaviampCoverArtCache()
}

internal actual suspend fun platformCoverArtBytes(url: String): ByteArray? =
    iosPlatformCoverArtByteLoader?.invoke(url)

internal actual fun decodePlatformCoverArt(
    bytes: ByteArray,
    targetSidePx: Int,
): NaviampDecodedCoverArt? = runCatching {
    val decoded = SkiaImage.makeFromEncoded(bytes)
    NaviampDecodedCoverArt(
        image = decoded.toComposeImageBitmap(),
        rgbSamples = decoded.rgbSamples(),
    )
}.getOrNull()

private fun SkiaImage.rgbSamples(): List<NaviampRgbSample> {
    val bitmap = SkiaBitmap.makeFromImage(this)
    return try {
        val samples = mutableListOf<NaviampRgbSample>()
        val stepX = (width / 32).coerceAtLeast(1)
        val stepY = (height / 32).coerceAtLeast(1)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getColor(x, y)
                if ((pixel ushr 24) and 0xFF > 200) {
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
        samples
    } finally {
        bitmap.close()
    }
}
