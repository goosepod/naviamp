package app.naviamp.ui

import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampPopupToggleTest {
    @Test fun firstHoverOnEachItemKeepsTheMenuInPlace() = runComposeUiTest {
        setContent {
            NaviampRowOverflowMenu(NaviampColors(), List(6) {
                NaviampRowMenuItem("Action $it", NaviampTransportIcons.MoreVertical, {})
            })
        }
        onNodeWithContentDescription("More actions").performMouseInput { click() }
        val initialBounds = onNodeWithText("Action 0").fetchSemanticsNode().boundsInRoot
        repeat(6) { index ->
            onNodeWithText("Action $index").performMouseInput { moveTo(center) }
            waitForIdle()
            assertEquals(initialBounds, onNodeWithText("Action 0").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun overflowButtonClosesAnAlreadyOpenMenu() = runComposeUiTest {
        setContent {
            NaviampRowOverflowMenu(NaviampColors(), listOf(
                NaviampRowMenuItem(label = "Test action", icon = NaviampTransportIcons.MoreVertical, onClick = {}),
            ))
        }
        onNodeWithContentDescription("More actions").performMouseInput { click() }
        onNodeWithText("Test action").assertIsDisplayed()
        onNodeWithContentDescription("More actions").performMouseInput { click() }
        onNodeWithText("Test action").assertDoesNotExist()
    }
}
