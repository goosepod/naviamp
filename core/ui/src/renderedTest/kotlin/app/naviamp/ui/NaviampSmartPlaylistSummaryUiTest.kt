package app.naviamp.ui

import androidx.compose.ui.test.*
import app.naviamp.domain.smartplaylist.*
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistSummaryUiTest {
    @Test fun summaryShowsNestedLogicExclusionsSortLimitAndOwnerSemantics() = runComposeUiTest {
        val definition = definition()
        setContent { SmartPlaylistSummarySection(definition, NaviampColors()) }
        onNodeWithText("All of:", substring = true).assertExists()
        onNodeWithText("Any of:", substring = true).assertExists()
        onNodeWithText("is not", substring = true).assertExists()
        onNodeWithText("Sort priority: 1. Year ↓").assertExists()
        onNodeWithText("20% of matching tracks.").assertExists()
        onNodeWithTag("smart-playlist-owner-notice").assertExists()
    }

    @Test fun collapsingOptionsAndAdvancedPreservesTheWholeDefinition() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        val initial = definition()
        setContent {
            SmartPlaylistBuilderDialog(NaviampColors(), SmartPlaylistDraft.fromDefinition(initial),
                availableLibraries = listOf(ConnectionFormMusicFolder("a", "A"), ConnectionFormMusicFolder("b", "B")),
                selectedConnectionLibraryIds = listOf("a", "b"), onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithText("Refresh delay").assertDoesNotExist()
        onNodeWithTag("smart-playlist-options-toggle").performScrollTo().performClick()
        onNodeWithText("Refresh delay").assertExists()
        onNodeWithTag("smart-playlist-options-toggle").performScrollTo().performClick()
        onNodeWithText("Refresh delay").assertDoesNotExist()
        onNodeWithTag("smart-playlist-advanced-toggle").performScrollTo().performClick()
        onNodeWithText("Import JSON").assertExists()
        onNodeWithTag("smart-playlist-advanced-toggle").performScrollTo().performClick()
        onNodeWithText("Import JSON").assertDoesNotExist()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertEquals(initial, saved)
    }

    private fun definition() = SmartPlaylistDefinition("Owned mix", comment = "", match = SmartPlaylistMatch.All,
        rules = listOf(SmartPlaylistGroup(SmartPlaylistMatch.Any, listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.IsNot, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(false)),
            SmartPlaylistCondition(SmartPlaylistOperator.Contains, SmartPlaylistFields.Genre, SmartPlaylistValue.Text("rock")),
        ))), sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Year, true)),
        limitPercent = 20, isPublic = true, libraryIds = listOf("b"), refreshDelay = "12h")
}
