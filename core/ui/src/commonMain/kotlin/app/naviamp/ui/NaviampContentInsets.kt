package app.naviamp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared content viewport: protect system bars and consume overlapping keyboard insets once. */
@Composable
fun Modifier.naviampContentInsets(
    systemInsets: WindowInsets = WindowInsets.safeDrawing,
    keyboardInsets: WindowInsets = WindowInsets.ime,
): Modifier = windowInsetsPadding(systemInsets).windowInsetsPadding(keyboardInsets)
