package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds
import app.naviamp.domain.settings.InterfaceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampTelevisionHomePolicyTest {
    @Test
    fun focusingTheAlreadySelectedHomeTabDoesNotCloseTransientHomeContent() {
        assertFalse(televisionNavigationFocusChangesRoute(SharedRoute.Home, SharedRoute.Home))
        assertTrue(televisionNavigationFocusChangesRoute(SharedRoute.Library, SharedRoute.Home))
    }

    @Test
    fun preservesEveryVisibleSharedSectionInItsConfiguredOrder() {
        val sections = listOf(
            section(HomeSectionIds.RandomAlbums),
            section(HomeSectionIds.NavibeatMixes),
            section(HomeSectionIds.RecentAlbums),
            section(HomeSectionIds.RecentPlaylists),
            section(HomeSectionIds.RecentlyPlayed),
            section(HomeSectionIds.SimilarToStarredTracks),
            section(HomeSectionIds.MixesForYou),
            section(HomeSectionIds.RecentlyAdded),
        )

        assertEquals(
            listOf(
                HomeSectionIds.RandomAlbums,
                HomeSectionIds.NavibeatMixes,
                HomeSectionIds.RecentAlbums,
                HomeSectionIds.RecentPlaylists,
                HomeSectionIds.RecentlyPlayed,
                HomeSectionIds.SimilarToStarredTracks,
                HomeSectionIds.MixesForYou,
                HomeSectionIds.RecentlyAdded,
            ),
            televisionHomeSections(sections).map { it.id },
        )
    }

    @Test
    fun ignoresHiddenAndEmptySectionsButIncludesEveryAvailableKind() {
        val sections = listOf(
            section(HomeSectionIds.RecentRadio, visible = false),
            section(HomeSectionIds.RecentlyPlayed, itemCount = 0),
            section(HomeSectionIds.MixBuilders),
            section(HomeSectionIds.Stations),
            section(HomeSectionIds.RecentlyAdded),
        )

        assertEquals(
            listOf(HomeSectionIds.Stations, HomeSectionIds.RecentlyAdded),
            televisionHomeSections(sections).map { it.id },
        )
    }

    @Test
    fun televisionSettingsOmitMixBuildersWithoutRemovingTheirStandardAppOrder() {
        val settings = InterfaceSettings(
            homeSectionOrder = listOf(
                HomeSectionIds.RecentlyPlayed,
                HomeSectionIds.MixBuilders,
                HomeSectionIds.RecentAlbums,
            ),
        )
        val televisionSections = settings.televisionHomeSectionOptions()

        assertEquals(false, televisionSections.any { it.id == HomeSectionIds.MixBuilders })

        val reordered = televisionSections.moveHomeSectionItem(0, 1)
        val updated = settings.withOrderedTelevisionHomeSections(reordered)
        assertEquals(HomeSectionIds.MixBuilders, updated.homeSectionOrder[1])
    }

    @Test
    fun movingSectionScrollsOnlyWhenItLeavesTheVisibleSettingsWindow() {
        assertEquals(null, televisionHomeMoveScrollAnchor(5, listOf(3, 4, 5, 6)))
        assertEquals(2, televisionHomeMoveScrollAnchor(2, listOf(3, 4, 5, 6)))
        assertEquals(4, televisionHomeMoveScrollAnchor(7, listOf(3, 4, 5, 6)))
    }

    @Test
    fun capsItemsWithoutOverridingASmallerSharedLimit() {
        val sections = listOf(
            section(HomeSectionIds.RecentlyPlayed, itemCount = 40),
            section(HomeSectionIds.RecentlyAdded, itemCount = 12, homeItemLimit = 7),
        )

        assertEquals(listOf(30, 7), televisionHomeSections(sections).map { it.items.size })
    }

    private fun section(
        id: String,
        visible: Boolean = true,
        itemCount: Int = 1,
        homeItemLimit: Int? = null,
    ) = SharedHomeCollectionSectionUi(
        id = id,
        title = id,
        items = List(itemCount) { index -> item("$id-$index") },
        visible = visible,
        homeItemLimit = homeItemLimit,
    )

    private fun item(id: String): SharedHomeCollectionItemUi {
        val mediaItem = SharedMediaItemUi(id = id, title = id, subtitle = "")
        return SharedHomeCollectionItemUi(
            mediaItem = mediaItem,
            mediaKind = SharedMediaItemKind.Album,
            action = SharedHomeCollectionItemAction.OpenAlbum,
        )
    }
}
