package app.naviamp.ui

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

internal actual suspend fun platformCoverArtBytes(url: String): ByteArray? =
    loadNaviampCoverArtBytes(url)

internal actual fun decodePlatformCoverArt(
    bytes: ByteArray,
    targetSidePx: Int,
): NaviampDecodedCoverArt? = runCatching {
    naviampDecodedCoverArt(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(), targetSidePx)
}.getOrNull()
