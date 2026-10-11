package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinitionUnavailable
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SmartPlaylistInspectionDialog(
    editing: SmartPlaylistEditingResult,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
) {
    require(editing !is SmartPlaylistEditingResult.Editable)
    NaviampPopupPresence()
    NaviampAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.smart_playlist_inspection_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val explanation = when (editing) {
                    is SmartPlaylistEditingResult.ReadOnly -> Res.string.smart_playlist_inspection_read_only
                    is SmartPlaylistEditingResult.Unavailable -> when (editing.reason) {
                        SmartPlaylistDefinitionUnavailable.Missing -> Res.string.smart_playlist_inspection_missing
                        SmartPlaylistDefinitionUnavailable.PermissionDenied -> Res.string.smart_playlist_inspection_permission
                        SmartPlaylistDefinitionUnavailable.NotFound -> Res.string.smart_playlist_inspection_not_found
                    }
                    is SmartPlaylistEditingResult.Editable -> error("Editable definitions use the builder")
                }
                Text(stringResource(explanation), modifier = Modifier.testTag("smart-playlist-inspection-status"))
                if (editing is SmartPlaylistEditingResult.ReadOnly) SelectionContainer {
                    Text(editing.originalJson, color = colors.primaryText, fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, modifier = Modifier.testTag("smart-playlist-original-definition"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.common_close)) }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}
