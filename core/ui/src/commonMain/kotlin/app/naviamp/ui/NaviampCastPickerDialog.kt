package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class NaviampCastPickerTargetUi(val id: String, val displayName: String)
enum class NaviampCastPickerProblemUi { Discovery, Connection, TargetLost }
data class NaviampCastPickerUi(
    val visible: Boolean = false,
    val targets: List<NaviampCastPickerTargetUi> = emptyList(),
    val connectingTargetId: String? = null,
    val selectedTargetId: String? = null,
    val problem: NaviampCastPickerProblemUi? = null,
)

@Composable
fun NaviampCastPickerDialog(
    state: NaviampCastPickerUi,
    onSelect: (String) -> Unit,
    onLocal: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    NaviampPopupPresence()
    val colors = NaviampColors.Dark
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = colors.background,
            surface = colors.controlSurface,
            primary = colors.accent,
            onPrimary = colors.onAccent,
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
        ),
        typography = rememberNaviampTypography(),
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.now_playing_cast_to_device)) },
            text = {
                Column(Modifier.widthIn(min = 260.dp, max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state.problem) {
                        NaviampCastPickerProblemUi.Discovery -> Text(stringResource(Res.string.cast_picker_discovery_failed))
                        NaviampCastPickerProblemUi.Connection -> Text(stringResource(Res.string.cast_picker_connection_failed))
                        NaviampCastPickerProblemUi.TargetLost -> Text(stringResource(Res.string.cast_picker_target_lost))
                        null -> if (state.targets.isEmpty()) Text(stringResource(Res.string.cast_picker_searching))
                    }
                    if (state.connectingTargetId != null) Text(stringResource(Res.string.common_connecting))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(state.targets, key = { it.id }) { target ->
                            TextButton(onClick = { onSelect(target.id) }, enabled = state.connectingTargetId == null,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(target.displayName)
                            }
                        }
                    }
                    if (state.selectedTargetId != null) TextButton(onClick = onLocal) {
                        Text(stringResource(Res.string.cast_picker_return_local))
                    }
                    if (state.problem != null) TextButton(onClick = onRetry) {
                        Text(stringResource(Res.string.library_sources_retry))
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) } },
            containerColor = colors.controlSurface,
        )
    }
}
