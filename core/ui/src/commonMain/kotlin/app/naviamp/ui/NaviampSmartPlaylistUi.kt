package app.naviamp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import app.naviamp.domain.library.LibrarySyncProgress
import app.naviamp.domain.library.LibrarySyncProgressPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistReference
import app.naviamp.domain.smartplaylist.smartPlaylistReferenceMatches
import app.naviamp.domain.smartplaylist.referencesPlaylist
import app.naviamp.domain.smartplaylist.selectOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistConditionDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import app.naviamp.domain.smartplaylist.inspectSmartPlaylistJson
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldCatalog
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldOption
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistGroupDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistGenreOption
import app.naviamp.domain.smartplaylist.SmartPlaylistGenreSelection
import app.naviamp.domain.smartplaylist.SmartPlaylistLimitMode
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistPreview
import app.naviamp.domain.smartplaylist.SmartPlaylistRefreshDelay
import app.naviamp.domain.smartplaylist.SmartPlaylistSortDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistTemplates
import app.naviamp.domain.smartplaylist.SmartPlaylistValueType
import app.naviamp.domain.smartplaylist.displayLabel
import app.naviamp.domain.smartplaylist.smartPlaylistGenreSuggestions
import app.naviamp.domain.smartplaylist.updated
import app.naviamp.domain.smartplaylist.usesCalendarDate
import app.naviamp.domain.smartplaylist.valueLabel
import app.naviamp.domain.smartplaylist.validate
import app.naviamp.domain.smartplaylist.SmartPlaylistValidationIssue
import app.naviamp.domain.smartplaylist.SmartPlaylistIssueCode
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource

@Composable
fun SmartPlaylistBuilderDialog(
    colors: NaviampColors,
    initialDraft: SmartPlaylistDraft = SmartPlaylistDraft(),
    title: String = "Smart playlist",
    saveLabel: String = "Save",
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
    genreCatalog: List<SmartPlaylistGenreOption> = emptyList(),
    currentPlaylistId: String? = null,
    onReferences: suspend () -> List<SmartPlaylistReference> = { emptyList() },
    onPreview: suspend (SmartPlaylistDefinition) -> SmartPlaylistPreview = {
        SmartPlaylistPreview(message = "Preview is not available for this connection.")
    },
    onSyncLibrary: NaviampSmartPlaylistSync? = null,
    onDismissRequest: () -> Unit,
    onSave: suspend (SmartPlaylistDefinition) -> Unit,
    onSaveWithPassword: (suspend (SmartPlaylistDefinition, String) -> Unit)? = null,
) {
    var draft by remember(initialDraft, selectedConnectionLibraryIds) {
        mutableStateOf(
            if (initialDraft.selectedLibraryIds == null && selectedConnectionLibraryIds.size > 1) {
                initialDraft.copy(selectedLibraryIds = selectedConnectionLibraryIds)
            } else {
                initialDraft
            },
        )
    }
    var inspection by remember { mutableStateOf<SmartPlaylistEditingResult?>(null) }
    val unsupportedImportMessage = stringResource(Res.string.smart_playlist_unsupported_import)
    var importJson by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var passwordPromptOpen by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordSaving by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingPasswordDefinition by remember { mutableStateOf<SmartPlaylistDefinition?>(null) }
    var livePreview by remember { mutableStateOf<SmartPlaylistPreview?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var librarySyncing by remember { mutableStateOf(false) }
    var librarySyncProgress by remember { mutableStateOf<LibrarySyncProgress?>(null) }
    var librarySyncJob by remember { mutableStateOf<Job?>(null) }
    var librarySyncStopped by remember { mutableStateOf(false) }
    var librarySyncFailed by remember { mutableStateOf(false) }
    var previewRevision by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var references by remember { mutableStateOf<List<SmartPlaylistReference>>(emptyList()) }
    var referencesLoading by remember { mutableStateOf(true) }
    var referencesFailed by remember { mutableStateOf(false) }
    var referencesRevision by remember { mutableStateOf(0) }
    LaunchedEffect(onReferences, referencesRevision) {
        referencesLoading = true
        referencesFailed = false
        try {
            val loaded = onReferences()
            currentCoroutineContext().ensureActive()
            references = loaded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            referencesFailed = true
        } finally {
            referencesLoading = false
        }
    }
    val selfReferenceMessage = stringResource(Res.string.smart_playlist_reference_self)
    val definition = remember(draft, currentPlaylistId, selfReferenceMessage) {
        runCatching {
            draft.toDefinition().also {
                require(currentPlaylistId == null || !it.referencesPlaylist(currentPlaylistId)) { selfReferenceMessage }
            }
        }
    }
    val validationIssues = remember(draft) { draft.validate() }
    val validationMessage = stringResource(Res.string.smart_playlist_validation_fix)
    val jsonPreview = definition.getOrNull()?.toNspJson() ?: validationMessage

    LaunchedEffect(draft, librarySyncing, previewRevision) {
        livePreview = null
        previewLoading = false
        if (librarySyncing) return@LaunchedEffect
        val definitionToPreview = definition.getOrNull() ?: return@LaunchedEffect
        delay(SmartPlaylistPreviewDebounceMillis)
        previewLoading = true
        val result = runCatching { onPreview(definitionToPreview) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                SmartPlaylistPreview(message = error.message ?: "Could not preview this smart playlist.")
            }
        currentCoroutineContext().ensureActive()
        livePreview = result
        previewLoading = false
    }

    NaviampPopupPresence()

    NaviampAlertDialog(
        onDismissRequest = { librarySyncJob?.cancel(); onDismissRequest() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("smart-playlist-editor-scroll"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmartPlaylistSection(
                    title = "Details",
                    colors = colors,
                ) {
                    SmartPlaylistTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = "Name",
                        colors = colors,
                    )
                    SmartPlaylistTextField(
                        value = draft.comment,
                        onValueChange = { draft = draft.copy(comment = it) },
                        label = "Comment",
                        colors = colors,
                    )
                }
                SmartPlaylistSection(
                    title = "Templates",
                    colors = colors,
                ) {
                    SmartPlaylistTemplates.recommended.forEach { template ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(template.title, color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(template.description, color = colors.secondaryText, fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    draft = SmartPlaylistDraft.fromDefinition(template.definition)
                                    saveMessage = null
                                    importMessage = null
                                },
                            ) {
                                Text("Use", color = colors.accent)
                            }
                        }
                    }
                }
                SmartPlaylistCustomControls(
                    colors = colors,
                    draft = draft,
                    genreCatalog = genreCatalog,
                    validationIssues = validationIssues,
                    references = references,
                    referencesLoading = referencesLoading,
                    referencesFailed = referencesFailed,
                    currentPlaylistId = currentPlaylistId,
                    onReferencesRetry = { referencesRevision++ },
                    onDraftChange = { draft = it },
                )
                TextButton(modifier = Modifier.testTag("smart-playlist-options-toggle"), onClick = { optionsOpen = !optionsOpen }) {
                    Text(stringResource(Res.string.smart_playlist_options))
                }
                if (optionsOpen) SmartPlaylistOptionsControls(colors, draft, availableLibraries, selectedConnectionLibraryIds) { draft = it }
                definition.getOrNull()?.let { SmartPlaylistSummarySection(it, colors, references, availableLibraries) }
                validationIssues.filter {
                    it.location.conditionId == null && it.location.groupId == null &&
                        (it.code != SmartPlaylistIssueCode.InvalidRefreshDelay || !optionsOpen)
                }.forEach {
                    Text(smartPlaylistIssueMessage(it), color = colors.favorite, fontSize = 12.sp)
                }
                if (definition.exceptionOrNull()?.message == selfReferenceMessage) {
                    Text(selfReferenceMessage, color = colors.favorite,
                        modifier = Modifier.semantics { error(selfReferenceMessage); liveRegion = LiveRegionMode.Polite })
                }
                saveMessage?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
                if (onSyncLibrary != null) {
                    Text(stringResource(Res.string.smart_playlist_sync_description), color = colors.secondaryText, fontSize = 12.sp)
                    TextButton(
                        modifier = Modifier.testTag("smart-playlist-sync-library"),
                        enabled = !librarySyncing,
                        onClick = {
                            if (!librarySyncing) {
                                librarySyncing = true
                                librarySyncFailed = false
                                librarySyncStopped = false
                                librarySyncProgress = null
                                librarySyncJob = coroutineScope.launch {
                                    try {
                                        val editorContext = currentCoroutineContext()
                                        onSyncLibrary { progress ->
                                            withContext(editorContext) { librarySyncProgress = progress }
                                        }
                                        currentCoroutineContext().ensureActive()
                                        previewRevision++
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        librarySyncFailed = true
                                    } finally {
                                        librarySyncing = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(if (librarySyncing) Res.string.smart_playlist_sync_running else Res.string.smart_playlist_sync_library))
                    }
                    if (librarySyncing) {
                        val progress = librarySyncProgress
                        val count = progress?.trackCount ?: 0
                        val total = progress?.total?.takeIf { it > 0 }
                        val label = when {
                            progress?.phase == LibrarySyncProgressPhase.LoadingAlbums ->
                                stringResource(Res.string.smart_playlist_sync_albums, progress.albumCount)
                            total != null -> stringResource(Res.string.smart_playlist_sync_tracks_total, count, total)
                            else -> stringResource(Res.string.smart_playlist_sync_tracks, count)
                        }
                        Column(Modifier.fillMaxWidth().testTag("smart-playlist-sync-progress")
                            .semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(label, color = colors.secondaryText, fontSize = 12.sp)
                            if (total != null) {
                                LinearProgressIndicator(progress = { (count.toFloat() / total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(), color = colors.accent,
                                    trackColor = colors.mutedText.copy(alpha = 0.25f))
                            }
                            TextButton(onClick = {
                                librarySyncStopped = true
                                librarySyncJob?.cancel()
                            }, modifier = Modifier.testTag("smart-playlist-stop-sync")) {
                                Text(stringResource(Res.string.smart_playlist_sync_stop))
                            }
                        }
                    }
                    if (librarySyncStopped && !librarySyncing) {
                        Text(stringResource(Res.string.smart_playlist_sync_stopped), color = colors.secondaryText, fontSize = 12.sp)
                    }
                    if (librarySyncFailed) {
                        Text(
                            stringResource(Res.string.smart_playlist_sync_failed),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.favorite, fontSize = 12.sp,
                        )
                    }
                }
                SmartPlaylistPreviewSection(
                    colors = colors,
                    preview = livePreview.takeIf { definition.isSuccess },
                    loading = previewLoading || librarySyncing,
                    invalid = definition.isFailure,
                )
                TextButton(modifier = Modifier.testTag("smart-playlist-advanced-toggle"), onClick = { advancedOpen = !advancedOpen }) {
                    Text(stringResource(Res.string.smart_playlist_advanced))
                }
                if (advancedOpen) {
                    SmartPlaylistSection(
                        title = "Import",
                        colors = colors,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { importOpen = !importOpen }) {
                                Text(if (importOpen) "Hide import" else "Import JSON", color = colors.accent)
                            }
                            importMessage?.let { message ->
                                Text(message, color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                        if (importOpen) {
                            OutlinedTextField(
                                colors = naviampOutlinedTextFieldColors(colors),
                                value = importJson,
                                onValueChange = {
                                    importJson = it
                                    importMessage = null
                                },
                                label = { Text("Paste .nsp or Navidrome smart playlist JSON", color = colors.secondaryText) },
                                minLines = 4,
                                maxLines = 8,
                                textStyle = TextStyle(fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth().naviampTextInputFocus(),
                            )
                            TextButton(
                                enabled = importJson.isNotBlank(),
                                onClick = {
                                    runCatching {
                                        inspectSmartPlaylistJson(importJson)
                                    }.onSuccess { imported ->
                                        if (imported !is SmartPlaylistEditingResult.Editable) {
                                            inspection = imported
                                            return@onSuccess
                                        }
                                        draft = SmartPlaylistDraft.fromDefinition(imported.definition)
                                        importMessage = "Imported ${imported.definition.name}."
                                        importOpen = false
                                    }.onFailure {
                                        importMessage = unsupportedImportMessage
                                    }
                                },
                            ) {
                                Text("Validate and load", color = colors.accent)
                            }
                        }
                    }
                    SmartPlaylistSection(
                        title = "Generated JSON",
                        colors = colors,
                    ) {
                        OutlinedTextField(
                            colors = naviampOutlinedTextFieldColors(colors),
                            value = jsonPreview,
                            onValueChange = {},
                            readOnly = true,
                            minLines = 6,
                            maxLines = 10,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(SmartPlaylistSaveTestTag),
                enabled = definition.isSuccess && !saving,
                onClick = {
                    val smartPlaylist = definition.getOrThrow()
                    saving = true
                    saveMessage = "Saving ${smartPlaylist.name}..."
                    coroutineScope.launch {
                        runCatching {
                            onSave(smartPlaylist)
                        }.onSuccess {
                            saving = false
                            saveMessage = "Saved ${smartPlaylist.name}."
                            onDismissRequest()
                        }.onFailure { error ->
                            saving = false
                            if (onSaveWithPassword != null && error.requiresSmartPlaylistPassword()) {
                                pendingPasswordDefinition = smartPlaylist
                                password = ""
                                passwordError = null
                                passwordPromptOpen = true
                                saveMessage = "Enter your Navidrome password to enable smart playlist saving."
                            } else {
                                saveMessage = error.message ?: "Could not save smart playlist."
                            }
                        }
                    }
                },
            ) {
                Text(if (saving) "Saving..." else saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
    inspection?.let { editing ->
        SmartPlaylistInspectionDialog(editing, colors) { inspection = null }
    }
    if (passwordPromptOpen) {
        val pendingDefinition = pendingPasswordDefinition
        NaviampPopupPresence()
        NaviampAlertDialog(
            onDismissRequest = {
                if (!passwordSaving) {
                    passwordPromptOpen = false
                    pendingPasswordDefinition = null
                    password = ""
                    passwordError = null
                }
            },
            title = { Text("Navidrome password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Smart playlist changes need a Navidrome API token. Enter your password once and Naviamp will save the token for future edits.",
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        colors = naviampOutlinedTextFieldColors(colors),
                        modifier = Modifier.fillMaxWidth().testTag(SmartPlaylistPasswordFieldTestTag).naviampTextInputFocus(),
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text("Password", color = colors.secondaryText) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = TextStyle(fontSize = 13.sp),
                    )
                    passwordError?.let { message ->
                        Text(message, color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(SmartPlaylistPasswordSaveTestTag),
                    enabled = pendingDefinition != null && password.isNotBlank() && !passwordSaving,
                    onClick = {
                        val definitionToSave = pendingDefinition ?: return@TextButton
                        val passwordToUse = password
                        passwordSaving = true
                        passwordError = null
                        coroutineScope.launch {
                            runCatching {
                                onSaveWithPassword?.invoke(definitionToSave, passwordToUse)
                            }.onSuccess {
                                passwordSaving = false
                                passwordPromptOpen = false
                                pendingPasswordDefinition = null
                                password = ""
                                passwordError = null
                                onDismissRequest()
                            }.onFailure { error ->
                                passwordSaving = false
                                passwordError = error.message ?: "Could not authenticate with Navidrome."
                            }
                        }
                    },
                ) {
                    Text(if (passwordSaving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !passwordSaving,
                    onClick = {
                        passwordPromptOpen = false
                        pendingPasswordDefinition = null
                        password = ""
                        passwordError = null
                    },
                ) {
                    Text("Cancel")
                }
            },
            containerColor = colors.controlSurface,
            titleContentColor = colors.primaryText,
            textContentColor = colors.secondaryText,
        )
    }
}

internal const val SmartPlaylistSaveTestTag = "smart-playlist-save"
internal const val SmartPlaylistPasswordFieldTestTag = "smart-playlist-password"
internal const val SmartPlaylistPasswordSaveTestTag = "smart-playlist-password-save"
internal const val SmartPlaylistLoadPasswordFieldTestTag = "smart-playlist-load-password"
internal const val SmartPlaylistLoadPasswordConfirmTestTag = "smart-playlist-load-password-confirm"
internal const val SmartPlaylistGenreValueTestTag = "smart-playlist-genre-value"
internal const val SmartPlaylistPreviewTestTag = "smart-playlist-preview"
private const val SmartPlaylistPreviewDebounceMillis = 350L

@Composable
internal fun SmartPlaylistLoadPasswordDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
    onInspectWithPassword: suspend (SharedMediaItemUi, String) -> SmartPlaylistEditingResult,
    onReadOnly: (SmartPlaylistEditingResult) -> Unit,
    onLoaded: (SmartPlaylistDefinition) -> Unit,
) {
    val unsupportedImportMessage = stringResource(Res.string.smart_playlist_unsupported_import)
    var password by remember(playlist.id) { mutableStateOf("") }
    var loading by remember(playlist.id) { mutableStateOf(false) }
    var errorMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    NaviampPopupPresence()

    NaviampAlertDialog(
        onDismissRequest = { if (!loading) onDismissRequest() },
        title = { Text("Navidrome password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Editing this smart playlist needs a refreshed Navidrome API token. Enter your password once and Naviamp will save the new token.",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    colors = naviampOutlinedTextFieldColors(colors),
                    modifier = Modifier.fillMaxWidth().testTag(SmartPlaylistLoadPasswordFieldTestTag).naviampTextInputFocus(),
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password", color = colors.secondaryText) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(fontSize = 13.sp),
                )
                errorMessage?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(SmartPlaylistLoadPasswordConfirmTestTag),
                enabled = password.isNotBlank() && !loading,
                onClick = {
                    val passwordToUse = password
                    loading = true
                    errorMessage = null
                    coroutineScope.launch {
                        runCatching { onInspectWithPassword(playlist, passwordToUse) }
                            .onSuccess { editing ->
                                loading = false
                                if (editing is SmartPlaylistEditingResult.Editable) onLoaded(editing.definition)
                                else onReadOnly(editing)
                            }
                            .onFailure { error ->
                                loading = false
                                errorMessage = if (error is IllegalArgumentException) unsupportedImportMessage
                                    else error.message ?: "Could not authenticate with Navidrome."
                            }
                    }
                },
            ) {
                Text(if (loading) "Loading..." else "Continue")
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
private fun SmartPlaylistCustomControls(
    colors: NaviampColors,
    draft: SmartPlaylistDraft,
    genreCatalog: List<SmartPlaylistGenreOption>,
    validationIssues: List<SmartPlaylistValidationIssue>,
    references: List<SmartPlaylistReference>,
    referencesLoading: Boolean,
    referencesFailed: Boolean,
    currentPlaylistId: String?,
    onReferencesRetry: () -> Unit,

    onDraftChange: (SmartPlaylistDraft) -> Unit,
) {
    SmartPlaylistSection(title = "Filters", colors = colors) {
        SmartPlaylistDropdown(
            label = "Match",
            value = when (draft.match) {
                SmartPlaylistMatch.All -> "All rules"
                SmartPlaylistMatch.Any -> "Any rule"
            },
            colors = colors,
            options = listOf("All rules" to SmartPlaylistMatch.All, "Any rule" to SmartPlaylistMatch.Any),
            onSelected = { onDraftChange(draft.copy(match = it)) },
        )
        draft.conditions.forEachIndexed { index, condition ->
            SmartPlaylistRuleControls(
                title = "Filter ${index + 1}",
                colors = colors,
                condition = condition,
                genreCatalog = genreCatalog,
                references = references,
                referencesLoading = referencesLoading,
                referencesFailed = referencesFailed,
                currentPlaylistId = currentPlaylistId,
                onReferencesRetry = onReferencesRetry,
                issues = validationIssues.filter { it.location.conditionId == condition.id },
                onConditionChange = { updatedCondition ->
                    onDraftChange(draft.copy(conditions = draft.conditions.updated(index, updatedCondition.copy(isStarter = false))))
                },
                onRemove = if (draft.conditions.size > 1 || draft.groups.isNotEmpty()) {
                    { onDraftChange(draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })) }
                } else {
                    null
                },
            )
        }
        TextButton(
            onClick = { onDraftChange(draft.copy(conditions = draft.conditions + SmartPlaylistConditionDraft())) },
        ) {
            Text("Add filter", color = colors.accent)
        }
    }

    SmartPlaylistSection(title = "Groups", colors = colors) {
        draft.groups.forEachIndexed { groupIndex, group ->
            SmartPlaylistNestedSection(
                title = "Group ${groupIndex + 1}",
                colors = colors,
                trailing = {
                    TextButton(
                        onClick = { onDraftChange(draft.copy(groups = draft.groups.filterIndexed { i, _ -> i != groupIndex })) },
                    ) {
                        Text("Remove", color = colors.secondaryText, fontSize = 12.sp)
                    }
                },
            ) {
                validationIssues.filter { it.location.groupId == group.id && it.location.conditionId == null }.forEach {
                    Text(smartPlaylistIssueMessage(it), color = colors.favorite, fontSize = 12.sp)
                }
                SmartPlaylistDropdown(
                    label = "Match",
                    value = when (group.match) {
                        SmartPlaylistMatch.All -> "All group rules"
                        SmartPlaylistMatch.Any -> "Any group rule"
                    },
                    colors = colors,
                    options = listOf("All group rules" to SmartPlaylistMatch.All, "Any group rule" to SmartPlaylistMatch.Any),
                    onSelected = { match ->
                        onDraftChange(draft.copy(groups = draft.groups.updated(groupIndex, group.copy(match = match))))
                    },
                )
                group.conditions.forEachIndexed { conditionIndex, condition ->
                    SmartPlaylistRuleControls(
                        title = "Filter ${conditionIndex + 1}",
                        colors = colors,
                        condition = condition,
                        genreCatalog = genreCatalog,
                references = references,
                referencesLoading = referencesLoading,
                referencesFailed = referencesFailed,
                currentPlaylistId = currentPlaylistId,
                onReferencesRetry = onReferencesRetry,
                        issues = validationIssues.filter { it.location.conditionId == condition.id },
                        onConditionChange = { updatedCondition ->
                            onDraftChange(
                                draft.copy(
                                    groups = draft.groups.updated(
                                        groupIndex,
                                        group.copy(conditions = group.conditions.updated(conditionIndex, updatedCondition.copy(isStarter = false))),
                                    ),
                                ),
                            )
                        },
                        onRemove = if (group.conditions.size > 1) {
                            {
                                onDraftChange(
                                    draft.copy(
                                        groups = draft.groups.updated(
                                            groupIndex,
                                            group.copy(conditions = group.conditions.filterIndexed { i, _ -> i != conditionIndex }),
                                        ),
                                    ),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                TextButton(
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                groups = draft.groups.updated(
                                    groupIndex,
                                    group.copy(conditions = group.conditions + SmartPlaylistConditionDraft()),
                                ),
                            ),
                        )
                    },
                ) {
                    Text("Add group filter", color = colors.accent)
                }
            }
        }
        TextButton(
            onClick = { onDraftChange(draft.copy(groups = draft.groups + SmartPlaylistGroupDraft())) },
        ) {
            Text("Add group", color = colors.accent)
        }
    }

    SmartPlaylistSection(title = "Sort and Limit", colors = colors) {
        draft.sort.forEachIndexed { index, sort ->
            SmartPlaylistNestedSection(
                title = "Sort ${index + 1}",
                colors = colors,
                trailing = {
                    TextButton(
                        onClick = { onDraftChange(draft.copy(sort = draft.sort.filterIndexed { i, _ -> i != index })) },
                    ) {
                        Text("Remove", color = colors.secondaryText, fontSize = 12.sp)
                    }
                },
            ) {
                SmartPlaylistDropdown(
                    label = "Field",
                    value = smartPlaylistFieldLabel(sort.field),
                    colors = colors,
                    options = SmartPlaylistFieldCatalog.sortableFields
                        .smartPlaylistMenuOrder(CommonSmartPlaylistSortFields)
                        .map { smartPlaylistFieldLabel(it) to it },
                    onSelected = { field ->
                        onDraftChange(draft.copy(sort = draft.sort.updated(index, sort.copy(field = field))))
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sort.descending,
                        onCheckedChange = { checked ->
                            onDraftChange(draft.copy(sort = draft.sort.updated(index, sort.copy(descending = checked))))
                        },
                    )
                    Text("Descending", color = colors.secondaryText, fontSize = 12.sp)
                }
            }
        }
        TextButton(
            onClick = { onDraftChange(draft.copy(sort = draft.sort + SmartPlaylistSortDraft())) },
        ) {
            Text("Add sort", color = colors.accent)
        }
        SmartPlaylistDropdown(
            label = "Limit",
            value = when (draft.limitMode) {
                SmartPlaylistLimitMode.Unlimited -> stringResource(Res.string.smart_playlist_limit_unlimited)
                SmartPlaylistLimitMode.TrackCount -> stringResource(Res.string.smart_playlist_limit_tracks)
                SmartPlaylistLimitMode.Percent -> stringResource(Res.string.smart_playlist_limit_percent)
            },
            colors = colors,
            options = listOf(
                stringResource(Res.string.smart_playlist_limit_unlimited) to SmartPlaylistLimitMode.Unlimited,
                stringResource(Res.string.smart_playlist_limit_tracks) to SmartPlaylistLimitMode.TrackCount,
                stringResource(Res.string.smart_playlist_limit_percent) to SmartPlaylistLimitMode.Percent,
            ),
            onSelected = { onDraftChange(draft.copy(limitMode = it)) },
        )
        if (draft.limitMode != SmartPlaylistLimitMode.Unlimited) SmartPlaylistTextField(
            value = draft.limit.toString(),
            onValueChange = { onDraftChange(draft.copy(limit = it.toIntOrNull() ?: draft.limit)) },
            label = if (draft.limitMode == SmartPlaylistLimitMode.Percent) "Limit percent" else "Track limit",
            colors = colors,
        )

    }
}


@Composable
private fun SmartPlaylistOptionsControls(
    colors: NaviampColors,
    draft: SmartPlaylistDraft,
    availableLibraries: List<ConnectionFormMusicFolder>,
    selectedConnectionLibraryIds: List<String>,
    onDraftChange: (SmartPlaylistDraft) -> Unit,
) {
    if (selectedConnectionLibraryIds.size > 1) {
        val selectedIds = draft.selectedLibraryIds.orEmpty().toSet()
        val libraries = selectedConnectionLibraryIds.map { id ->
            availableLibraries.firstOrNull { library -> library.id == id }
                ?: ConnectionFormMusicFolder(id = id, name = id)
        }
        SmartPlaylistSection(title = "Libraries", colors = colors) {
            Text(
                "Choose which of this connection's libraries the playlist can use.",
                color = colors.secondaryText,
                fontSize = 12.sp,
            )
            libraries.forEach { library ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = library.id in selectedIds,
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                selectedIds + library.id
                            } else {
                                selectedIds - library.id
                            }
                            if (updated.isNotEmpty()) {
                                onDraftChange(
                                    draft.copy(
                                        selectedLibraryIds = selectedConnectionLibraryIds.filter { it in updated },
                                    ),
                                )
                            }
                        },
                    )
                    Text(library.name, color = colors.primaryText, fontSize = 13.sp)
                }
            }
        }
    }
    SmartPlaylistSection(title = stringResource(Res.string.smart_playlist_options), colors = colors) {
        SmartPlaylistTextField(
            value = draft.refreshDelay,
            onValueChange = { onDraftChange(draft.copy(refreshDelay = it)) },
            label = stringResource(Res.string.smart_playlist_refresh_delay),
            colors = colors,
        )
        val refreshDelayValid = draft.refreshDelay.isBlank() ||
            SmartPlaylistRefreshDelay.isValid(draft.refreshDelay.trim())
        Text(
            stringResource(
                if (refreshDelayValid) {
                    Res.string.smart_playlist_refresh_delay_hint
                } else {
                    Res.string.smart_playlist_refresh_delay_invalid
                },
            ),
            color = if (refreshDelayValid) colors.secondaryText else colors.favorite,
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.isPublic,
                onCheckedChange = { onDraftChange(draft.copy(isPublic = it)) },
            )
            Text("Public playlist", color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmartPlaylistRuleControls(
    title: String,
    colors: NaviampColors,
    condition: SmartPlaylistConditionDraft,
    genreCatalog: List<SmartPlaylistGenreOption>,
    issues: List<SmartPlaylistValidationIssue>,
    references: List<SmartPlaylistReference>,
    referencesLoading: Boolean,
    referencesFailed: Boolean,
    currentPlaylistId: String?,
    onReferencesRetry: () -> Unit,

    onConditionChange: (SmartPlaylistConditionDraft) -> Unit,
    onRemove: (() -> Unit)?,
) {
    SmartPlaylistNestedSection(
        title = title,
        colors = colors,
        trailing = onRemove?.let { remove ->
            {
                TextButton(onClick = remove) {
                    Text("Remove", color = colors.secondaryText, fontSize = 12.sp)
                }
            }
        },
    ) {
        SmartPlaylistDropdown(
            label = "Field",
            value = smartPlaylistFieldLabel(condition.field),
            colors = colors,
            options = SmartPlaylistFieldCatalog.fields
                .smartPlaylistMenuOrder(CommonSmartPlaylistRuleFields)
                .map { smartPlaylistFieldLabel(it) to it },
            onSelected = { field ->
                onConditionChange(
                    condition.copy(
                        field = field,
                        operator = field.operators.first(),
                        value = "",
                        secondValue = "",
                        genreSelection = null,
                    ),
                )
            },
        )
        SmartPlaylistDropdown(
            label = "Operator",
            value = condition.operator.displayLabel(),
            colors = colors,
            options = condition.field.operators.map { it.displayLabel() to it },
            onSelected = { operator ->
                onConditionChange(
                    condition.selectOperator(operator),
                )
            },
        )
        SmartPlaylistConditionValueControls(
            colors = colors,
            condition = condition,
            genreCatalog = genreCatalog,
            references = references,
            referencesLoading = referencesLoading,
            referencesFailed = referencesFailed,
            currentPlaylistId = currentPlaylistId,
            onReferencesRetry = onReferencesRetry,
            onConditionChange = onConditionChange,
        )
        if (condition.operator == SmartPlaylistOperator.InTheRange) {
            SmartPlaylistOperandField(
                calendarDate = condition.usesCalendarDate(),
                value = condition.secondValue,
                onValueChange = { value -> onConditionChange(condition.copy(secondValue = value)) },
                label = stringResource(Res.string.smart_playlist_end_value),
                colors = colors,
            )
        }
        issues.forEach { issue ->
            val message = smartPlaylistIssueMessage(issue)
            Text(
                message, color = colors.favorite, fontSize = 12.sp,
                modifier = Modifier.testTag("smart-playlist-rule-error-${condition.id}")
                    .semantics { error(message); liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun smartPlaylistIssueMessage(issue: SmartPlaylistValidationIssue): String = stringResource(
    when (issue.code) {
        SmartPlaylistIssueCode.RequiredValue, SmartPlaylistIssueCode.RequiredName -> Res.string.smart_playlist_validation_required
        SmartPlaylistIssueCode.InvalidNumber -> Res.string.smart_playlist_validation_number
        SmartPlaylistIssueCode.InvalidDate -> Res.string.smart_playlist_validation_date
        SmartPlaylistIssueCode.InvalidDays -> Res.string.smart_playlist_validation_days
        SmartPlaylistIssueCode.ReversedRange -> Res.string.smart_playlist_validation_range
        SmartPlaylistIssueCode.OutOfBounds -> Res.string.smart_playlist_validation_bounds
        SmartPlaylistIssueCode.InvalidBoolean, SmartPlaylistIssueCode.InvalidChoice -> Res.string.smart_playlist_validation_choice
        SmartPlaylistIssueCode.UnsupportedOperator, SmartPlaylistIssueCode.InvalidSort -> Res.string.smart_playlist_validation_operation
        SmartPlaylistIssueCode.EmptyGroup, SmartPlaylistIssueCode.NoRules -> Res.string.smart_playlist_validation_rules
        SmartPlaylistIssueCode.InvalidLimit -> Res.string.smart_playlist_validation_limit
        SmartPlaylistIssueCode.InvalidRefreshDelay -> Res.string.smart_playlist_refresh_delay_invalid
    },
)

@Composable
private fun SmartPlaylistPreviewSection(
    colors: NaviampColors,
    preview: SmartPlaylistPreview?,
    loading: Boolean,
    invalid: Boolean,
) {
    SmartPlaylistSection(title = "Preview", colors = colors) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(SmartPlaylistPreviewTestTag),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            preview?.indexedTrackCount?.let { count ->
                Text(stringResource(Res.string.smart_playlist_preview_coverage, count), color = colors.secondaryText, fontSize = 12.sp)
            }
            when {
                invalid -> Text(stringResource(Res.string.smart_playlist_validation_fix), color = colors.favorite, fontSize = 12.sp)
                loading || preview == null -> Text(stringResource(Res.string.smart_playlist_preview_waiting), color = colors.secondaryText, fontSize = 12.sp)
                preview.available -> {
                    val matching = preview.matchingTrackCount ?: 0
                    val result = preview.resultTrackCount ?: matching
                    Text(
                        stringResource(Res.string.smart_playlist_preview_count, matching, result),
                        color = colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                    Text(stringResource(Res.string.smart_playlist_preview_samples), color = colors.secondaryText, fontSize = 12.sp)
                    if (preview.exampleTracks.isEmpty()) {
                        Text(stringResource(Res.string.smart_playlist_preview_empty), color = colors.secondaryText, fontSize = 12.sp)
                    }
                    if (preview.exampleTracks.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .testTag("smart-playlist-examples"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(preview.exampleTracks) { track ->
                                Text(stringResource(Res.string.smart_playlist_preview_track,
                                    track.title, track.artistName, track.albumTitle.orEmpty()),
                                    modifier = Modifier.fillMaxWidth().testTag("smart-playlist-example-${track.id.value}"),
                                    color = colors.primaryText, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                preview.indexedTrackCount == 0 && preview.unsupportedFields.isEmpty() ->
                    Text(stringResource(Res.string.smart_playlist_preview_no_index), color = colors.secondaryText, fontSize = 12.sp)
                preview.message != null -> preview.message?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
                else -> {
                    if (SmartPlaylistFields.LibraryId in preview.unsupportedFields) {
                        Text(stringResource(Res.string.smart_playlist_preview_library_metadata),
                            color = colors.secondaryText, fontSize = 12.sp)
                    }
                    val otherFields = preview.unsupportedFields.filter { it != SmartPlaylistFields.LibraryId }
                    if (otherFields.isNotEmpty()) {
                        val labels = otherFields.map { field ->
                            SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == field }
                                ?.let { smartPlaylistFieldLabel(it) } ?: field
                        }
                        Text(stringResource(Res.string.smart_playlist_preview_unsupported, labels.joinToString()),
                            color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistSection(
    title: String,
    colors: NaviampColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun SmartPlaylistNestedSection(
    title: String,
    colors: NaviampColors,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun SmartPlaylistConditionValueControls(
    colors: NaviampColors,
    condition: SmartPlaylistConditionDraft,
    genreCatalog: List<SmartPlaylistGenreOption>,
    references: List<SmartPlaylistReference>,
    referencesLoading: Boolean,
    referencesFailed: Boolean,
    currentPlaylistId: String?,
    onReferencesRetry: () -> Unit,
    onConditionChange: (SmartPlaylistConditionDraft) -> Unit,
) {
    if (condition.field.valueType == SmartPlaylistValueType.PlaylistId) {
        SmartPlaylistReferencePicker(condition.value, references, referencesLoading, referencesFailed,
            currentPlaylistId, onReferencesRetry, colors) { id -> onConditionChange(condition.copy(value = id)) }
        return
    }
    if (condition.field.valueType == SmartPlaylistValueType.Boolean ||
        condition.operator == SmartPlaylistOperator.IsMissing ||
        condition.operator == SmartPlaylistOperator.IsPresent
    ) {
        SmartPlaylistDropdown(
            label = condition.valueLabel(),
            value = when (condition.value.trim().lowercase()) {
                "true" -> "True"
                "false" -> "False"
                else -> "Choose"
            },
            colors = colors,
            options = listOf("True" to "true", "False" to "false"),
            onSelected = { value -> onConditionChange(condition.copy(value = value)) },
        )
        return
    }

    if (condition.field.field == SmartPlaylistFields.Genre) {
        SmartPlaylistGenreTypeahead(
            value = condition.value,
            onValueChange = { value ->
                onConditionChange(condition.copy(value = value, genreSelection = null))
            },
            onGenreSelected = { genre ->
                onConditionChange(
                    condition.copy(
                        value = genreDisplayTitle(genre.canonicalName),
                        genreSelection = SmartPlaylistGenreSelection(
                            canonicalName = genre.canonicalName,
                            libraryGenreNames = genre.libraryGenreNames,
                        ),
                    ),
                )
            },
            label = condition.valueLabel(),
            colors = colors,
            genres = genreCatalog,
        )
        condition.genreSelection?.let { selection ->
            val providerNames = selection.libraryGenreNames
            Text(
                when (providerNames.size) {
                    0 -> "Not currently in this library; saves as ${genreDisplayTitle(selection.canonicalName)}."
                    1 -> "Matches library tag: ${providerNames.single()}"
                    else -> "Matches library tags: ${providerNames.joinToString(" or ")}"
                },
                color = colors.secondaryText,
                fontSize = 11.sp,
            )
        }
    } else {
        SmartPlaylistOperandField(
            calendarDate = condition.usesCalendarDate(),
            value = condition.value,
            onValueChange = { value -> onConditionChange(condition.copy(value = value)) },
            label = condition.valueLabel(),
            colors = colors,
            relativeDays = condition.operator == SmartPlaylistOperator.InTheLast ||
                condition.operator == SmartPlaylistOperator.NotInTheLast,
        )
    }
}

@Composable
private fun SmartPlaylistGenreTypeahead(
    value: String,
    onValueChange: (String) -> Unit,
    onGenreSelected: (SmartPlaylistGenreOption) -> Unit,
    label: String,
    colors: NaviampColors,
    genres: List<SmartPlaylistGenreOption>,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = smartPlaylistGenreSuggestions(value, genres)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            colors = naviampOutlinedTextFieldColors(colors),
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.isNotBlank()
            },
            label = { Text(label, color = colors.secondaryText) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SmartPlaylistGenreValueTestTag)
                .naviampTextInputFocus()
                .onFocusChanged { focus ->
                    if (focus.isFocused && value.isNotBlank()) expanded = true
                },
        )
        NaviampDropdownMenu(
            expanded = expanded && value.isNotBlank(),
            onDismissRequest = { expanded = false },
            containerColor = colors.controlSurface,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            NaviampPopupPresence()
            if (suggestions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No matches", color = colors.secondaryText) },
                    onClick = {},
                    enabled = false,
                )
            }
            suggestions.forEach { genre ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(genreDisplayTitle(genre.canonicalName), color = colors.primaryText)
                            Text(
                                genre.trackCount?.let { count ->
                                    if (count == 1) "1 track" else "$count tracks"
                                } ?: "Not in library",
                                color = colors.secondaryText,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = {
                        onGenreSelected(genre)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
) {
    OutlinedTextField(
        colors = naviampOutlinedTextFieldColors(colors),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.secondaryText) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth().naviampTextInputFocus(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SmartPlaylistDropdown(
    label: String,
    value: String,
    colors: NaviampColors,
    options: List<Pair<String, T>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val normalizedQuery = query.trim().takeUnless { it.equals(value, ignoreCase = true) }.orEmpty()
    val visibleOptions = options
        .filter { (optionLabel, _) -> normalizedQuery.isBlank() || optionLabel.contains(normalizedQuery, ignoreCase = true) }
    // Keep the anchor gesture alive across focus/preview recompositions during a held press.
    val changeExpanded = remember(value) {
        { requested: Boolean ->
            expanded = requested
            if (!requested) query = value
        }
    }
    // Open on completed anchor activation, rather than text-input focus during mouse press.
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = changeExpanded,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            colors = naviampOutlinedTextFieldColors(colors),
            value = if (expanded) query else value,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label, color = colors.secondaryText) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .naviampTextInputFocus()
                .onPreviewKeyEvent { event ->
                    if (expanded && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        expanded = false
                        query = value
                        true
                    } else false
                },
        )
        NaviampDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = value
            },
            containerColor = colors.controlSurface,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            NaviampPopupPresence()
            if (visibleOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No matches", color = colors.secondaryText) },
                    onClick = {},
                    enabled = false,
                )
            }
            visibleOptions.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel, color = colors.primaryText) },
                    onClick = {
                        expanded = false
                        query = optionLabel
                        onSelected(optionValue)
                    },
                )
            }
        }
    }
}

private val CommonSmartPlaylistRuleFields = listOf(
    SmartPlaylistFields.Loved,
    SmartPlaylistFields.DateLoved,
    SmartPlaylistFields.Rating,
    SmartPlaylistFields.DateRated,
    SmartPlaylistFields.LastPlayed,
    SmartPlaylistFields.PlayCount,
    SmartPlaylistFields.DateAdded,
    SmartPlaylistFields.Artist,
    SmartPlaylistFields.Album,
    SmartPlaylistFields.Title,
    SmartPlaylistFields.Genre,
    SmartPlaylistFields.Year,
)

private val CommonSmartPlaylistSortFields = listOf(
    SmartPlaylistFields.DateLoved,
    SmartPlaylistFields.LastPlayed,
    SmartPlaylistFields.DateAdded,
    SmartPlaylistFields.Rating,
    SmartPlaylistFields.PlayCount,
    SmartPlaylistFields.Artist,
    SmartPlaylistFields.Album,
    SmartPlaylistFields.Title,
    SmartPlaylistFields.Genre,
    SmartPlaylistFields.Year,
    SmartPlaylistFields.Random,
)

private fun List<SmartPlaylistFieldOption>.smartPlaylistMenuOrder(commonFields: List<String>): List<SmartPlaylistFieldOption> =
    sortedWith(
        compareBy<SmartPlaylistFieldOption> { option ->
            val index = commonFields.indexOf(option.field)
            if (index == -1) Int.MAX_VALUE else index
        }.thenBy { option -> option.label.lowercase() },
    )

@Composable
internal fun smartPlaylistFieldLabel(option: SmartPlaylistFieldOption): String = when (option.field) {
    SmartPlaylistFields.AlbumDateAdded -> stringResource(Res.string.smart_playlist_field_album_date_added)
    SmartPlaylistFields.AlbumDateModified -> stringResource(Res.string.smart_playlist_field_album_date_modified)
    SmartPlaylistFields.AlbumDuration -> stringResource(Res.string.smart_playlist_field_album_duration)
    SmartPlaylistFields.AlbumSongCount -> stringResource(Res.string.smart_playlist_field_album_song_count)
    SmartPlaylistFields.AlbumSize -> stringResource(Res.string.smart_playlist_field_album_size)
    else -> option.label
}

internal fun Throwable.requiresSmartPlaylistPassword(): Boolean {
    val message = message.orEmpty()
    return message.contains("HTTP 401", ignoreCase = true) ||
        (
            message.contains("password", ignoreCase = true) &&
                message.contains("smart playlist", ignoreCase = true)
            )
}
