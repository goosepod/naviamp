package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistMoveDirection
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Keyed by the owning row; moving it retains the row's controls and focus state. */
@Composable
internal fun SmartPlaylistMovementControls(
    rowKey: String,
    index: Int,
    count: Int,
    colors: NaviampColors,
    onMove: (SmartPlaylistMoveDirection) -> Unit,
) {
    val up = remember { FocusRequester() }
    val down = remember { FocusRequester() }
    var requestedFocus by remember { mutableStateOf<SmartPlaylistMoveDirection?>(null) }
    val position = stringResource(Res.string.smart_playlist_row_position, index + 1, count)
    LaunchedEffect(index, requestedFocus) {
        requestedFocus?.let { direction ->
            if (count > 1) {
                val target = if (direction == SmartPlaylistMoveDirection.Up && index > 0 || index == count - 1) up else down
                target.requestFocus()
            }
            requestedFocus = null
        }
    }
    Column {
        Row {
            SmartPlaylistMoveDirection.entries.forEach { direction ->
                val isUp = direction == SmartPlaylistMoveDirection.Up
                IconButton(
                    enabled = index + direction.offset in 0 until count,
                    onClick = { requestedFocus = direction; onMove(direction) },
                    modifier = Modifier.focusRequester(if (isUp) up else down)
                        .testTag("$rowKey:${if (isUp) "up" else "down"}")
                        .semantics { stateDescription = position; liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(if (isUp) NaviampIcons.ChevronUp else NaviampIcons.ChevronDown,
                        contentDescription = stringResource(if (isUp) Res.string.smart_playlist_move_up else Res.string.smart_playlist_move_down),
                        tint = if (index + direction.offset in 0 until count) colors.primaryText else colors.mutedText)
                }
            }
        }
        Text(position, color = colors.secondaryText, fontSize = 10.sp,
            modifier = Modifier.testTag("$rowKey:position"))
    }
}
