package app.naviamp.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistConditionDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldCatalog
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistTypedInputsUiTest {
    @Test
    fun ratingChoicesSaveNavidromeStarValues() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.Rating)

        choose("Choose", "4 stars")
        assertEquals("""{"is":{"rating":4}}""", save(saved))
        choose("4 stars", "Unrated")
        assertEquals("""{"is":{"rating":0}}""", save(saved))
    }

    @Test
    fun explicitStatusUsesDescriptiveChoicesIncludingNotSpecified() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.ExplicitStatus)

        onNode(selector("Not specified")).assertExists()
        assertEquals("""{"is":{"explicitstatus":""}}""", save(saved))
        choose("Not specified", "Clean")
        assertEquals("""{"is":{"explicitstatus":"c"}}""", save(saved))
    }

    @Test
    fun relativePresetsAndCustomDaysSaveTheSameShape() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.InTheLast)

        onNodeWithTag("smart-playlist-days-preset-30").performScrollTo().performClick()
        assertEquals("""{"inTheLast":{"dateadded":30}}""", save(saved))
        onNodeWithContentDescription("Days").performTextReplacement("45")
        assertEquals("""{"inTheLast":{"dateadded":45}}""", save(saved))
    }

    @Test
    fun presenceRulesNeedNoExtraValueChoice() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.Bpm, SmartPlaylistOperator.IsMissing)

        onNodeWithText("No value needed.").assertExists()
        assertTrue(onAllNodesWithText("Choose").fetchSemanticsNodes().isEmpty())
        assertEquals("""{"isMissing":{"bpm":true}}""", save(saved))
    }

    @Test
    fun numericFieldsShowUnitsAndPrecisionAndKeepInvalidTextEditable() = runComposeUiTest {
        val condition = SmartPlaylistConditionDraft(field(SmartPlaylistFields.Year), SmartPlaylistOperator.Is)
        val saved = showRule(condition)

        onNodeWithText("Whole numbers only.").assertExists()
        onNodeWithContentDescription("Value").performTextReplacement("19x9")
        onNodeWithTag("smart-playlist-rule-error-${condition.id}").assertExists()
        onNode(hasText("19x9") and hasSetTextAction()).assertExists()
        onNodeWithContentDescription("Value").performTextReplacement("1999")
        assertEquals("""{"is":{"year":1999}}""", save(saved))
    }

    @Test
    fun anInvalidChoiceValueStaysVisibleWithItsErrorUntilAChoiceReplacesIt() = runComposeUiTest {
        val condition = SmartPlaylistConditionDraft(field(SmartPlaylistFields.Rating), SmartPlaylistOperator.Is, "four")
        val saved = showRule(condition)

        onNodeWithTag("smart-playlist-rule-error-${condition.id}").assertExists()
        onNode(selector("four")).assertExists()
        choose("four", "5 stars")
        onNode(selector("5 stars")).assertExists()
        assertEquals("""{"is":{"rating":5}}""", save(saved))
    }

    @Test
    fun decimalFieldsShowTheirUnit() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.Duration, SmartPlaylistOperator.GreaterThan)

        onNodeWithText("sec").assertExists()
        onNodeWithText("Decimals are allowed, for example 2.5.").assertExists()
        onNodeWithContentDescription("Value").performTextReplacement("90.5")
        assertEquals("""{"gt":{"duration":90.5}}""", save(saved))
    }

    @Test
    fun invalidCalendarDatesShowAnInlineError() = runComposeUiTest {
        val condition = SmartPlaylistConditionDraft(field(SmartPlaylistFields.DateAdded), SmartPlaylistOperator.Before, "2026-02-30")
        showRule(condition)

        onNodeWithTag("smart-playlist-rule-error-${condition.id}").assertExists()
        onNodeWithTag("smart-playlist-choose-date").assertExists()
    }

    @Test
    fun booleanConditionsKeepTheirSelector() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.Loved)

        choose("Choose", "False")
        assertEquals("""{"is":{"loved":false}}""", save(saved))
    }

    @Test
    fun changingTheFieldDropsAnOperandTypedForTheOldField() = runComposeUiTest {
        val saved = showRule(SmartPlaylistConditionDraft(field(SmartPlaylistFields.Rating), SmartPlaylistOperator.Is, "4"))

        onNode(selector("4 stars")).assertExists()
        onNode(hasText("Rating") and hasSetTextAction()).performScrollTo().performMouseInput { click() }
        onNode(hasText("Rating") and hasSetTextAction()).performTextReplacement("yea")
        onNodeWithText("Year").performClick()

        assertTrue(onAllNodesWithText("4 stars").fetchSemanticsNodes().isEmpty())
        onNodeWithContentDescription("Value").performTextReplacement("1999")
        assertEquals("""{"is":{"year":1999}}""", save(saved))
    }

    @Test
    fun fixedChoicesAreReadOnlySelectorsThatOpenFromTheKeyboard() = runComposeUiTest {
        val saved = showRule(SmartPlaylistFields.Rating)

        onNode(selector("is")).assertExists()
        onNode(hasText("Rating") and hasSetTextAction()).assertExists()
        onNode(selector("Choose")).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        onNode(selector("Choose")).performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("3 stars").assertIsSelectable().performClick()
        onNode(selector("3 stars")).assertExists()
        assertEquals("""{"is":{"rating":3}}""", save(saved))
    }

    private fun selector(text: String) =
        hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false) and hasClickAction()

    private fun field(key: String) = SmartPlaylistFieldCatalog.fields.first { it.field == key }

    private fun ComposeUiTest.showRule(key: String, operator: SmartPlaylistOperator? = null) =
        showRule(SmartPlaylistConditionDraft(field(key), operator ?: field(key).operators.first()))

    private fun ComposeUiTest.showRule(condition: SmartPlaylistConditionDraft): MutableList<SmartPlaylistDefinition> {
        val saved = mutableListOf<SmartPlaylistDefinition>()
        setContent {
            SmartPlaylistBuilderDialog(
                NaviampColors(),
                initialDraft = SmartPlaylistDraft(conditions = listOf(condition)),
                onDismissRequest = {},
                onSave = { saved += it },
            )
        }
        return saved
    }

    private fun ComposeUiTest.choose(current: String, option: String) {
        onNode(selector(current)).performScrollTo().performMouseInput { click() }
        onNodeWithText(option).performClick()
    }

    private fun ComposeUiTest.save(saved: MutableList<SmartPlaylistDefinition>): String {
        val before = saved.size
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { saved.size > before }
        return saved.last().rules.single().toJsonElement().toString()
    }
}
