package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.library_sources_cancel
import app.naviamp.ui.generated.resources.library_sources_description
import app.naviamp.ui.generated.resources.library_sources_empty
import app.naviamp.ui.generated.resources.library_sources_loading
import app.naviamp.ui.generated.resources.library_sources_connection_required
import app.naviamp.ui.generated.resources.library_sources_load_failed
import app.naviamp.ui.generated.resources.library_sources_save_failed
import app.naviamp.ui.generated.resources.library_sources_retry
import app.naviamp.ui.generated.resources.library_sources_save
import app.naviamp.ui.generated.resources.library_sources_title
import org.jetbrains.compose.resources.stringResource

internal const val LibrarySourcePickerTestTag = "library-source-picker"
internal const val LibrarySourceChoiceTagPrefix = "library-source-choice-"

@Composable
internal fun NaviampLibrarySourcePicker(
    colors: NaviampColors,
    picker: NaviampLibrarySourcePickerUi,
    actions: NaviampLibraryActions,
) {
    if (!picker.visible) return
    val errorMessage = picker.errorKind?.let { error ->
        stringResource(when (error) {
            NaviampLibrarySourcePickerError.ConnectionRequired -> Res.string.library_sources_connection_required
            NaviampLibrarySourcePickerError.LoadFailed -> Res.string.library_sources_load_failed
            NaviampLibrarySourcePickerError.SaveFailed -> Res.string.library_sources_save_failed
        })
    }
    AlertDialog(
        modifier = Modifier.testTag(LibrarySourcePickerTestTag),
        onDismissRequest = { if (!picker.loading && !picker.saving) actions.onCancelSources() },
        title = { Text(stringResource(Res.string.library_sources_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.library_sources_description),
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
                when {
                    picker.loading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.library_sources_loading))
                    }
                    picker.libraries.isEmpty() -> Text(
                        errorMessage ?: stringResource(Res.string.library_sources_empty),
                        color = colors.secondaryText,
                    )
                    else -> {
                        errorMessage?.let { Text(it, color = colors.secondaryText, fontSize = 12.sp) }
                        picker.libraries.forEach { library ->
                            val checked = library.id in picker.selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !picker.saving) { actions.onToggleSource(library.id) }
                                    .padding(vertical = 4.dp)
                                    .testTag(LibrarySourceChoiceTagPrefix + library.id),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = !picker.saving)
                                Text(library.name, color = colors.primaryText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (picker.libraries.isNotEmpty()) {
                TextButton(
                    enabled = !picker.loading && !picker.saving && picker.selectedIds.isNotEmpty(),
                    onClick = actions.onSaveSources,
                ) { Text(stringResource(Res.string.library_sources_save)) }
            } else if (!picker.loading && picker.errorKind == NaviampLibrarySourcePickerError.LoadFailed) {
                TextButton(onClick = actions.onRetrySources) {
                    Text(stringResource(Res.string.library_sources_retry))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !picker.saving, onClick = actions.onCancelSources) {
                Text(stringResource(Res.string.library_sources_cancel))
            }
        },
    )
}
