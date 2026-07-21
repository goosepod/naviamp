package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                actions = NaviampPlaylistDetailActions(),
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
}

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
