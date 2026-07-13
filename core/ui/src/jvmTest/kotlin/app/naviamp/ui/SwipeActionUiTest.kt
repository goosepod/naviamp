package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.TrackSwipeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class SwipeActionUiTest {
    @Test
    fun visibleOverflowMenuHasAnAccessibleLabel() = runComposeUiTest {
        setContent {
            NaviampRowOverflowMenu(
                colors = NaviampColors(),
                items = listOf(
                    NaviampRowMenuItem("Play next", NaviampIcons.Queue, onClick = {}),
                ),
            )
        }

        onNodeWithContentDescription("More actions").assertExists()
    }

    @Test
    fun swipePastThresholdTriggersActionInItsDirection() = runComposeUiTest {
        val triggered = mutableListOf<String>()
        setContent {
            SwipeTestSurface(
                onSwipeRight = { triggered += "right" },
                onSwipeLeft = { triggered += "left" },
            )
        }

        onNodeWithTag(SwipeContentTag).performTouchInput {
            down(center)
            moveTo(Offset(right - 1f, center.y), delayMillis = 200)
            up()
        }
        onNodeWithTag(SwipeContentTag).performTouchInput {
            down(center)
            moveTo(Offset(1f, center.y), delayMillis = 200)
            up()
        }

        runOnIdle { assertEquals(listOf("right", "left"), triggered) }
    }

    @Test
    fun swipeBelowThresholdReturnsToRestWithoutTriggering() = runComposeUiTest {
        var triggerCount = 0
        setContent {
            SwipeTestSurface(
                onSwipeRight = { triggerCount += 1 },
                onSwipeLeft = { triggerCount += 1 },
            )
        }

        onNodeWithTag(SwipeContentTag).performTouchInput {
            down(center)
            moveTo(Offset(center.x + 20f, center.y))
            up()
        }

        runOnIdle { assertEquals(0, triggerCount) }
    }

    @Test
    fun cancelledSwipeDoesNotTriggerAction() = runComposeUiTest {
        var triggerCount = 0
        setContent {
            SwipeTestSurface(
                onSwipeRight = { triggerCount += 1 },
                onSwipeLeft = { triggerCount += 1 },
            )
        }

        onNodeWithTag(SwipeContentTag).performTouchInput {
            down(center)
            moveTo(Offset(right - 1f, center.y))
            cancel()
        }

        runOnIdle { assertEquals(0, triggerCount) }
    }

    @Test
    fun unavailableDirectionCannotTrigger() = runComposeUiTest {
        var rightTriggerCount = 0
        setContent {
            SwipeTestSurface(
                onSwipeRight = { rightTriggerCount += 1 },
                onSwipeLeft = null,
            )
        }

        onNodeWithTag(SwipeContentTag).performTouchInput {
            down(center)
            moveTo(Offset(1f, center.y), delayMillis = 200)
            up()
        }

        runOnIdle { assertEquals(0, rightTriggerCount) }
    }

    @Test
    fun removeIsOnlyAvailableForQueueItems() {
        val item = NaviampNowPlayingItemUi(
            id = nowPlayingQueueItemId(2),
            title = "Track",
            subtitle = "Artist",
        )
        var request: NowPlayingItemActionRequest? = null

        assertNull(
            nowPlayingSwipeActionVisual(
                action = TrackSwipeAction.Remove,
                item = item,
                queueContext = false,
                canToggleFavorite = true,
                onAddToPlaylist = {},
                onAction = {},
            ),
        )
        val queueRemove = nowPlayingSwipeActionVisual(
            action = TrackSwipeAction.Remove,
            item = item,
            queueContext = true,
            canToggleFavorite = true,
            onAddToPlaylist = {},
            onAction = { request = it },
        )

        assertNotNull(queueRemove).onTriggered()
        assertEquals(NowPlayingItemAction.RemoveFromQueue, request?.action)
        assertEquals(NowPlayingItemTarget.QueueIndex(2), request?.target)
    }
}

@Composable
private fun SwipeTestSurface(
    onSwipeRight: (() -> Unit)?,
    onSwipeLeft: (() -> Unit)?,
) {
    SwipeActionContainer(
        swipeRight = onSwipeRight?.let { action -> testVisual("Right", action) },
        swipeLeft = onSwipeLeft?.let { action -> testVisual("Left", action) },
    ) { modifier ->
        Box(modifier.testTag(SwipeContentTag).size(width = 300.dp, height = 48.dp))
    }
}

private fun testVisual(label: String, onTriggered: () -> Unit) = TrackSwipeActionVisual(
    label = label,
    icon = NaviampIcons.Queue,
    background = Color.Green,
    onTriggered = onTriggered,
)

private const val SwipeContentTag = "swipe-content"
