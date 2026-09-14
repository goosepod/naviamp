package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** TV text stays readable over any artwork pixel, including an all-white background. */
@Composable
internal fun TelevisionReadingSurface(colors: NaviampColors) {
    Box(Modifier.fillMaxSize().background(televisionReadingSurfaceColor(colors)))
}

internal fun televisionReadingSurfaceColor(colors: NaviampColors) =
    colors.background.copy(alpha = TelevisionReadingSurfaceAlpha)

internal const val TelevisionReadingSurfaceAlpha = 0.32f

internal val NaviampTelevisionColors = NaviampColors.Dark.copy(
    secondaryText = NaviampColors.Dark.primaryText,
    mutedText = NaviampColors.Dark.primaryText,
)
