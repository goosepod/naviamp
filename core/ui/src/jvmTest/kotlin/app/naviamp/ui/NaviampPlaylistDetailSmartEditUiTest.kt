package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import app.naviamp.domain.smartplaylist.editingResult
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampPlaylistDetailSmartEditUiTest {
    @Test
    fun ownedReadonlySmartPlaylistShowsManageActions() = runComposeUiTest {
        val playlist = SharedMediaItemUi(
            id = "smart-1",
            title = "Work Ambient",
            subtitle = "0 tracks",
            isSmartPlaylist = true,
            canEditPlaylist = false,
            canManagePlaylist = true,
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
                    onRefresh = {},
                    onSortModeChanged = {},
                    smartPlaylist = testSmartPlaylistActions(),
                ),
                playlistChoices = emptyList(),
            )
        }

        onAllNodesWithContentDescription("Edit smart playlist")[0].assertExists()
        onAllNodesWithContentDescription("Delete playlist")[0].assertExists()
    }

    @Test
    fun unownedReadonlySmartPlaylistHidesManageActions() = runComposeUiTest {
        val playlist = SharedMediaItemUi(
            id = "smart-2",
            title = "Shared Smart Playlist",
            subtitle = "0 tracks",
            isSmartPlaylist = true,
            canEditPlaylist = false,
            canManagePlaylist = false,
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
                    onRefresh = {},
                    onSortModeChanged = {},
                    smartPlaylist = testSmartPlaylistActions(),
                ),
                playlistChoices = emptyList(),
            )
        }

        assertTrue(onAllNodesWithContentDescription("Edit smart playlist").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithContentDescription("Delete playlist").fetchSemanticsNodes().isEmpty())
    }

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
                    onRefresh = {},
                    onSortModeChanged = {},
                    smartPlaylist = testSmartPlaylistActions(
                        onLoad = {
                            definitionLoaded = true
                            testSmartPlaylistDefinition()
                        },
                    ),
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
    fun unsupportedDefinitionOpensInspectorWithoutSavingAndKeepsOwnerDelete() = runComposeUiTest {
        val original = """{"all":[{"unknown":{"custom":"original"}}],"futureOption":true}"""
        var writes = 0
        val playlist = SharedMediaItemUi("smart-1", "Original", "", isSmartPlaylist = true, canManagePlaylist = true)
        setContent {
            NaviampPlaylistDetailContent(NaviampColors(),
                NaviampPlaylistDetailScreenUi(selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist, emptyList())),
                testPlaylistDetailActions(),
                NaviampPlaylistsActions({}, {}, testSmartPlaylistActions(
                    onInspect = { SmartPlaylistEditingResult.ReadOnly(original) },
                    onUpdate = { _, _ -> writes++ })), emptyList())
        }
        onAllNodesWithContentDescription("Edit smart playlist")[0].performClick()
        onNodeWithTag("smart-playlist-original-definition").assertTextEquals(original)
        onNodeWithTag(SmartPlaylistSaveTestTag).assertDoesNotExist()
        onNodeWithText("Close").performClick()
        onAllNodesWithContentDescription("Delete playlist")[0].assertExists()
        assertEquals(0, writes)
    }

    @Test
    fun unsupportedJsonImportPreservesThePreviouslySupportedDraft() = runComposeUiTest {
        val initial = app.naviamp.domain.smartplaylist.SmartPlaylistDraft.fromDefinition(testSmartPlaylistDefinition())
        val original = """{"name":"Unsupported","all":[{"newOperator":{"newField":[1,2,3]}}]}"""
        var saved: SmartPlaylistDefinition? = null
        setContent {
            SmartPlaylistBuilderDialog(NaviampColors(), initialDraft = initial,
                onDismissRequest = {}, onSave = { saved = it })
        }
        onNodeWithText("Import JSON").performScrollTo().performClick()
        onNodeWithText("Paste .nsp or Navidrome smart playlist JSON").performScrollTo().performTextInput(original)
        onNodeWithText("Validate and load").performScrollTo().performClick()
        onNodeWithTag("smart-playlist-original-definition").assertTextEquals(original)
        assertEquals(null, saved)
        onNodeWithText("Close").performClick()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { saved != null }
        assertEquals("Work Ambient", saved!!.name)
        assertEquals(testSmartPlaylistDefinition().rules, saved!!.rules)
    }

    @Test
    fun deniedRulesHaveAnExplicitExplanationAndNoEditor() = runComposeUiTest {
        val playlist = SharedMediaItemUi("smart-1", "Denied", "", isSmartPlaylist = true, canManagePlaylist = true)
        setContent {
            NaviampPlaylistDetailContent(NaviampColors(),
                NaviampPlaylistDetailScreenUi(selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist, emptyList())),
                testPlaylistDetailActions(), NaviampPlaylistsActions({}, {}, testSmartPlaylistActions(
                    onInspect = { SmartPlaylistEditingResult.Unavailable(app.naviamp.domain.smartplaylist.SmartPlaylistDefinitionUnavailable.PermissionDenied) })), emptyList())
        }
        onAllNodesWithContentDescription("Edit smart playlist")[0].performClick()
        onNodeWithTag("smart-playlist-inspection-status").assertTextEquals("You do not have permission to view this playlist’s rules.")
        onNodeWithTag("smart-playlist-original-definition").assertDoesNotExist()
        onNodeWithTag(SmartPlaylistSaveTestTag).assertDoesNotExist()
    }

    @Test
    fun unsupportedRulesAfterPasswordRecoveryOpenInspector() = runComposeUiTest {
        var passwordUsed: String? = null
        val playlist = SharedMediaItemUi("smart-1", "Original", "", isSmartPlaylist = true, canManagePlaylist = true)
        setContent {
            NaviampPlaylistDetailContent(NaviampColors(),
                NaviampPlaylistDetailScreenUi(selectedPlaylist = playlist, detail = SharedPlaylistDetailUi(playlist, emptyList())),
                testPlaylistDetailActions(), NaviampPlaylistsActions({}, {}, testSmartPlaylistActions(
                    onInspect = { error("Navidrome returned HTTP 401.") },
                    onInspectWithPassword = { _, password -> passwordUsed = password; SmartPlaylistEditingResult.ReadOnly("nested original") })), emptyList())
        }
        onAllNodesWithContentDescription("Edit smart playlist")[0].performClick()
        onNodeWithTag(SmartPlaylistLoadPasswordFieldTestTag).performTextInput("secret")
        onNodeWithTag(SmartPlaylistLoadPasswordConfirmTestTag).performClick()
        waitUntil { passwordUsed == "secret" }
        onNodeWithTag("smart-playlist-original-definition").assertTextEquals("nested original")
        onNodeWithTag(SmartPlaylistSaveTestTag).assertDoesNotExist()
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
                    onRefresh = {},
                    onSortModeChanged = {},
                    smartPlaylist = testSmartPlaylistActions(
                        onLoad = { error("Navidrome returned HTTP 401.") },
                        onLoadWithPassword = { _, password ->
                            retriedPassword = password
                            testSmartPlaylistDefinition()
                        },
                    ),
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

private fun testSmartPlaylistActions(
    onLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition = { testSmartPlaylistDefinition() },
    onLoadWithPassword: suspend (SharedMediaItemUi, String) -> SmartPlaylistDefinition = { playlist, _ -> onLoad(playlist) },
    onInspect: suspend (SharedMediaItemUi) -> SmartPlaylistEditingResult = { onLoad(it).editingResult() },
    onInspectWithPassword: suspend (SharedMediaItemUi, String) -> SmartPlaylistEditingResult = { item, password -> onLoadWithPassword(item, password).editingResult() },
    onUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit = { _, _ -> },
) = NaviampSmartPlaylistActions(
    onInspect = onInspect,
    onInspectWithPassword = onInspectWithPassword,
    onSave = {},
    onUpdate = onUpdate,
    onSaveWithPassword = { _, _ -> },
    onUpdateWithPassword = { _, _, _ -> },
    onLoad = onLoad,
    onLoadWithPassword = onLoadWithPassword,
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
