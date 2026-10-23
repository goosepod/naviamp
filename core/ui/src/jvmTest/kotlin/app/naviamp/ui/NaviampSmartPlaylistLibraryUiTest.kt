package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import app.naviamp.domain.smartplaylist.*
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistConditionDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFieldCatalog
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistLibraryUiTest {
    @Test fun lateLibraryNamesPreserveEditsAndShowNamesForLibraryConditions() = runComposeUiTest {
        val libraries = mutableStateOf(emptyList<ConnectionFormMusicFolder>())
        val selected = mutableStateOf(emptyList<String>())
        var saved: SmartPlaylistDefinition? = null
        val definition = SmartPlaylistDefinition("Original name", libraryIds = listOf("1"), rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.LibraryId, SmartPlaylistValue.Number(1))))
        val draft = SmartPlaylistDraft.fromDefinition(definition)
        setContent { SmartPlaylistBuilderDialog(NaviampColors(), draft,
            availableLibraries = libraries.value, selectedConnectionLibraryIds = selected.value,
            onDismissRequest = {}, onSave = { saved = it }) }
        onNodeWithText("Original name").performTextReplacement("Keep my edit")
        runOnIdle {
            libraries.value = listOf(ConnectionFormMusicFolder("1", "Music Library"), ConnectionFormMusicFolder("2", "Classical"))
            selected.value = listOf("1", "2")
        }
        onNodeWithText("Keep my edit").assertExists()
        onNodeWithText("Music Library").performScrollTo().assertExists()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }
        assertEquals("Keep my edit", saved?.name)
        assertEquals(definition.rules, saved?.rules)
        assertEquals(listOf("1"), saved?.libraryIds)
    }

    @Test
    fun singleSelectedLibraryKeepsLibrarySelectorHidden() = runComposeUiTest {
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                availableLibraries = testLibraries(),
                selectedConnectionLibraryIds = listOf("2"),
                onDismissRequest = {},
                onSave = {},
            )
        }

        assertEquals(0, onAllNodesWithText("Libraries").fetchSemanticsNodes().size)
    }

    @Test
    fun multipleSelectedLibrariesShowsNamesInSelector() = runComposeUiTest {
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                availableLibraries = testLibraries(),
                selectedConnectionLibraryIds = listOf("2", "4"),
                onDismissRequest = {},
                onSave = {},
            )
        }

        onNodeWithTag("smart-playlist-options-toggle").performScrollTo().performClick()
        onNodeWithText("Libraries").assertExists()
        onNodeWithText("Ambient").assertExists()
        onNodeWithText("Rock").assertExists()
    }

    @Test
    fun genreSuggestionsStayHiddenUntilTypingAndUseTheFullOntology() = runComposeUiTest {
        val genreField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Genre }
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                initialDraft = SmartPlaylistDraft(
                    conditions = listOf(
                        SmartPlaylistConditionDraft(
                            field = genreField,
                            operator = SmartPlaylistOperator.Contains,
                        ),
                    ),
                ),
                genreCatalog = listOf("dream pop", "jazz", "rock").map {
                    app.naviamp.domain.smartplaylist.SmartPlaylistGenreOption(it)
                },
                onDismissRequest = {},
                onSave = {},
            )
        }

        assertEquals(0, onAllNodesWithText("Dream Pop").fetchSemanticsNodes().size)
        onNodeWithTag(SmartPlaylistGenreValueTestTag).performTextInput("dream")
        onNodeWithText("Dream Pop").performClick()
        onNodeWithTag(SmartPlaylistGenreValueTestTag).assertTextContains("Dream Pop")
    }

    @Test
    fun navidrome064FieldAndRefreshDelayValidationAreVisible() = runComposeUiTest {
        val albumDateAdded = SmartPlaylistFieldCatalog.fields.first {
            it.field == SmartPlaylistFields.AlbumDateAdded
        }
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                initialDraft = SmartPlaylistDraft(
                    conditions = listOf(
                        SmartPlaylistConditionDraft(
                            field = albumDateAdded,
                            operator = SmartPlaylistOperator.InTheLast,
                            value = "30",
                        ),
                    ),
                    refreshDelay = "tomorrow",
                ),
                onDismissRequest = {},
                onSave = {},
            )
        }

        onNodeWithText("Album Date Added").assertExists()
        onNodeWithText("Options").performScrollTo().performClick()
        onNodeWithText("Refresh delay").assertExists()
        onNodeWithText("Use a valid duration such as 12h, 1d, or 1w.").assertExists()
    }
}

private fun testLibraries() = listOf(
    ConnectionFormMusicFolder(id = "2", name = "Ambient"),
    ConnectionFormMusicFolder(id = "4", name = "Rock"),
)
