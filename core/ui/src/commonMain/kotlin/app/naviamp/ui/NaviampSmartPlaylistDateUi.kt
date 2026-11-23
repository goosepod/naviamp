package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.naviamp.domain.smartplaylist.SmartPlaylistOperandInput
import app.naviamp.domain.smartplaylist.SmartPlaylistValueUnit
import app.naviamp.domain.smartplaylist.smartPlaylistDateFromUtcMillis
import app.naviamp.domain.smartplaylist.smartPlaylistDateToUtcMillis
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SmartPlaylistOperandField(
    calendarDate: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
    relativeDays: Boolean = false,
    number: SmartPlaylistOperandInput.Number? = null,
    dayPresets: List<Int> = emptyList(),
) {
    var choosingDate by remember(calendarDate) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SmartPlaylistOutlinedTextField(
            colors = colors,
            value = value, onValueChange = onValueChange,
            label = label,
            singleLine = true, textStyle = TextStyle(fontSize = 13.sp),
            suffix = number?.unit?.let { smartPlaylistUnitLabel(it) },
            keyboardOptions = when {
                number?.decimal == true -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                number != null || relativeDays -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions.Default
            },
            modifier = Modifier.fillMaxWidth().naviampTextInputFocus(),
        )
        if (calendarDate) {
            Text(stringResource(Res.string.smart_playlist_date_format), color = colors.secondaryText, fontSize = 11.sp)
            TextButton(onClick = { choosingDate = true }, modifier = Modifier.testTag("smart-playlist-choose-date")) {
                Text(stringResource(Res.string.smart_playlist_choose_date), color = colors.accent)
            }
        } else if (relativeDays) {
            if (dayPresets.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayPresets.forEach { days ->
                        val selected = value.trim() == days.toString()
                        TextButton(
                            onClick = { onValueChange(days.toString()) },
                            modifier = Modifier.testTag("smart-playlist-days-preset-$days").semantics { this.selected = selected },
                        ) {
                            Text(
                                stringResource(Res.string.smart_playlist_days_preset, days),
                                color = if (selected) colors.accent else colors.secondaryText,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Text(stringResource(Res.string.smart_playlist_days_hint), color = colors.secondaryText, fontSize = 11.sp)
        } else if (number != null) {
            val precision = stringResource(if (number.decimal) Res.string.smart_playlist_number_decimal else Res.string.smart_playlist_number_whole)
            val minimum = number.minimum
            val maximum = number.maximum
            val range = if (minimum != null && maximum != null) {
                " " + stringResource(Res.string.smart_playlist_number_range, minimum.plainNumber(), maximum.plainNumber())
            } else ""
            Text(precision + range, color = colors.secondaryText, fontSize = 11.sp)
        }
    }
    if (choosingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = smartPlaylistDateToUtcMillis(value),
            yearRange = 1..9999,
        )
        NaviampDialog(onDismissRequest = { choosingDate = false }) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Surface(color = colors.controlSurface, shape = DatePickerDefaults.shape) {
                Column(Modifier.widthIn(max = 360.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)) {
                            choosingDate = false
                            true
                        } else false
                    }
                    .focusRequester(focusRequester).focusable()
                    .verticalScroll(rememberScrollState())) {
                    DatePicker(state = state, showModeToggle = false,
                        title = { Text(stringResource(Res.string.smart_playlist_choose_date), modifier = Modifier.padding(24.dp)) },
                        colors = DatePickerDefaults.colors(
                            containerColor = colors.controlSurface,
                            titleContentColor = colors.secondaryText, headlineContentColor = colors.primaryText,
                            weekdayContentColor = colors.secondaryText, subheadContentColor = colors.primaryText,
                            navigationContentColor = colors.primaryText, yearContentColor = colors.primaryText,
                            currentYearContentColor = colors.accent, selectedYearContentColor = colors.onAccent,
                            selectedYearContainerColor = colors.accent, dayContentColor = colors.primaryText,
                            selectedDayContentColor = colors.onAccent, selectedDayContainerColor = colors.accent,
                            todayContentColor = colors.accent, todayDateBorderColor = colors.accent,
                            dividerColor = colors.border,
                        ))
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { choosingDate = false }) {
                            Text(stringResource(Res.string.common_cancel), color = colors.secondaryText)
                        }
                        TextButton(enabled = state.selectedDateMillis != null,
                            onClick = {
                                state.selectedDateMillis?.let(::smartPlaylistDateFromUtcMillis)?.let(onValueChange)
                                choosingDate = false
                            }, modifier = Modifier.testTag("smart-playlist-use-date")) {
                            Text(stringResource(Res.string.smart_playlist_use_date), color = colors.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun smartPlaylistUnitLabel(unit: SmartPlaylistValueUnit): String = stringResource(
    when (unit) {
        SmartPlaylistValueUnit.Seconds -> Res.string.smart_playlist_unit_seconds
        SmartPlaylistValueUnit.Bytes -> Res.string.smart_playlist_unit_bytes
        SmartPlaylistValueUnit.Kilobits -> Res.string.smart_playlist_unit_kbps
        SmartPlaylistValueUnit.Hertz -> Res.string.smart_playlist_unit_hertz
        SmartPlaylistValueUnit.Bits -> Res.string.smart_playlist_unit_bits
        SmartPlaylistValueUnit.BeatsPerMinute -> Res.string.smart_playlist_unit_bpm
        SmartPlaylistValueUnit.Decibels -> Res.string.smart_playlist_unit_decibels
        SmartPlaylistValueUnit.Stars -> Res.string.smart_playlist_unit_stars
    },
)

private fun Double.plainNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
