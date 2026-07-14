package app.naviamp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.naviamp.domain.smartplaylist.SmartPlaylistConditionDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldCatalog
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldOption
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistGroupDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistLimitMode
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistSortDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistTemplates
import app.naviamp.domain.smartplaylist.SmartPlaylistValueType
import app.naviamp.domain.smartplaylist.displayLabel
import app.naviamp.domain.smartplaylist.updated
import app.naviamp.domain.smartplaylist.valueLabel
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

@Composable
fun SmartPlaylistBuilderDialog(
    colors: NaviampColors,
    initialDraft: SmartPlaylistDraft = SmartPlaylistDraft(),
    title: String = "Smart playlist",
    saveLabel: String = "Save",
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
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
    var importJson by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var passwordPromptOpen by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordSaving by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingPasswordDefinition by remember { mutableStateOf<SmartPlaylistDefinition?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val definition = remember(draft) {
        runCatching { draft.toDefinition() }
    }
    val jsonPreview = definition.getOrNull()?.toNspJson() ?: definition.exceptionOrNull()?.message.orEmpty()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
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
                            value = importJson,
                            onValueChange = {
                                importJson = it
                                importMessage = null
                            },
                            label = { Text("Paste .nsp or Navidrome smart playlist JSON", color = colors.secondaryText) },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            enabled = importJson.isNotBlank(),
                            onClick = {
                                runCatching {
                                    SmartPlaylistDefinition.fromNspJson(importJson)
                                }.onSuccess { imported ->
                                    draft = SmartPlaylistDraft.fromDefinition(imported)
                                    importMessage = "Imported ${imported.name}."
                                    importOpen = false
                                }.onFailure { error ->
                                    importMessage = error.message ?: "Could not import smart playlist JSON."
                                }
                            },
                        ) {
                            Text("Validate and load", color = colors.accent)
                        }
                    }
                }
                SmartPlaylistCustomControls(
                    colors = colors,
                    draft = draft,
                    availableLibraries = availableLibraries,
                    selectedConnectionLibraryIds = selectedConnectionLibraryIds,
                    onDraftChange = { draft = it },
                )
                saveMessage?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
                SmartPlaylistSection(
                    title = "Generated JSON",
                    colors = colors,
                ) {
                    OutlinedTextField(
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
    if (passwordPromptOpen) {
        val pendingDefinition = pendingPasswordDefinition
        AlertDialog(
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
                        modifier = Modifier.fillMaxWidth().testTag(SmartPlaylistPasswordFieldTestTag),
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

@Composable
private fun SmartPlaylistCustomControls(
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
                onConditionChange = { updatedCondition ->
                    onDraftChange(draft.copy(conditions = draft.conditions.updated(index, updatedCondition)))
                },
                onRemove = if (draft.conditions.size > 1) {
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
                        onConditionChange = { updatedCondition ->
                            onDraftChange(
                                draft.copy(
                                    groups = draft.groups.updated(
                                        groupIndex,
                                        group.copy(conditions = group.conditions.updated(conditionIndex, updatedCondition)),
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
                trailing = if (draft.sort.size > 1) {
                    {
                        TextButton(
                            onClick = { onDraftChange(draft.copy(sort = draft.sort.filterIndexed { i, _ -> i != index })) },
                        ) {
                            Text("Remove", color = colors.secondaryText, fontSize = 12.sp)
                        }
                    }
                } else {
                    null
                },
            ) {
                SmartPlaylistDropdown(
                    label = "Field",
                    value = sort.field.label,
                    colors = colors,
                    options = SmartPlaylistFieldCatalog.sortableFields
                        .smartPlaylistMenuOrder(CommonSmartPlaylistSortFields)
                        .map { it.label to it },
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
                SmartPlaylistLimitMode.TrackCount -> "Track count"
                SmartPlaylistLimitMode.Percent -> "Percent"
            },
            colors = colors,
            options = listOf("Track count" to SmartPlaylistLimitMode.TrackCount, "Percent" to SmartPlaylistLimitMode.Percent),
            onSelected = { onDraftChange(draft.copy(limitMode = it)) },
        )
        SmartPlaylistTextField(
            value = draft.limit.toString(),
            onValueChange = { onDraftChange(draft.copy(limit = it.toIntOrNull() ?: draft.limit)) },
            label = if (draft.limitMode == SmartPlaylistLimitMode.Percent) "Limit percent" else "Track limit",
            colors = colors,
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
            value = condition.field.label,
            colors = colors,
            options = SmartPlaylistFieldCatalog.fields
                .smartPlaylistMenuOrder(CommonSmartPlaylistRuleFields)
                .map { it.label to it },
            onSelected = { field ->
                onConditionChange(condition.copy(field = field, operator = field.operators.first(), value = "", secondValue = ""))
            },
        )
        SmartPlaylistDropdown(
            label = "Operator",
            value = condition.operator.displayLabel(),
            colors = colors,
            options = condition.field.operators.map { it.displayLabel() to it },
            onSelected = { operator ->
                onConditionChange(condition.copy(operator = operator, value = "", secondValue = ""))
            },
        )
        SmartPlaylistConditionValueControls(
            colors = colors,
            condition = condition,
            onConditionChange = onConditionChange,
        )
        if (condition.operator == SmartPlaylistOperator.InTheRange) {
            SmartPlaylistTextField(
                value = condition.secondValue,
                onValueChange = { value -> onConditionChange(condition.copy(secondValue = value)) },
                label = "End value",
                colors = colors,
            )
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
    onConditionChange: (SmartPlaylistConditionDraft) -> Unit,
) {
    if (condition.field.valueType == SmartPlaylistValueType.Boolean) {
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

    SmartPlaylistTextField(
        value = condition.value,
        onValueChange = { value -> onConditionChange(condition.copy(value = value)) },
        label = condition.valueLabel(),
        colors = colors,
    )
}

@Composable
private fun SmartPlaylistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.secondaryText) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> SmartPlaylistDropdown(
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
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
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
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        expanded = true
                    }
                },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = value
            },
            containerColor = colors.controlSurface,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
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

private fun Throwable.requiresSmartPlaylistPassword(): Boolean {
    val message = message.orEmpty()
    return message.contains("HTTP 401", ignoreCase = true) ||
        (
            message.contains("password", ignoreCase = true) &&
                message.contains("smart playlist", ignoreCase = true)
            )
}
