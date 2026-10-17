package app.naviamp.ui

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
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
        onNodeWithText("Sort priority: 1. Year (descending)").assertExists()
        onNodeWithText("20% of matching tracks.").assertExists()
        onNodeWithTag("smart-playlist-owner-notice").assertExists()
    }

    @Test fun summaryReadsValuesWithoutJsonAndUpdatesNamesWithoutRewritingIds() = runComposeUiTest {
        val references = mutableStateOf(emptyList<SmartPlaylistReference>())
        val definition = SmartPlaylistDefinition("Readable", rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.InPlaylist, "id", SmartPlaylistValue.Text("saved-id")),
            SmartPlaylistCondition(SmartPlaylistOperator.NotInPlaylist, "id", SmartPlaylistValue.Text("missing-id")),
            SmartPlaylistCondition(SmartPlaylistOperator.Contains, SmartPlaylistFields.Title, SmartPlaylistValue.Text("A \"quoted\" song")),
            SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
            SmartPlaylistCondition(SmartPlaylistOperator.InTheRange, SmartPlaylistFields.Rating,
                SmartPlaylistValue.Range(SmartPlaylistValue.Decimal(2.5), SmartPlaylistValue.Number(5))),
            SmartPlaylistCondition(SmartPlaylistOperator.InTheRange, SmartPlaylistFields.Date,
                SmartPlaylistValue.Range(SmartPlaylistValue.Text("2026-01-01"), SmartPlaylistValue.Text("2026-02-01"))),
            SmartPlaylistCondition(SmartPlaylistOperator.InTheLast, SmartPlaylistFields.LastPlayed, SmartPlaylistValue.Number(30)),
            SmartPlaylistCondition(SmartPlaylistOperator.NotInTheLast, SmartPlaylistFields.LastPlayed, SmartPlaylistValue.Number(1)),
            SmartPlaylistCondition(SmartPlaylistOperator.IsMissing, SmartPlaylistFields.Album, SmartPlaylistValue.Flag(false)),
            SmartPlaylistCondition(SmartPlaylistOperator.IsPresent, SmartPlaylistFields.Artist, SmartPlaylistValue.Flag(false)),
            SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.ExplicitStatus, SmartPlaylistValue.Text("c")),
        ))
        val original = definition.toNspJson()
        setContent { SmartPlaylistSummarySection(definition, NaviampColors(), references.value) }
        onNodeWithText("Unavailable playlist (saved-id)", substring = true).assertExists()
        runOnIdle { references.value = listOf(
            SmartPlaylistReference("saved-id", "Folk Music", "Alice"),
            SmartPlaylistReference("another-id", "Folk Music", "Bob"),
        ) }
        onNodeWithText("Track in playlist Folk Music (Alice)", substring = true).assertExists()
        onNodeWithText("Track not in playlist Unavailable playlist (missing-id)", substring = true).assertExists()
        onNodeWithText("contains A \"quoted\" song", substring = true).assertExists()
        onNodeWithText("is yes", substring = true).assertExists()
        onNodeWithText("between 2.5 – 5", substring = true).assertExists()
        onNodeWithText("between 2026-01-01 – 2026-02-01", substring = true).assertExists()
        onNodeWithText("in the last 30 days", substring = true).assertExists()
        onNodeWithText("not in the last 1 day", substring = true).assertExists()
        onNodeWithText("Album is present", substring = true).assertExists()
        onNodeWithText("Artist is missing", substring = true).assertExists()
        onNodeWithText("Explicit Status is clean", substring = true).assertExists()
        onNodeWithText("is missing false", substring = true).assertDoesNotExist()
        assertEquals(original, definition.toNspJson())
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
