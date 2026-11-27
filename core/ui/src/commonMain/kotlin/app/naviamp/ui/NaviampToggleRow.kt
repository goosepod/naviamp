package app.naviamp.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * Makes a whole on/off row one accessible toggle: a single focus target that reports its checked
 * state and role, so screen readers and switch access announce "on"/"off" and flip it with one
 * activation. The visual switch or checkbox inside the row must not handle clicks itself.
 */
internal fun Modifier.naviampToggleRow(
    checked: Boolean,
    enabled: Boolean = true,
    role: Role = Role.Switch,
    onCheckedChange: (Boolean) -> Unit,
): Modifier = toggleable(value = checked, enabled = enabled, role = role, onValueChange = onCheckedChange)

/**
 * Switch semantics for an on/off row whose click is already handled by its container, such as a
 * focusable TV button: it reports the switch role and current state instead of a plain button.
 */
internal fun Modifier.naviampToggleSemantics(checked: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}
