package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The first iOS compilation boundary deliberately renders a stable placeholder.
 *
 * Network-backed image loading and palette extraction require a host-provided authenticated byte
 * loader and native image decoder; those belong in the iOS application milestone rather than an
 * incomplete implicit network client here.
 */
@Composable
actual fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.albumArtPlaceholder),
    )
}

@Composable
actual fun PlatformExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    Box(
        modifier = Modifier
            .size(maxWidth, maxHeight)
            .background(colors.albumArtPlaceholder),
    )
}

@Composable
actual fun rememberPlatformCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color> = remember(colors) {
    NaviampPlayerColors.fallback(colors).gradientColors
}

@Composable
actual fun rememberPlatformCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors = remember(colors) {
    NaviampPlayerColors.fallback(colors)
}
