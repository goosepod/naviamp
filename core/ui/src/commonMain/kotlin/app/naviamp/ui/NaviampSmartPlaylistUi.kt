package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistConditionDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldCatalog
import app.naviamp.domain.smartplaylist.SmartPlaylistGroupDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistLimitMode
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistSortDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistValueType
import app.naviamp.domain.smartplaylist.displayLabel
import app.naviamp.domain.smartplaylist.updated
import app.naviamp.domain.smartplaylist.valueLabel
import kotlinx.coroutines.launch

@Composable
fun SmartPlaylistBuilderDialog(
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
    onSave: suspend (SmartPlaylistDefinition) -> Unit,
) {
    var draft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val definition = remember(draft) {
        runCatching { draft.toDefinition() }
    }
    val jsonPreview = definition.getOrNull()?.toNspJson() ?: definition.exceptionOrNull()?.message.orEmpty()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Smart playlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Build a Navidrome smart playlist from rules, sorting, limits, and visibility.",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
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
                SmartPlaylistCustomControls(
                    colors = colors,
                    draft = draft,
                    onDraftChange = { draft = it },
                )
                saveMessage?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
                Text("Generated JSON", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = jsonPreview,
                    onValueChange = {},
                    readOnly = true,
                    minLines = 8,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
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
                            saveMessage = error.message ?: "Could not save smart playlist."
                        }
                    }
                },
            ) {
                Text(if (saving) "Saving..." else "Save")
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
}

@Composable
private fun SmartPlaylistCustomControls(
    colors: NaviampColors,
    draft: SmartPlaylistDraft,
    onDraftChange: (SmartPlaylistDraft) -> Unit,
) {
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
            title = "Rule ${index + 1}",
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
        Text("Add rule", color = colors.accent)
    }

    Text("Groups", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    draft.groups.forEachIndexed { groupIndex, group ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Group ${groupIndex + 1}", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TextButton(
                    onClick = { onDraftChange(draft.copy(groups = draft.groups.filterIndexed { i, _ -> i != groupIndex })) },
                ) {
                    Text("Remove group", color = colors.secondaryText)
                }
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
                    title = "Group ${groupIndex + 1}.${conditionIndex + 1}",
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
                Text("Add group rule", color = colors.accent)
            }
        }
    }
    TextButton(
        onClick = { onDraftChange(draft.copy(groups = draft.groups + SmartPlaylistGroupDraft())) },
    ) {
        Text("Add group", color = colors.accent)
    }

    Text("Sort", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    draft.sort.forEachIndexed { index, sort ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SmartPlaylistDropdown(
                label = "Field",
                value = sort.field.label,
                colors = colors,
                options = SmartPlaylistFieldCatalog.sortableFields.map { it.label to it },
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
                if (draft.sort.size > 1) {
                    TextButton(
                        onClick = { onDraftChange(draft.copy(sort = draft.sort.filterIndexed { i, _ -> i != index })) },
                    ) {
                        Text("Remove sort", color = colors.secondaryText)
                    }
                }
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

@Composable
private fun SmartPlaylistRuleControls(
    title: String,
    colors: NaviampColors,
    condition: SmartPlaylistConditionDraft,
    onConditionChange: (SmartPlaylistConditionDraft) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SmartPlaylistDropdown(
            label = "Field",
            value = condition.field.label,
            colors = colors,
            options = SmartPlaylistFieldCatalog.fields.map { it.label to it },
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
        onRemove?.let { remove ->
            TextButton(onClick = remove) {
                Text("Remove rule", color = colors.secondaryText)
            }
        }
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
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label: $value", color = colors.secondaryText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.controlSurface,
        ) {
            options.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel, color = colors.primaryText) },
                    onClick = {
                        expanded = false
                        onSelected(optionValue)
                    },
                )
            }
        }
    }
}
