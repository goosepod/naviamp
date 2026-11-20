package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** A platform editor supplies text or returns null when the user dismisses it. */
fun interface NaviampTelevisionTextEntry {
    suspend fun enter(request: NaviampTelevisionTextEntryRequest): String?
}

data class NaviampTelevisionTextEntryRequest(
    val value: String,
    val label: String,
    val submitLabel: String,
    val cancelLabel: String,
)

val LocalNaviampTelevisionTextEntry = staticCompositionLocalOf<NaviampTelevisionTextEntry?> { null }

/** The app editor responds to the same remote events as the rest of the TV interface. */
@Composable
internal fun TelevisionSearchEditor(
    request: NaviampTelevisionTextEntryRequest,
    colors: NaviampColors,
    systemTextEntry: NaviampTelevisionTextEntry,
    onClose: (String?) -> Unit,
) {
    var draft by remember { mutableStateOf(request.value) }
    var systemKeyboardOpen by remember { mutableStateOf(false) }
    val canSubmit = draft.isNotBlank() || request.value.isNotEmpty()
    val systemKeyboardFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    // Enter activates a focused editor action; elsewhere (letters, hardware typing) it submits.
    var focusedAction by remember { mutableStateOf<TelevisionSearchEditorAction?>(null) }
    fun Modifier.editorAction(action: TelevisionSearchEditorAction) = onFocusChanged {
        if (it.isFocused) focusedAction = action else if (focusedAction == action) focusedAction = null
    }
    fun deleteLastCharacter() {
        val length = draft.length
        if (length > 0) {
            val surrogatePair = length > 1 && draft[length - 1].isLowSurrogate() && draft[length - 2].isHighSurrogate()
            draft = draft.dropLast(if (surrogatePair) 2 else 1)
        }
    }

    NaviampDialog(onDismissRequest = { onClose(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
            repeat(5) {
                withFrameNanos { }
                if (firstKeyFocus.requestFocus()) return@LaunchedEffect
            }
        }
        LaunchedEffect(systemKeyboardOpen) {
            if (systemKeyboardOpen) {
                systemTextEntry.enter(request.copy(value = draft))?.let { draft = it }
                systemKeyboardOpen = false
                withFrameNanos { }
                systemKeyboardFocus.requestFocus()
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .testTag(TelevisionSearchEditorTestTag)
                .onPreviewKeyEvent { event ->
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return@onPreviewKeyEvent false
                    }
                    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        if (event.type == KeyEventType.KeyDown) when (focusedAction) {
                            TelevisionSearchEditorAction.Space -> draft += ' '
                            TelevisionSearchEditorAction.Backspace -> deleteLastCharacter()
                            TelevisionSearchEditorAction.SystemKeyboard -> systemKeyboardOpen = true
                            TelevisionSearchEditorAction.Cancel -> onClose(null)
                            TelevisionSearchEditorAction.Submit, null -> if (canSubmit) onClose(draft)
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.Backspace, Key.Delete -> {
                            deleteLastCharacter()
                            true
                        }
                        else -> televisionSearchKeyboardCharacter(event.key, event.utf16CodePoint)?.let {
                            draft += it
                            true
                        } ?: false
                    }
                },
        ) {
            Text(request.label, color = colors.secondaryText, fontSize = 16.sp)
            Text(
                draft.ifEmpty { "\u00A0" }, color = colors.primaryText, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .background(colors.controlSurface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            val keyPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            TelevisionSearchKeyboardRows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { column, character ->
                        val focus = if (rowIndex == 0 && column == 0) Modifier.focusRequester(firstKeyFocus) else Modifier
                        TelevisionTextButton(
                            character.toString(), colors, calmFocus = true, contentPadding = keyPadding,
                            modifier = Modifier.weight(1f).then(focus), onClick = { draft += character },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelevisionTextButton(stringResource(Res.string.tv_search_keyboard_space), colors, calmFocus = true,
                    modifier = Modifier.editorAction(TelevisionSearchEditorAction.Space),
                    contentPadding = keyPadding, onClick = { draft += ' ' })
                TelevisionTextButton(stringResource(Res.string.tv_search_keyboard_backspace), colors, calmFocus = true,
                    modifier = Modifier.editorAction(TelevisionSearchEditorAction.Backspace),
                    contentPadding = keyPadding, onClick = ::deleteLastCharacter)
                TelevisionTextButton(stringResource(Res.string.tv_search_system_keyboard), colors, calmFocus = true,
                    modifier = Modifier.focusRequester(systemKeyboardFocus)
                        .editorAction(TelevisionSearchEditorAction.SystemKeyboard),
                    contentPadding = keyPadding, onClick = { systemKeyboardOpen = true })
                Spacer(Modifier.weight(1f))
                TelevisionTextButton(request.cancelLabel, colors, calmFocus = true, contentPadding = keyPadding,
                    modifier = Modifier.editorAction(TelevisionSearchEditorAction.Cancel), onClick = { onClose(null) })
                TelevisionTextButton(request.submitLabel, colors, calmFocus = true, contentPadding = keyPadding,
                    modifier = Modifier.editorAction(TelevisionSearchEditorAction.Submit),
                    enabled = canSubmit, onClick = { onClose(draft) })
            }
        }
    }
}

private enum class TelevisionSearchEditorAction { Space, Backspace, SystemKeyboard, Cancel, Submit }

private val TelevisionSearchKeyboardRows = "abcdefghijklmnopqrstuvwxyz0123456789".chunked(12)
internal const val TelevisionSearchEditorTestTag = "television-search-editor"

internal fun televisionSearchKeyboardCharacter(key: Key, codePoint: Int): Char? = when (key) {
    // Device Hub remote events can carry printable characters even when the key is a direction.
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter,
    Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape, Key.Menu, Key.MediaPlayPause,
    -> null
    else -> codePoint.takeIf { it in 32..Char.MAX_VALUE.code }?.toChar()?.takeUnless {
        it.isISOControl() || it.isSurrogate()
    }
}
