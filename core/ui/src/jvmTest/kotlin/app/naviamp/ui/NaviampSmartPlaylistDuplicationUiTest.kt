package app.naviamp.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinitionUnavailable
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistGroup
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistSort
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistDuplicationUiTest {
    private val source = SharedMediaItemUi("smart-1", "Evening", "12 tracks", isSmartPlaylist = true, canManagePlaylist = true)

    @Test
    fun listDuplicateCreatesAnIndependentDynamicCopyWithoutTouchingTheSource() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions()
        showPlaylists(fake, playlists = listOf(source, SharedMediaItemUi("other", "Evening copy", "")))

        openListDuplicate()

        onNodeWithText("Duplicate smart playlist").assertIsDisplayed()
        onNodeWithText("Evening copy 2").assertExists()
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { fake.created.isNotEmpty() }
        assertEquals(listOf(richDefinition.copy(name = "Evening copy 2")), fake.created)
        assertTrue(fake.updated.isEmpty())
        assertEquals(listOf("smart-1"), fake.inspected)
        onNodeWithText("Duplicate smart playlist").assertDoesNotExist()
    }

    @Test
    fun cancellingADuplicateCreatesNothing() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions()
        showPlaylists(fake)

        openListDuplicate()
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Duplicate smart playlist").assertDoesNotExist()
        assertTrue(fake.created.isEmpty())
        assertTrue(fake.updated.isEmpty())
    }

    @Test
    fun createFailureKeepsTheDraftAndAllowsARetry() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions(createFailures = mutableListOf(IllegalStateException("You do not have permission to create playlists.")))
        showPlaylists(fake)

        openListDuplicate()
        onNodeWithText("Evening copy").performScrollTo().performTextInput(" B")
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { onAllNodesWithText("You do not have permission to create playlists.").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Duplicate smart playlist").assertExists()
        assertTrue(fake.created.isEmpty())

        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { fake.created.isNotEmpty() }
        assertEquals(richDefinition.rules, fake.created.single().rules)
        assertTrue(fake.updated.isEmpty())
    }

    @Test
    fun lookupFailureShowsAnErrorAndNeverFallsBackToCopyingTracks() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions(inspect = { error("Server unavailable.") })
        val mediaRequests = mutableListOf<NaviampMediaItemActionRequest>()
        showPlaylists(fake, onMediaAction = { mediaRequests += it })

        openListDuplicate()

        waitUntil { onAllNodesWithText("Server unavailable.").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Duplicate smart playlist").assertDoesNotExist()
        assertTrue(mediaRequests.isEmpty())
        assertTrue(fake.created.isEmpty())
    }

    @Test
    fun unsupportedOrDeniedDefinitionsUseTheSafeInspectionPolicy() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions(inspect = {
            SmartPlaylistEditingResult.Unavailable(SmartPlaylistDefinitionUnavailable.PermissionDenied)
        })
        showPlaylists(fake)

        openListDuplicate()

        onNodeWithTag("smart-playlist-inspection-status").assertExists()
        onNodeWithTag(SmartPlaylistSaveTestTag).assertDoesNotExist()
        assertTrue(fake.created.isEmpty())
    }

    @Test
    fun expiredSessionPromptsForPasswordAndContinuesTheDuplicate() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions(inspect = { error("Navidrome returned HTTP 401.") })
        showPlaylists(fake)

        openListDuplicate()
        onNodeWithTag(SmartPlaylistLoadPasswordFieldTestTag).performTextInput("secret")
        onNodeWithTag(SmartPlaylistLoadPasswordConfirmTestTag).performClick()

        waitUntil { onAllNodesWithText("Duplicate smart playlist").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("secret"), fake.inspectPasswords)
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { fake.created.isNotEmpty() }
        assertEquals("Evening copy", fake.created.single().name)
        assertTrue(fake.updated.isEmpty())
    }

    @Test
    fun detailDuplicateWorksForSharedPlaylistsAndCreatesInsteadOfUpdating() = runComposeUiTest {
        val fake = FakeSmartPlaylistActions()
        val shared = source.copy(canManagePlaylist = false)
        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(selectedPlaylist = shared, detail = SharedPlaylistDetailUi(shared, emptyList())),
                actions = NaviampPlaylistDetailActions(onBack = {}, onPlaylistAction = {}, onUpdateStandardPlaylist = { _, _, _ -> }, onTrackAction = {}),
                playlistsActions = NaviampPlaylistsActions({}, {}, fake.actions),
                playlistChoices = emptyList(),
            )
        }

        clickAction("Duplicate smart playlist")
        waitUntil { onAllNodesWithText("Evening copy").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitUntil { fake.created.isNotEmpty() }

        assertEquals(richDefinition.copy(name = "Evening copy"), fake.created.single())
        assertTrue(fake.updated.isEmpty())
    }

    private fun ComposeUiTest.showPlaylists(
        fake: FakeSmartPlaylistActions,
        playlists: List<SharedMediaItemUi> = listOf(source),
        onMediaAction: (NaviampMediaItemActionRequest) -> Unit = {},
    ) {
        setContent {
            NaviampPlaylistsContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistsScreenUi(playlists = playlists),
                actions = NaviampPlaylistsActions({}, {}, fake.actions),
                mediaActions = NaviampMediaActions(onTrackAction = {}, onMediaItemAction = onMediaAction),
                playlistChoices = emptyList(),
            )
        }
    }

    private fun ComposeUiTest.openListDuplicate() {
        onAllNodesWithContentDescription("More actions")[1].performClick()
        onNodeWithText("Duplicate smart playlist").performClick()
    }

    private fun ComposeUiTest.clickAction(label: String) {
        val visible = onAllNodesWithContentDescription(label).fetchSemanticsNodes()
        if (visible.isNotEmpty()) {
            onAllNodesWithContentDescription(label)[0].performClick()
        } else {
            onAllNodesWithContentDescription("More actions")[0].performClick()
            onNodeWithText(label).performClick()
        }
    }
}

private val richDefinition = SmartPlaylistDefinition(
    name = "Evening",
    comment = "Calm",
    match = SmartPlaylistMatch.Any,
    rules = listOf(
        SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
        SmartPlaylistGroup(
            SmartPlaylistMatch.All,
            listOf(
                SmartPlaylistCondition(SmartPlaylistOperator.Contains, SmartPlaylistFields.Artist, SmartPlaylistValue.Text("Ascendant")),
                SmartPlaylistCondition(SmartPlaylistOperator.InTheLast, SmartPlaylistFields.DateAdded, SmartPlaylistValue.Number(30)),
            ),
        ),
    ),
    sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Rating, descending = true), SmartPlaylistSort(SmartPlaylistFields.Title)),
    limitPercent = 40,
    isPublic = true,
    libraryIds = listOf("2"),
    refreshDelay = "2h",
)

private class FakeSmartPlaylistActions(
    val inspect: suspend (SharedMediaItemUi) -> SmartPlaylistEditingResult = {
        SmartPlaylistEditingResult.Editable(richDefinition, "{}")
    },
    val createFailures: MutableList<Throwable> = mutableListOf(),
) {
    val created = mutableListOf<SmartPlaylistDefinition>()
    val updated = mutableListOf<Pair<String, SmartPlaylistDefinition>>()
    val inspected = mutableListOf<String>()
    val inspectPasswords = mutableListOf<String>()

    val actions = NaviampSmartPlaylistActions(
        onSave = { definition -> createFailures.removeFirstOrNull()?.let { throw it }; created += definition },
        onUpdate = { item, definition -> updated += item.id to definition },
        onSaveWithPassword = { definition, _ -> created += definition },
        onUpdateWithPassword = { item, definition, _ -> updated += item.id to definition },
        onLoad = { error("Duplication must inspect, not load.") },
        onLoadWithPassword = { _, _ -> error("Duplication must inspect, not load.") },
        onInspect = { item -> inspected += item.id; inspect(item) },
        onInspectWithPassword = { _, password ->
            inspectPasswords += password
            SmartPlaylistEditingResult.Editable(richDefinition, "{}")
        },
    )
}
