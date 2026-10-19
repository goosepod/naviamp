package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
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
    val firstResultFocus = remember { FocusRequester() }
    val duplicateContexts = remember(references) {
        references.groupingBy { Triple(it.name.lowercase(), it.owner, it.isSmart) }.eachCount()
    }
    fun needsId(reference: SmartPlaylistReference): Boolean =
        duplicateContexts[Triple(reference.name.lowercase(), reference.owner, reference.isSmart)]?.let { it > 1 } == true
    val selected = references.firstOrNull { it.id == value }
    TextButton(modifier = Modifier.testTag("smart-playlist-reference-open"), onClick = { query = ""; open = true }) {
        Text(selected?.name ?: stringResource(Res.string.smart_playlist_reference_choose))
    }
    if (selected == null && value.isNotBlank()) Text(value, color = colors.secondaryText)
    selected?.let {
        val context = referenceContext(it, needsId(it))
        if (context.isNotBlank()) Text(context, color = colors.secondaryText)
    }
    Text(stringResource(Res.string.smart_playlist_reference_owner_access), color = colors.secondaryText)
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
                    val matches = smartPlaylistReferenceMatches(references, query, currentPlaylistId)
                    fun focusFirstResult(): Boolean = !loading && !failed && matches.isNotEmpty() && firstResultFocus.requestFocus()
                    SmartPlaylistOutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = stringResource(Res.string.smart_playlist_reference_search),
                        colors = colors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusFirstResult() }),
                        modifier = Modifier.fillMaxWidth().testTag("smart-playlist-reference-search").naviampTextInputFocus()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) false
                                else when {
                                    event.key == Key.DirectionDown || (event.key == Key.Tab && !event.isShiftPressed) -> focusFirstResult()
                                    event.key == Key.Escape -> { open = false; true }
                                    else -> false
                                }
                            },
                    )
                    when {
                        loading -> Text(stringResource(Res.string.smart_playlist_reference_loading))
                        failed -> {
                            Text(stringResource(Res.string.smart_playlist_reference_failed),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.smart_playlist_reference_retry)) }
                        }
                        else -> {
                            if (matches.isEmpty()) Text(stringResource(Res.string.smart_playlist_reference_empty))
                            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                                matches.forEachIndexed { index, reference ->
                                    TextButton(modifier = Modifier.fillMaxWidth().testTag("smart-playlist-reference-${reference.id}")
                                        .focusProperties { canFocus = true }
                                        .then(if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier),
                                        onClick = { onSelected(reference.id); open = false }) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(reference.name.ifBlank { reference.id }, color = colors.primaryText)
                                            val context = referenceContext(reference, needsId(reference))
                                            if (context.isNotBlank()) Text(context, color = colors.secondaryText)
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
private fun referenceContext(reference: SmartPlaylistReference, includeId: Boolean): String = listOfNotNull(
    reference.owner?.takeIf { it.isNotBlank() },
    if (reference.isSmart) stringResource(Res.string.playlists_smart) else null,
    reference.id.takeIf { includeId },
).joinToString(" · ")
