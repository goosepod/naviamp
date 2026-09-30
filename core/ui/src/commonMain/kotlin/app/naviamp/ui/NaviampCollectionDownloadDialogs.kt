package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.common_cancel
import app.naviamp.ui.generated.resources.download_keep_collection
import app.naviamp.ui.generated.resources.download_preview_at_least
import app.naviamp.ui.generated.resources.download_preview_budget
import app.naviamp.ui.generated.resources.download_preview_empty
import app.naviamp.ui.generated.resources.download_preview_estimate
import app.naviamp.ui.generated.resources.download_preview_storage_limit
import app.naviamp.ui.generated.resources.download_preview_summary
import app.naviamp.ui.generated.resources.download_preview_title
import app.naviamp.ui.generated.resources.download_preview_too_large
import app.naviamp.ui.generated.resources.download_preview_unsupported
import app.naviamp.ui.generated.resources.download_stop_keep_files
import app.naviamp.ui.generated.resources.download_stop_remove_files
import app.naviamp.ui.generated.resources.download_stop_summary
import app.naviamp.ui.generated.resources.download_stop_title
import org.jetbrains.compose.resources.stringResource

/** Collection decisions belong to the active shared shell, regardless of the selected route. */
@Composable
internal fun NaviampCollectionDownloadDialogs(
    downloads: NaviampDownloadsScreenUi,
    actions: NaviampDownloadsActions,
    colors: NaviampColors,
) {
    downloads.collectionPreview?.let { preview ->
        NaviampPopupPresence()
        AlertDialog(
            onDismissRequest = actions.onDismissCollection,
            containerColor = colors.controlSurface,
            title = { Text(stringResource(Res.string.download_preview_title, preview.title), color = colors.primaryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val error = when (preview.error) {
                        NaviampCollectionDownloadPreviewError.TooLarge -> Res.string.download_preview_too_large
                        NaviampCollectionDownloadPreviewError.Unsupported -> Res.string.download_preview_unsupported
                        NaviampCollectionDownloadPreviewError.Empty -> Res.string.download_preview_empty
                        NaviampCollectionDownloadPreviewError.StorageLimit -> Res.string.download_preview_storage_limit
                        null -> null
                    }
                    if (error != null) {
                        Text(stringResource(error), color = colors.secondaryText)
                    } else {
                        Text(stringResource(Res.string.download_preview_summary,
                            preview.albumCount, preview.trackCount, preview.alreadyDownloadedCount),
                            color = colors.secondaryText)
                        val estimate = preview.estimatedNewBytes?.storageBytesLabel()
                            ?: stringResource(Res.string.download_preview_at_least,
                                preview.knownNewBytes.storageBytesLabel())
                        Text(stringResource(Res.string.download_preview_estimate, estimate),
                            color = colors.secondaryText)
                        Text(stringResource(Res.string.download_preview_budget,
                            preview.remainingBudgetBytes.storageBytesLabel()), color = colors.secondaryText)
                    }
                }
            },
            confirmButton = {
                if (preview.error == null) {
                    TextButton(onClick = actions.onConfirmCollection) {
                        Text(stringResource(Res.string.download_keep_collection))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissCollection) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
    downloads.collectionRemoval?.let { removal ->
        NaviampPopupPresence()
        AlertDialog(
            onDismissRequest = actions.onDismissCollection,
            containerColor = colors.controlSurface,
            title = { Text(stringResource(Res.string.download_stop_title, removal.title), color = colors.primaryText) },
            text = { Text(stringResource(Res.string.download_stop_summary), color = colors.secondaryText) },
            confirmButton = {
                TextButton(onClick = { actions.onStopCollection(false) }) {
                    Text(stringResource(Res.string.download_stop_keep_files))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onStopCollection(true) }) {
                    Text(stringResource(Res.string.download_stop_remove_files))
                }
                TextButton(onClick = actions.onDismissCollection) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}
