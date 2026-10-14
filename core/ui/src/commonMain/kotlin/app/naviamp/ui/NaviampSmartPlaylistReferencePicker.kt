package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.naviamp.domain.smartplaylist.SmartPlaylistReference
import app.naviamp.domain.smartplaylist.smartPlaylistReferenceMatches
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

suspend fun smartPlaylistSelfReferenceMessage(): String = getString(Res.string.smart_playlist_reference_self)

@Composable
internal fun SmartPlaylistReferencePicker(
    value: String,
    references: List<SmartPlaylistReference>,
    loading: Boolean,
    failed: Boolean,
    currentPlaylistId: String?,
    onRetry: () -> Unit,
    colors: NaviampColors,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = references.firstOrNull { it.id == value }
    TextButton(modifier = Modifier.testTag("smart-playlist-reference-open"), onClick = { query = ""; open = true }) {
        Text(selected?.name ?: stringResource(Res.string.smart_playlist_reference_choose))
    }
    if (value.isNotBlank()) Text(selected?.let { referenceContext(it) } ?: value, color = colors.secondaryText)
    if (!loading && !failed && value.isNotBlank() && selected == null) {
        Text(stringResource(Res.string.smart_playlist_reference_missing, value), color = colors.secondaryText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (open) {
        NaviampPopupPresence()
        NaviampAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(Res.string.smart_playlist_reference_choose)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(Res.string.smart_playlist_reference_search)) },
                        colors = naviampOutlinedTextFieldColors(colors),
                        modifier = Modifier.fillMaxWidth().testTag("smart-playlist-reference-search").naviampTextInputFocus(),
                    )
                    when {
                        loading -> Text(stringResource(Res.string.smart_playlist_reference_loading))
                        failed -> {
                            Text(stringResource(Res.string.smart_playlist_reference_failed),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.smart_playlist_reference_retry)) }
                        }
                        else -> {
                            val matches = smartPlaylistReferenceMatches(references, query, currentPlaylistId)
                            if (matches.isEmpty()) Text(stringResource(Res.string.smart_playlist_reference_empty))
                            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                                matches.forEach { reference ->
                                    TextButton(modifier = Modifier.fillMaxWidth().testTag("smart-playlist-reference-${reference.id}"),
                                        onClick = { onSelected(reference.id); open = false }) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(reference.name, color = colors.primaryText)
                                            Text(referenceContext(reference), color = colors.secondaryText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.common_cancel)) } },
            containerColor = colors.controlSurface,
            titleContentColor = colors.primaryText,
            textContentColor = colors.secondaryText,
        )
    }
}

@Composable
private fun referenceContext(reference: SmartPlaylistReference): String = listOfNotNull(
    reference.owner?.takeIf { it.isNotBlank() },
    if (reference.isSmart) stringResource(Res.string.playlists_smart) else null,
    reference.id,
).joinToString(" · ")
