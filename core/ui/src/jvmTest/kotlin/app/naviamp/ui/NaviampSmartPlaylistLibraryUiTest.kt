package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
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
}

private fun testLibraries() = listOf(
    ConnectionFormMusicFolder(id = "2", name = "Ambient"),
    ConnectionFormMusicFolder(id = "4", name = "Rock"),
)
