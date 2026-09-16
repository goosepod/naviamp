package app.naviamp.ui

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Shared thumbnail bounds and palette sampling after native image decoding. */
internal fun naviampDecodedCoverArt(source: ImageBitmap, targetSidePx: Int): NaviampDecodedCoverArt {
    val size = naviampCoverArtSize(source.width, source.height, targetSidePx)
    val width = size.width
    val height = size.height
    val image = if (width == source.width && height == source.height) source else {
        ImageBitmap(width, height).also { target ->
            Canvas(target).drawImageRect(
                image = source,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(source.width, source.height),
                dstOffset = IntOffset.Zero,
                dstSize = size,
                paint = Paint().apply { filterQuality = FilterQuality.Low },
            )
        }
    }
    val pixels = IntArray(width * height)
    image.readPixels(pixels)
    return NaviampDecodedCoverArt(image, naviampCoverArtSamples(pixels, width, height))
}

internal fun naviampCoverArtSize(width: Int, height: Int, targetSidePx: Int): IntSize {
    val scale = (targetSidePx.coerceAtLeast(1).toDouble() / maxOf(width, height)).coerceAtMost(1.0)
    return IntSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
}

internal fun naviampCoverArtSamples(pixels: IntArray, width: Int, height: Int): List<NaviampRgbSample> =
    buildList {
        for (y in 0 until height step (height / 32).coerceAtLeast(1)) {
            for (x in 0 until width step (width / 32).coerceAtLeast(1)) {
                val pixel = pixels[y * width + x]
                if ((pixel ushr 24) > 200) {
                    add(NaviampRgbSample((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255))
                }
            }
        }
    }
