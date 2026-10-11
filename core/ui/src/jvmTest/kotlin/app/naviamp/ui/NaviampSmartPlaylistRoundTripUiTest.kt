package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistRoundTripUiTest {
    @Test
    fun switchingAnInvalidTrackLimitToUnlimitedRestoresSaving() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        setContent {
            SmartPlaylistBuilderDialog(colors = NaviampColors(), initialDraft = imported().copy(
                limitMode = app.naviamp.domain.smartplaylist.SmartPlaylistLimitMode.TrackCount, limit = 0),
                onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithTag(SmartPlaylistSaveTestTag).assertIsNotEnabled()
        onNodeWithText("Track count").performScrollTo().performClick()
        onNodeWithText("Unlimited").performClick()
        onNodeWithTag(SmartPlaylistSaveTestTag).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertNull(saved!!.limit)
        assertNull(saved!!.limitPercent)
    }
    private fun imported(properties: String = "") = SmartPlaylistDraft.fromDefinition(
        SmartPlaylistDefinition.fromNspJson("""{"name":"Mix","all":[{"is":{"loved":true}}]$properties}"""),
    )

    @Test
    fun unlimitedImportShowsUnlimitedAndSavesWithoutImplicitLimitOrSort() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        setContent {
            SmartPlaylistBuilderDialog(colors = NaviampColors(), initialDraft = imported(),
                onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithText("Unlimited").performScrollTo().assertExists()
        assertEquals(0, onAllNodesWithText("Track limit").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("Limit percent").fetchSemanticsNodes().size)
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertNull(saved!!.limit)
        assertNull(saved!!.limitPercent)
        assertTrue(saved!!.sort.isEmpty())
    }

    @Test
    fun lastSortCanBeRemovedAndTrackLimitCanBeChangedToUnlimited() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        setContent {
            SmartPlaylistBuilderDialog(colors = NaviampColors(), initialDraft = imported(",\"sort\":\"-rating\",\"limit\":25"),
                onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithText("Remove").performScrollTo().performClick()
        onNodeWithText("Track count").performScrollTo().performClick()
        onNodeWithText("Unlimited").performClick()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertNull(saved!!.limit)
        assertTrue(saved!!.sort.isEmpty())
    }

    @Test
    fun unrepresentableImportIsInspectableAndKeepsTheCurrentDraft() = runComposeUiTest {
        var saved: SmartPlaylistDefinition? = null
        setContent {
            SmartPlaylistBuilderDialog(colors = NaviampColors(), initialDraft = imported(),
                onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithText("Import JSON").performScrollTo().performClick()
        onNodeWithText("Paste .nsp or Navidrome smart playlist JSON").performScrollTo().performTextInput(
            """{"name":"Broken","all":[{"gt":{"rating":3.75}}]}""",
        )
        onNodeWithText("Validate and load").performScrollTo().performClick()
        onNodeWithTag("smart-playlist-original-definition").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertEquals("Mix", saved!!.name)
        assertEquals(imported().toDefinition().rules, saved!!.rules)
    }
}
