package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampTelevisionHomePolicyTest {
    @Test
    fun selectsOneRailPerTelevisionPriorityGroupInStableOrder() {
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
                HomeSectionIds.RecentlyPlayed,
                HomeSectionIds.RecentAlbums,
                HomeSectionIds.SimilarToStarredTracks,
                HomeSectionIds.NavibeatMixes,
                HomeSectionIds.RecentPlaylists,
            ),
            televisionHomeSections(sections).map { it.id },
        )
    }

    @Test
    fun ignoresHiddenEmptyAndNonTelevisionSections() {
        val sections = listOf(
            section(HomeSectionIds.RecentRadio, visible = false),
            section(HomeSectionIds.RecentlyPlayed, itemCount = 0),
            section(HomeSectionIds.Stations),
            section(HomeSectionIds.RecentlyAdded),
        )

        assertEquals(
            listOf(HomeSectionIds.RecentlyAdded),
            televisionHomeSections(sections).map { it.id },
        )
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
