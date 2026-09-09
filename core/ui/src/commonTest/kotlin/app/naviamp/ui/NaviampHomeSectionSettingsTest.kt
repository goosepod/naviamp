package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.homeSectionPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampHomeSectionSettingsTest {
    @Test
    fun sharedCatalogContainsEveryHomeSectionInDefaultOrder() {
        val options = InterfaceSettings().orderedHomeScreenSectionOptions()

        assertEquals(18, options.size)
        assertEquals(HomeSectionIds.FavoriteArtists, options.first().id)
        assertEquals(HomeSectionIds.Decade, options.last().id)
    }

    @Test
    fun visibilityChangesWithoutChangingOrderOrOtherPresentationSettings() {
        val initial = InterfaceSettings()
        val hidden = initial.withHomeScreenSectionVisible(HomeSectionIds.RecentlyAdded, false)

        assertFalse(hidden.homeSectionPresentation(HomeSectionIds.RecentlyAdded).visible)
        assertEquals(initial.orderedHomeScreenSectionOptions(), hidden.orderedHomeScreenSectionOptions())
        assertTrue(hidden.homeSectionPresentation(HomeSectionIds.RecentAlbums).visible)
    }

    @Test
    fun orderedSectionsPreserveUnknownPersistedIds() {
        val initial = InterfaceSettings(homeSectionOrder = listOf("future-section", HomeSectionIds.RecentAlbums))
        val reordered = initial.orderedHomeScreenSectionOptions().moveHomeSectionItem(0, 1)

        val updated = initial.withOrderedHomeScreenSections(reordered)

        assertEquals("future-section", updated.homeSectionOrder.last())
        assertEquals(reordered.map { it.id }, updated.homeSectionOrder.dropLast(1))
    }
}
