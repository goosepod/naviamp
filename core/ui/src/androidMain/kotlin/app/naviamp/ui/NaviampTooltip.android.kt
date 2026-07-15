package app.naviamp.ui

import androidx.compose.runtime.Composable

@Composable
actual fun NaviampTooltip(
    text: String,
    colors: NaviampColors,
    content: @Composable () -> Unit,
) {
    content()
}
