package app.naviamp.ui

import androidx.compose.runtime.Composable

/** Hover tooltips do not apply to the initial touch-first iOS host. */
@Composable
actual fun NaviampTooltip(
    text: String,
    colors: NaviampColors,
    content: @Composable () -> Unit,
) {
    content()
}
