package app.naviamp.ui

import androidx.compose.ui.graphics.toComposeImageBitmap
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
    naviampDecodedCoverArt(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(), targetSidePx)
}.getOrNull()
