package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        onNodeWithText("Refresh delay").assertExists()
        onNodeWithText("Use a valid duration such as 12h, 1d, or 1w.").assertExists()
    }
}

private fun testLibraries() = listOf(
    ConnectionFormMusicFolder(id = "2", name = "Ambient"),
    ConnectionFormMusicFolder(id = "4", name = "Rock"),
)
