package app.naviamp.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampCoreNowPlayingSwipeTest {
    private val sheetTag = "now-playing-sheet"

    @Test
    fun phonePlayerSlidesOverTheBrowseScreenAndKeepsItsScrollPosition() = runDesktopComposeUiTest(400, 540) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val core = NaviampCore.create(scope, fakeCoreServices())
        val open = mutableStateOf(false)
        var closes = 0
        val shell = core.actions.shell
        val actions = shell.copy(
            navigationActions = shell.navigationActions.copy(
                onOpenNowPlaying = { open.value = true },
                onCloseNowPlaying = { closes++; open.value = false },
            ),
        )
        try {
            setContent {
                NaviampSharedAppShell(
                    uiState = NaviampAppShellUiState(
                        connectionSettings = NaviampConnectionSettingsUi(
                            connection = NaviampShellConnectionUi(connected = true),
                        ),
                        shellChrome = NaviampShellChromeUi(selectedRoute = SharedRoute.Settings, nowPlayingOpen = open.value),
                        nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused"),
                    ),
                    actions = actions,
                    syncActions = core.actions.settingsSync,
                )
            }
            waitForIdle()
            onNodeWithTag(sheetTag).assertDoesNotExist()
            val settings = onNode(
                hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            )
            settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
            waitForIdle()
            val scrolled = settings.verticalScroll()
            assertTrue(scrolled > 0f)

            onNode(hasText("Song")).performClick()
            waitForIdle()
            assertEquals(true, open.value)
            onNodeWithTag(sheetTag).assertIsDisplayed()
            onAllNodesWithContentDescription("Home").onFirst().assertIsNotDisplayed()

            onNodeWithTag(sheetTag).performTouchInput { swipe(Offset(centerX, 80f), Offset(centerX, 480f), 800) }
            waitForIdle()
            assertEquals(1, closes)
            onNodeWithTag(sheetTag).assertDoesNotExist()
            onAllNodesWithContentDescription("Home").onFirst().assertIsDisplayed()
            assertEquals(scrolled, settings.verticalScroll())
            onNode(hasText("Song") and hasAnyAncestor(hasTestTag(sheetTag))).assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.verticalScroll(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}
