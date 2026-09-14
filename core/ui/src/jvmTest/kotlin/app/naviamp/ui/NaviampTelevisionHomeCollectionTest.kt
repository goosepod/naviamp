package app.naviamp.ui

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionHomeCollectionTest {
    @Test
    fun homeRailExposesRemoteReachableViewAllAction() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val section = section()
        setContent {
            TelevisionHome(
                home = NaviampHomeScreenUi(content = SharedHomeUi(collectionSections = listOf(section))),
                colors = NaviampColors.Dark,
                actions = homeActions(onCollectionSelected = opened::add),
                mediaActions = mediaActions(),
            )
        }

        onNodeWithTag("$TelevisionHomeViewAllTestTagPrefix${section.id}").performClick()

        assertEquals(listOf(section.id), opened)
    }

    @Test
    fun dedicatedCollectionFocusesFirstItemAndReturnsToBackControl() = runComposeUiTest {
        mainClock.autoAdvance = false
        var backCount = 0
        val selections = mutableListOf<NaviampMediaItemActionRequest>()
        setContent {
            TelevisionHomeCollection(
                page = SharedHomeCollectionPageUi(section()),
                colors = NaviampColors.Dark,
                actions = homeActions(onCollectionBack = { backCount += 1 }),
                mediaActions = mediaActions(onMediaItemAction = selections::add),
                topNavigationFocusRequester = FocusRequester(),
            )
        }
        mainClock.advanceTimeBy(300)

        onNodeWithTag(TelevisionHomeCollectionTestTag).assertExists()
        onNodeWithTag("${TelevisionHomeCollectionItemTestTagPrefix}0").assertIsFocused().performClick()
        assertEquals(1, selections.size)

        onNodeWithTag("${TelevisionHomeCollectionItemTestTagPrefix}0").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        onNodeWithTag(TelevisionHomeCollectionBackTestTag).assertIsFocused().performClick()
        assertEquals(1, backCount)
    }

    private fun section() = SharedHomeCollectionSectionUi(
        id = "recent-albums",
        title = "Recent albums",
        items = listOf(
            SharedHomeCollectionItemUi(
                mediaItem = SharedMediaItemUi("album-1", "Album", "Artist"),
                mediaKind = SharedMediaItemKind.Album,
                action = SharedHomeCollectionItemAction.OpenAlbum,
            ),
        ),
    )

    private fun homeActions(
        onCollectionSelected: (String) -> Unit = {},
        onCollectionBack: () -> Unit = {},
    ) = NaviampHomeActions(
        onRefresh = {},
        onRecentRadioSelected = {},
        onInternetRadioStationSelected = {},
        onMixBuilderSelected = {},
        onStationSelected = {},
        onSonicDiscoveryTrackAction = {},
        onRecentlyPlayedTrackAction = {},
        onCollectionSelected = onCollectionSelected,
        onCollectionBack = onCollectionBack,
        onCollectionPageLayoutChanged = { _, _ -> },
    )

    private fun mediaActions(
        onMediaItemAction: (NaviampMediaItemActionRequest) -> Unit = {},
    ) = NaviampMediaActions(
        onTrackAction = {},
        onMediaItemAction = onMediaItemAction,
    )
}
