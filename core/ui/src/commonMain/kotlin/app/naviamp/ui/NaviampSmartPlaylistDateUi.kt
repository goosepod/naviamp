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
import app.naviamp.domain.smartplaylist.smartPlaylistDateFromUtcMillis
import app.naviamp.domain.smartplaylist.smartPlaylistDateToUtcMillis
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmartPlaylistOperandField(
    calendarDate: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
    relativeDays: Boolean = false,
) {
    var choosingDate by remember(calendarDate) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            colors = naviampOutlinedTextFieldColors(colors),
            value = value, onValueChange = onValueChange,
            label = { Text(label, color = colors.secondaryText) },
            singleLine = true, textStyle = TextStyle(fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().naviampTextInputFocus(),
        )
        if (calendarDate) {
            Text(stringResource(Res.string.smart_playlist_date_format), color = colors.secondaryText, fontSize = 11.sp)
            TextButton(onClick = { choosingDate = true }, modifier = Modifier.testTag("smart-playlist-choose-date")) {
                Text(stringResource(Res.string.smart_playlist_choose_date), color = colors.accent)
            }
        } else if (relativeDays) {
            Text(stringResource(Res.string.smart_playlist_days_hint), color = colors.secondaryText, fontSize = 11.sp)
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
