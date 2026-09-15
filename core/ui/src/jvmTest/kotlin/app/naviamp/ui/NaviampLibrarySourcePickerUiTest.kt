package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampLibrarySourcePickerUiTest {
    @Test
    fun pickerShowsOnlyFriendlyLibraryNamesAndDispatchesInternalSelection() = runDesktopComposeUiTest {
        val toggled = mutableListOf<String>()
        setContent {
            val colors = NaviampColors.Dark
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = colors.background,
                    surface = colors.controlSurface,
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    onBackground = colors.primaryText,
                    onSurface = colors.primaryText,
                ),
                typography = rememberNaviampTypography(),
            ) {
                NaviampLibrarySourcePicker(
                    colors = colors,
                    picker = NaviampLibrarySourcePickerUi(
                        visible = true,
                        libraries = listOf(
                            ConnectionFormMusicFolder("1", "Music", defaultSelected = true),
                            ConnectionFormMusicFolder("2", "Classical"),
                        ),
                        selectedIds = listOf("1"),
                    ),
                    actions = NaviampLibraryActions(
                        onViewChanged = {}, onQueryChanged = {}, onRefresh = {}, onLoadMore = {},
                        onJumpToLetter = {}, onTrackAction = {}, onToggleSource = toggled::add,
                    ),
                )
            }
        }

        onNodeWithText("Music").assertIsDisplayed()
        onNodeWithText("Classical").assertIsDisplayed().performClick()
        onAllNodesWithText("1", substring = false).assertCountEquals(0)
        onAllNodesWithText("2", substring = false).assertCountEquals(0)
        assertEquals(listOf("2"), toggled)
    }
}
