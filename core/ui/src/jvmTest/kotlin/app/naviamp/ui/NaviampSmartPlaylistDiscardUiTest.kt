package app.naviamp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistDiscardUiTest {
    private val dispatcher = NaviampSystemBackDispatcher()
    private var dismissed = 0
    private val saved = mutableListOf<SmartPlaylistDefinition>()
    private var keyboardInsets by mutableStateOf(WindowInsets(0))

    @Test
    fun backWithoutChangesClosesImmediately() = runComposeUiTest {
        showEditor()

        back()

        runOnIdle { assertEquals(1, dismissed) }
        onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun backWithChangesAsksAndKeepEditingKeepsTheDraft() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Name").performTextReplacement("Evening mix")

        back()

        onNodeWithText("Discard changes?").assertExists()
        onNodeWithTag(SmartPlaylistKeepEditingTestTag).assertIsFocused().performClick()
        onNodeWithText("Discard changes?").assertDoesNotExist()
        onNode(hasText("Evening mix") and hasSetTextAction()).assertExists()
        runOnIdle { assertEquals(0, dismissed) }
    }

    @Test
    fun backOnThePromptKeepsEditing() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Name").performTextReplacement("Evening mix")

        back()
        back()

        onNodeWithText("Discard changes?").assertDoesNotExist()
        onNode(hasText("Evening mix") and hasSetTextAction()).assertExists()
        runOnIdle { assertEquals(0, dismissed) }
    }

    @Test
    fun discardClosesWithoutSaving() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Name").performTextReplacement("Evening mix")

        onNodeWithText("Cancel").performClick()
        onNodeWithTag(SmartPlaylistDiscardTestTag).performClick()

        runOnIdle {
            assertEquals(1, dismissed)
            assertTrue(saved.isEmpty())
        }
    }

    @Test
    fun backWithTheKeyboardShowingHidesItBeforeAskingAnything() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Name").performClick().performTextReplacement("Evening mix")
        keyboardInsets = WindowInsets(bottom = 300.dp)

        back()

        onNodeWithContentDescription("Name").assertIsNotFocused()
        onNodeWithText("Discard changes?").assertDoesNotExist()
        runOnIdle { assertEquals(0, dismissed) }
        keyboardInsets = WindowInsets(0)
        back()
        onNodeWithText("Discard changes?").assertExists()
    }

    @Test
    fun savingDoesNotAsk() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Name").performTextReplacement("Evening mix")

        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()

        waitUntil { saved.isNotEmpty() && dismissed == 1 }
        onNodeWithText("Discard changes?").assertDoesNotExist()
        assertFalse(saved.single().name.isBlank())
    }

    private fun ComposeUiTest.showEditor() {
        val definition = SmartPlaylistDefinition(
            name = "Evening",
            rules = listOf(SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Artist, SmartPlaylistValue.Text("Ascendant"))),
        )
        val initial = SmartPlaylistDraft.fromDefinition(definition)
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
                SmartPlaylistBuilderDialog(
                    NaviampColors(),
                    initialDraft = initial,
                    onDismissRequest = { dismissed++ },
                    onSave = { saved += it },
                    keyboardInsets = keyboardInsets,
                )
            }
        }
    }

    private fun ComposeUiTest.back() {
        runOnIdle { requireNotNull(dispatcher.currentHandler).invoke() }
    }
}
