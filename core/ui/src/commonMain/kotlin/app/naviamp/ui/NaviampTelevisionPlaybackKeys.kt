package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo

/** Remote transport policy is shared; the native host only translates its media button. */
internal fun Modifier.naviampTelevisionPlaybackKeys(
    nowPlaying: NowPlayingUi?,
    playbackKey: Key = Key.MediaPlayPause,
    onPlayback: (NowPlayingPlaybackActionRequest) -> Unit,
): Modifier = composed {
    val focused = LocalWindowInfo.current.isWindowFocused
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (!focused) held = false }
    val handlePlaybackKey: (KeyEvent) -> Boolean = { event ->
        if (event.key != playbackKey) false
        else if (!focused) { held = false; false }
        else when (event.type) {
            KeyEventType.KeyDown -> when {
                held -> true
                playbackKey == Key.Spacebar &&
                    (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed || event.isShiftPressed) -> false
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
    if (playbackKey == Key.Spacebar) onPreviewKeyEvent(handlePlaybackKey) else onKeyEvent(handlePlaybackKey)
}
