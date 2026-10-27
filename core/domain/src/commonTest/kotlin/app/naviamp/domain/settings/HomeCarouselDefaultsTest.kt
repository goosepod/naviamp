package app.naviamp.domain.settings

import kotlin.test.*

class HomeCarouselDefaultsTest {
    private val carousel = listOf(HomeSectionIds.FavoriteArtists, HomeSectionIds.MixesForYou,
        HomeSectionIds.MixBuilders, HomeSectionIds.RecentlyAdded, HomeSectionIds.RecentPlaylists)

    @Test fun newInstallsAndOlderExportsWithoutSavedLayoutsUseThePrimaryCarousels() {
        val fresh = InterfaceSettings()
        val oldExport = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{}}}""").preferences.interfaceSettings
        for (settings in listOf(fresh, oldExport)) {
            carousel.forEach { assertEquals(HomeSectionLayout.Carousel, settings.homeSectionPresentation(it).homeLayout) }
            assertEquals(HomeSectionLayout.Carousel, settings.homeSectionPresentation(HomeSectionIds.NavibeatMixes).homeLayout)
            assertEquals(HomeSectionLayout.List, settings.homeSectionPresentation(HomeSectionIds.RecentlyPlayed).homeLayout)
            assertEquals(HomeSectionLayout.List, settings.homeSectionPresentation(HomeSectionIds.RecentRadio).homeLayout)
            assertEquals(DefaultRecentRadioHomeItemLimit, settings.homeSectionPresentation(HomeSectionIds.RecentRadio).homeItemLimit)
        }
    }

    @Test fun explicitLayoutsVisibilityAndPagePreferencesSurviveNormalizationAndSyncRoundTrip() {
        HomeSectionLayout.entries.forEach { layout ->
            val settings = carousel.fold(InterfaceSettings()) { current, id -> current.withHomeSectionPresentation(id,
                HomeSectionPresentationSettings(homeLayout = layout, pageLayout = HomeSectionPageLayout.List, visible = false)) }
                .withHomeSectionOrder(carousel.reversed())
            val document = SettingsSyncDocument(preferences = SettingsSyncPreferences(interfaceSettings = settings))
            val restored = SettingsSyncJson.decode(SettingsSyncJson.encode(document)).preferences.interfaceSettings.normalized()
            assertEquals(settings, restored)
            carousel.forEach {
                val presentation = restored.homeSectionPresentation(it)
                assertEquals(layout, presentation.homeLayout)
                assertEquals(HomeSectionPageLayout.List, presentation.pageLayout)
                assertFalse(presentation.visible)
            }
            assertEquals(settings.homeSectionOrder, restored.homeSectionOrder)
        }
    }
}
