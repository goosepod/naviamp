package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A static QR code for a Connect link (#715). The symbol is encoded once per link and drawn as one
 * cached path; it never animates, so it costs nothing between offers.
 */
@Composable
internal fun NaviampConnectQrCode(
    link: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val symbol = remember(link) { NaviampQrCode.encode(link) } ?: return
    val path = remember(symbol) {
        Path().apply {
            for (y in 0 until symbol.size) for (x in 0 until symbol.size) {
                if (symbol[x, y]) addRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f))
            }
        }
    }
    // Scanners need dark modules on a light field in every theme, with a four-module quiet zone.
    Box(
        modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .testTag("connect-qr"),
    ) {
        Canvas(Modifier.padding(8.dp).aspectRatio(1f)) {
            val modules = symbol.size + QuietZoneModules * 2
            val cell = this.size.minDimension / modules
            scale(cell, cell, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                translate(QuietZoneModules.toFloat(), QuietZoneModules.toFloat()) {
                    drawPath(path, Color.Black)
                }
            }
        }
    }
}

private const val QuietZoneModules = 4

/**
 * A host's camera scanner (#715). Core owns when scanning starts, the permission fallback and what a
 * scanned link does; the host only supplies the camera, its permission prompt and a decoded string.
 */
interface NaviampQrScanner {
    /** Whether this device has a camera that can scan. */
    val available: Boolean

    @Composable fun hasCameraPermission(): Boolean

    /** Returns a launcher for the system camera-permission prompt; [onResult] receives the answer. */
    @Composable fun rememberCameraPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

    /** Camera preview that reports each decoded QR text while composed. */
    @Composable fun Viewfinder(onScanned: (String) -> Unit, modifier: Modifier)
}

val LocalNaviampQrScanner = staticCompositionLocalOf<NaviampQrScanner?> { null }
