package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampPlaylistDetailSmartEditUiTest {
    @Test
    fun headerBrainOpensSmartPlaylistEditor() = runComposeUiTest {
        var definitionLoaded = false
        val playlist = SharedMediaItemUi(
            id = "smart-1",
            title = "Work Ambient",
            subtitle = "12 tracks",
            isSmartPlaylist = true,
        )

        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist = playlist, tracks = emptyList()),
                ),
                actions = testPlaylistDetailActions(),
                playlistsActions = NaviampPlaylistsActions(
                    onSmartPlaylistLoad = {
                        definitionLoaded = true
                        testSmartPlaylistDefinition()
                    },
                ),
                playlistChoices = emptyList(),
            )
        }

        onAllNodesWithContentDescription("Edit smart playlist")[0].performClick()
        waitUntil { definitionLoaded }
        onNodeWithText("Details").assertExists()
        assertTrue(definitionLoaded)
    }

    @Test
    fun expiredNativeTokenPromptsForPasswordAndRetriesEdit() = runComposeUiTest {
        var retriedPassword: String? = null
        val playlist = SharedMediaItemUi(
            id = "smart-1",
            title = "Work Ambient",
            subtitle = "12 tracks",
            isSmartPlaylist = true,
        )

        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist = playlist, tracks = emptyList()),
                ),
                actions = testPlaylistDetailActions(),
                playlistsActions = NaviampPlaylistsActions(
                    onSmartPlaylistLoad = { error("Navidrome returned HTTP 401.") },
                    onSmartPlaylistLoadWithPassword = { _, password ->
                        retriedPassword = password
                        testSmartPlaylistDefinition()
                    },
                ),
                playlistChoices = emptyList(),
            )
        }

        onAllNodesWithContentDescription("Edit smart playlist")[0].performClick()
        onNodeWithText("Navidrome password").assertExists()
        onNodeWithTag(SmartPlaylistLoadPasswordFieldTestTag).performTextInput("secret")
        onNodeWithTag(SmartPlaylistLoadPasswordConfirmTestTag).performClick()
        waitUntil { retriedPassword == "secret" }
        onNodeWithText("Details").assertExists()
    }
}

private fun testPlaylistDetailActions() = NaviampPlaylistDetailActions(
    onBack = {},
    onPlaylistAction = {},
    onUpdateStandardPlaylist = { _, _ -> },
    onTrackAction = {},
)

private fun testSmartPlaylistDefinition() = SmartPlaylistDefinition(
    name = "Work Ambient",
    rules = listOf(
        SmartPlaylistCondition(
            operator = SmartPlaylistOperator.Is,
            field = SmartPlaylistFields.Artist,
            value = SmartPlaylistValue.Text("Ascendant"),
        ),
    ),
)
