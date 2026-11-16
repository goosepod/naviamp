package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo

/** Remote transport policy is shared; the native host only translates its media button. */
internal fun Modifier.naviampTelevisionPlaybackKeys(
    nowPlaying: NowPlayingUi?,
    onPlayback: (NowPlayingPlaybackActionRequest) -> Unit,
): Modifier = composed {
    val focused = LocalWindowInfo.current.isWindowFocused
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (!focused) held = false }
    onKeyEvent { event ->
        if (event.key != Key.MediaPlayPause) false
        else if (!focused) { held = false; false }
        else when (event.type) {
            KeyEventType.KeyDown -> when {
                held -> true
                nowPlaying?.canPlayPause != true -> false
                else -> {
                    held = true
                    onPlayback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.TogglePlayPause))
                    true
                }
            }
            KeyEventType.KeyUp -> held.also { held = false }
            else -> false
        }
    }
}
