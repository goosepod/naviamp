package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NaviampDiscoveryRequestsUi(discovery: SharedProviderDiscoveryUi, colors: NaviampColors, actions: NaviampHomeActions, television: Boolean = false) {
    if (!discovery.requestsOpen) return
    NaviampDialog(onDismissRequest = actions.onDiscoveryRequestsDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val closeFocus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { closeFocus.requestFocus() }
        val navigation = Modifier.onPreviewKeyEvent { event ->
            if (!television || event.type != KeyEventType.KeyDown) false else {
                val direction = when (event.key) {
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                direction?.let(focusManager::moveFocus) ?: false
            }
        }
        Surface(Modifier.then(navigation).widthIn(max = 1080.dp).fillMaxWidth().fillMaxHeight(0.9f).padding(12.dp), color = colors.controlSurface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.discovery_requests_title), color = colors.primaryText, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = actions.onDiscoveryRequestsDismiss, modifier = Modifier.focusRequester(closeFocus)) { Text(stringResource(Res.string.common_close)) }
                    TextButton(onClick = actions.onDiscoveryRequestsRefresh, enabled = !discovery.requestsLoading) { Text(stringResource(Res.string.library_refresh)) }
                    TextButton(onClick = { actions.onDiscoveryRequestsExternal(discovery.sourceId, discovery.sourceGeneration) }) {
                        Text(stringResource(Res.string.discovery_open_provider, localizedProviderName(discovery.providerId)))
                    }
                }
                if (discovery.requestsLoading) Text(stringResource(Res.string.library_refreshing), color = colors.secondaryText)
                if (discovery.requestsStatus != null && discovery.requestsStatus != ProviderDiscoveryStatus.Ready)
                    Text(stringResource(Res.string.discovery_requests_stale), color = colors.secondaryText)
                val activity = discovery.requestActivity.items.filter { it.releaseGroupMbid != null || it.identity?.managedAlbumId != null || it.kind in setOf("album_requested", "track_download", "album_grab") }
                if (discovery.acquisitions.isEmpty() && activity.isEmpty() && !discovery.requestsLoading)
                    Text(stringResource(Res.string.discovery_requests_empty), color = colors.secondaryText)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(discovery.acquisitions, key = { "local:${it.id}" }) { record ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.trackTitle ?: record.releaseTitle ?: record.artistName, color = colors.primaryText)
                            Text(record.artistName, color = colors.secondaryText)
                            DiscoveryAcquisitionResult(record, colors)
                            TextButton(onClick = { actions.onDiscoveryRequestSelected(discovery.sourceId, discovery.sourceGeneration, record.id) }) {
                                Text(stringResource(Res.string.discovery_view_artist))
                            }
                        }
                    }
                    if (activity.isNotEmpty()) item { Text(stringResource(Res.string.discovery_requests_activity), color = colors.primaryText,
                        modifier = Modifier.semantics { heading() }) }
                    items(activity, key = { "remote:${it.id}" }) { item ->
                        Column {
                            Text(item.title ?: item.releaseGroupMbid?.value ?: stringResource(Res.string.common_unknown), color = colors.primaryText)
                            item.artistName?.let { Text(it, color = colors.secondaryText) }
                            Text(stringResource(if (acquisitionPhase(item.status) == DiscoveryAcquisitionPhase.Complete)
                                Res.string.discovery_activity_complete else acquisitionPhaseMessage(acquisitionPhase(item.status))), color = colors.secondaryText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryAcquisitionResult(record: DiscoveryAcquisitionRecord, colors: NaviampColors) {
    if (record.phase == DiscoveryAcquisitionPhase.WaitingForRelease)
        DiscoveryOperationResult(record.operation.copy(status = ProviderDiscoveryOperationStatus.WaitingForRelease), colors)
    else Text(stringResource(acquisitionPhaseMessage(record.phase)), color = colors.secondaryText)
}

internal fun acquisitionPhaseMessage(phase: DiscoveryAcquisitionPhase) = when (phase) {
    DiscoveryAcquisitionPhase.Saved -> Res.string.discovery_request_saved
    DiscoveryAcquisitionPhase.MissingSource -> Res.string.discovery_request_missing_source
    DiscoveryAcquisitionPhase.WaitingForRelease -> Res.string.discovery_waiting_for_release
    DiscoveryAcquisitionPhase.Queued -> Res.string.discovery_request_queued
    DiscoveryAcquisitionPhase.Searching -> Res.string.search_searching
    DiscoveryAcquisitionPhase.Processing -> Res.string.discovery_acquisition_processing
    DiscoveryAcquisitionPhase.Downloading -> Res.string.discovery_acquisition_downloading
    DiscoveryAcquisitionPhase.Importing -> Res.string.discovery_acquisition_importing
    DiscoveryAcquisitionPhase.Partial -> Res.string.discovery_release_partial
    DiscoveryAcquisitionPhase.Complete -> Res.string.discovery_acquisition_complete
    DiscoveryAcquisitionPhase.Available -> Res.string.discovery_request_owned
    DiscoveryAcquisitionPhase.Blocked -> Res.string.discovery_acquisition_blocked
    DiscoveryAcquisitionPhase.Failed -> Res.string.discovery_acquisition_failed
    DiscoveryAcquisitionPhase.Cancelled -> Res.string.discovery_acquisition_cancelled
    DiscoveryAcquisitionPhase.Uncertain -> Res.string.discovery_action_uncertain
    DiscoveryAcquisitionPhase.Conflict -> Res.string.discovery_request_conflict
    DiscoveryAcquisitionPhase.Rejected -> Res.string.discovery_request_rejected
    DiscoveryAcquisitionPhase.Unknown -> Res.string.common_unknown
}
