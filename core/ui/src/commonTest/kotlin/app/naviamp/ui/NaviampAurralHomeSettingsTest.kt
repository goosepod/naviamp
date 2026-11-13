package app.naviamp.ui

import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.settings.*
import kotlin.test.*

class NaviampAurralHomeSettingsTest {
    @Test fun addingAurralToAnExistingHomeOrderDefaultsToTopButPreservesAnExplicitPosition() {
        val previousOrder = listOf(HomeSectionIds.RecentRadio, HomeSectionIds.FavoriteArtists)
        val settings = InterfaceSettings(homeSectionOrder = previousOrder)
        assertEquals(listOf(HomeSectionIds.Aurral) + previousOrder, settings.resolvedHomeSectionOrder().take(3))
        val moved = settings.withHomeSectionOrder(previousOrder + HomeSectionIds.Aurral)
        assertEquals(previousOrder + HomeSectionIds.Aurral, moved.resolvedHomeSectionOrder().take(3))
    }

    @Test fun aurralIsVisibleByDefaultForOldSettingsAndUsesTheSamePortableHomePreferences() {
        val old = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{}}}""").preferences.interfaceSettings
        assertTrue(old.homeSectionPresentation(HomeSectionIds.Aurral).visible)
        assertEquals(HomeSectionIds.Aurral, old.resolvedHomeSectionOrder().first())
        val settings = old.withHomeScreenSectionVisible(HomeSectionIds.Aurral, false)
            .withHomeSectionOrder(listOf(HomeSectionIds.RecentAlbums, HomeSectionIds.Aurral, "future-section"))
        val restored = SettingsSyncJson.decode(SettingsSyncJson.encode(SettingsSyncDocument(
            preferences = SettingsSyncPreferences(interfaceSettings = settings)))).preferences.interfaceSettings
        assertFalse(restored.homeSectionPresentation(HomeSectionIds.Aurral).visible)
        assertEquals(settings.homeSectionOrder, restored.homeSectionOrder)
    }

    @Test fun homeMappingCarriesSavedDiscoveryPreferences() {
        val settings = InterfaceSettings().withHomeScreenSectionVisible(HomeSectionIds.Aurral, false)
            .withHomeSectionOrder(listOf(HomeSectionIds.RecentAlbums, HomeSectionIds.Aurral))
        val mapped = HomeContent().toSharedHomeUi(coverArtUrl = { null }, interfaceSettings = settings)
        assertFalse(mapped.aurralHomeVisible)
        assertEquals(settings.resolvedHomeSectionOrder(), mapped.homeSectionOrder)
    }

    @Test fun discoveryUsesTheSharedOrderAndVisibilityWithoutFabricatingMediaItems() {
        val collection = SharedHomeCollectionSectionUi(id = HomeSectionIds.RecentAlbums, title = "Albums", items = emptyList())
        val home = SharedHomeUi(collectionSections = listOf(collection))
        assertEquals(listOf(HomeSectionIds.Aurral, HomeSectionIds.RecentAlbums), home.homeScreenSlots(true).map { it.id })
        val moved = home.copy(homeSectionOrder = listOf(HomeSectionIds.RecentAlbums, HomeSectionIds.Aurral))
        assertEquals(listOf(HomeSectionIds.RecentAlbums, HomeSectionIds.Aurral), moved.homeScreenSlots(true).map { it.id })
        assertNull(moved.homeScreenSlots(true).last().collection)
        assertEquals(listOf(HomeSectionIds.RecentAlbums), moved.copy(aurralHomeVisible = false).homeScreenSlots(true).map { it.id })
        assertEquals(listOf(HomeSectionIds.RecentAlbums), moved.homeScreenSlots(false).map { it.id })
    }
}
