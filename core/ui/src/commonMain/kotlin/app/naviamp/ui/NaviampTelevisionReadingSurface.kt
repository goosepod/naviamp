package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** TV text stays readable over any artwork pixel, including an all-white background. */
@Composable
internal fun TelevisionReadingSurface(colors: NaviampColors) {
    Box(Modifier.fillMaxSize().background(readableSurfaceColor(colors)))
}
