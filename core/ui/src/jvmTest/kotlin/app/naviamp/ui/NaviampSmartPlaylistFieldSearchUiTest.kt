package app.naviamp.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistFieldSearchUiTest {
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun emptySearchKeepsCommonFieldsFirstUnderReadableHeadings() = runComposeUiTest {
        showEditor()

        openPicker("Artist")

        onNode(hasText("Common") and heading).assertExists()
        onNode(hasText("Album and artist") and heading).assertExists()
        onNode(hasText("Identifiers") and heading).assertExists()
        val common = onNode(hasText("Common") and heading).fetchSemanticsNode().boundsInRoot.top
        val favorite = onNodeWithText("Favorite").fetchSemanticsNode().boundsInRoot.top
        assertTrue(common < favorite, "Common fields lead the empty search")
        onNode(hasText("Artist") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
    }

    @Test
    fun aliasSearchFindsTheFieldAndSelectionInsertsAnOrdinaryEditableRule() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        showEditor(onSave = { saved = it })

        openPicker("Artist")
        onNode(hasText("Artist") and hasSetTextAction()).performTextReplacement("tempo")
        onNodeWithText("Album").assertDoesNotExist()
        onNodeWithText("BPM").performClick()

        onNode(hasText("BPM") and hasContentDescription("Field") and hasSetTextAction()).assertExists()
        onNodeWithContentDescription("Value").performTextReplacement("120")
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { saved != null }
        assertEquals("""{"is":{"bpm":120}}""", saved!!.rules.single().toJsonElement().toString())
    }

    @Test
    fun keyboardAndDpadMoveThroughResultsAndSelect() = runComposeUiTest {
        showEditor()

        openPicker("Artist")
        onNode(hasText("Artist") and hasSetTextAction()).performTextReplacement("play")
        onNode(hasText("play") and hasSetTextAction()).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionCenter)
        }

        onNode(hasText("Play Count") and hasSetTextAction()).assertExists()
        onNode(hasText("Play Count") and hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        onNode(hasText("Common") and heading).assertExists()
        onNode(hasText("Play Count") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
    }

    @Test
    fun emptyResultsAreExplainedAndRepeatedSearchesKeepTheDraft() = runComposeUiTest {
        showEditor()
        onNodeWithContentDescription("Value").performTextReplacement("Ascendant")

        repeat(2) { attempt ->
            openPicker("Artist")
            onNode(hasText("Artist") and hasSetTextAction()).performTextReplacement("zzz$attempt")
            onNodeWithText("No matching fields").assertExists()
            onNode(hasText("zzz$attempt") and hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        }

        onNode(hasText("Artist") and hasSetTextAction()).assertExists()
        onNode(hasText("Ascendant") and hasSetTextAction()).assertExists()
    }

    @Test
    fun sortSearchOffersOnlySortableFieldsAndKeepsRandom() = runComposeUiTest {
        showEditor()

        openPicker("Title")
        onNode(hasText("Title") and hasSetTextAction()).performTextReplacement("playlist")
        onNodeWithText("No matching fields").assertExists()
        onNode(hasText("playlist") and hasSetTextAction()).performTextReplacement("shuffle")
        onNodeWithText("Random").performClick()

        onNode(hasText("Random") and hasSetTextAction()).assertTextContains("Random")
        assertTrue(onAllNodesWithText("Playlist ID").fetchSemanticsNodes().isEmpty())
    }

    private fun ComposeUiTest.showEditor(onSave: suspend (SmartPlaylistDefinition) -> Unit = {}) {
        setContent {
            SmartPlaylistBuilderDialog(NaviampColors(), initialDraft = SmartPlaylistDraft(), onDismissRequest = {}, onSave = onSave)
        }
    }

    private fun ComposeUiTest.openPicker(value: String) {
        onNode(hasText(value) and hasSetTextAction()).performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        onNode(hasText(value) and hasSetTextAction()).performMouseInput { click() }
    }
}
