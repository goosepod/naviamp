package app.naviamp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import app.naviamp.domain.smartplaylist.SmartPlaylistReference
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistReferencePickerTest {
    @Test fun keyboardCanChooseAReferenceWithoutAPointer() = runComposeUiTest {
        var selected: String? = null
        setContent {
            SmartPlaylistReferencePicker("", listOf(SmartPlaylistReference("target", "Target")),
                false, false, null, {}, NaviampColors()) { selected = it }
        }
        onNodeWithTag("smart-playlist-reference-open").performClick()
        onNodeWithTag("smart-playlist-reference-search").performClick().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Enter)
        }
        runOnIdle { assertEquals("target", selected) }
    }
    @Test fun searchDisambiguatesSameNamePlaylistsAndSelectsTheStableId() = runComposeUiTest {
        var selected: String? = null
        setContent {
            SmartPlaylistReferencePicker("old", listOf(
                SmartPlaylistReference("a", "Mix", "Alice"),
                SmartPlaylistReference("b", "Mix", "Bob", true),
                SmartPlaylistReference("self", "My playlist"),
            ), false, false, "self", {}, NaviampColors()) { selected = it }
        }
        onNodeWithTag("smart-playlist-reference-open").performClick()
        onNodeWithTag("smart-playlist-reference-self").assertDoesNotExist()
        onNodeWithTag("smart-playlist-reference-search").performTextInput("Bob")
        onNodeWithTag("smart-playlist-reference-a").assertDoesNotExist()
        onNodeWithTag("smart-playlist-reference-b").performClick()
        runOnIdle { assertEquals("b", selected) }
    }

    @Test fun failedLookupPreservesTheExistingIdAndOffersRetry() = runComposeUiTest {
        var retry = 0
        var selections = 0
        setContent {
            SmartPlaylistReferencePicker("missing-id", emptyList(), false, true, null,
                { retry++ }, NaviampColors()) { selections++ }
        }
        onNodeWithText("missing-id").assertExists()
        onNodeWithTag("smart-playlist-reference-open").performClick()
        onNodeWithText("Could not load playlists.").assertExists()
        onNodeWithText("Try again").performClick()
        runOnIdle { assertEquals(1, retry); assertEquals(0, selections) }
    }
}
