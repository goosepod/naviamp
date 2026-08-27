package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.homeSectionPresentation
import app.naviamp.domain.settings.resolvedHomeSectionOrder
import app.naviamp.domain.settings.withHomeSectionOrder
import app.naviamp.domain.settings.withHomeSectionPresentation

internal data class HomeScreenSectionOption(
    val id: String,
    val title: String,
)

internal val HomeScreenSectionOptions = listOf(
    HomeScreenSectionOption(HomeSectionIds.MixesForYou, "Mixes for You"),
    HomeScreenSectionOption(HomeSectionIds.NavibeatMixes, "NaviBeat Mixes"),
    HomeScreenSectionOption(HomeSectionIds.RecentRadio, "Recently Played Radio"),
    HomeScreenSectionOption(HomeSectionIds.RecentlyPlayed, "Recently Played"),
    HomeScreenSectionOption(HomeSectionIds.MixBuilders, "Mix Builders"),
    HomeScreenSectionOption(HomeSectionIds.MoreLikeRecentPlays, "More Like Recent Plays"),
    HomeScreenSectionOption(HomeSectionIds.SonicDeepCuts, "Sonic Deep Cuts"),
    HomeScreenSectionOption(HomeSectionIds.SimilarToStarredTracks, "Similar To Starred Tracks"),
    HomeScreenSectionOption(HomeSectionIds.RecentlyAdded, "Recently Added Music"),
    HomeScreenSectionOption(HomeSectionIds.RecentPlaylists, "Recent Playlists"),
    HomeScreenSectionOption(HomeSectionIds.RecentInternetRadio, "Recent Internet Radio"),
    HomeScreenSectionOption(HomeSectionIds.Stations, "Stations"),
    HomeScreenSectionOption(HomeSectionIds.RecentAlbums, "Recent Albums"),
    HomeScreenSectionOption(HomeSectionIds.FrequentlyPlayedAlbums, "Frequently Played Albums"),
    HomeScreenSectionOption(HomeSectionIds.RandomAlbums, "Random Albums"),
    HomeScreenSectionOption(HomeSectionIds.GenreSpotlight, "Genre Spotlight"),
    HomeScreenSectionOption(HomeSectionIds.Decade, "Decade Spotlight"),
)

internal fun InterfaceSettings.orderedHomeScreenSectionOptions(): List<HomeScreenSectionOption> =
    resolvedHomeSectionOrder(HomeScreenSectionOptions.map { it.id })
        .mapNotNull { id -> HomeScreenSectionOptions.firstOrNull { it.id == id } }

internal fun InterfaceSettings.withHomeScreenSectionVisible(
    sectionId: String,
    visible: Boolean,
): InterfaceSettings = withHomeSectionPresentation(
    sectionId,
    homeSectionPresentation(sectionId).copy(visible = visible),
)

internal fun InterfaceSettings.withOrderedHomeScreenSections(
    orderedSections: List<HomeScreenSectionOption>,
): InterfaceSettings {
    val knownIds = HomeScreenSectionOptions.mapTo(mutableSetOf()) { it.id }
    val unknownIds = homeSectionOrder.filterNot { it in knownIds }
    return withHomeSectionOrder(orderedSections.map { it.id } + unknownIds)
}

internal fun <T> List<T>.moveHomeSectionItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}
