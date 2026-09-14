package app.naviamp.ui
import app.naviamp.ui.generated.resources.*


import app.naviamp.domain.settings.HomeSectionIds
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.homeSectionPresentation
import app.naviamp.domain.settings.resolvedHomeSectionOrder
import app.naviamp.domain.settings.withHomeSectionOrder
import app.naviamp.domain.settings.withHomeSectionPresentation

internal data class HomeScreenSectionOption(
    val id: String,
    val title: String,
    val titleResource: org.jetbrains.compose.resources.StringResource? = null,
)

internal val HomeScreenSectionOptions = listOf(
    HomeScreenSectionOption(HomeSectionIds.FavoriteArtists, "", Res.string.settings_favorite_artists),
    HomeScreenSectionOption(HomeSectionIds.MixesForYou, "Mixes for You", Res.string.tv_home_mixes_for_you),
    HomeScreenSectionOption(HomeSectionIds.NavibeatMixes, "NaviBeat Mixes", Res.string.tv_home_navibeat_mixes),
    HomeScreenSectionOption(HomeSectionIds.RecentRadio, "Recently Played Radio", Res.string.home_recently_played_radio),
    HomeScreenSectionOption(HomeSectionIds.RecentlyPlayed, "Recently Played", Res.string.tv_home_recently_played),
    HomeScreenSectionOption(HomeSectionIds.MixBuilders, "Mix Builders", Res.string.tv_home_mix_builders),
    HomeScreenSectionOption(HomeSectionIds.MoreLikeRecentPlays, "More Like Recent Plays", Res.string.tv_home_more_like_recent_plays),
    HomeScreenSectionOption(HomeSectionIds.SonicDeepCuts, "Sonic Deep Cuts", Res.string.tv_home_sonic_deep_cuts),
    HomeScreenSectionOption(HomeSectionIds.SimilarToStarredTracks, "Similar To Starred Tracks", Res.string.tv_home_similar_to_starred_tracks),
    HomeScreenSectionOption(HomeSectionIds.RecentlyAdded, "Recently Added Music", Res.string.tv_home_recently_added_music),
    HomeScreenSectionOption(HomeSectionIds.RecentPlaylists, "Recent Playlists", Res.string.home_recent_playlists),
    HomeScreenSectionOption(HomeSectionIds.RecentInternetRadio, "Recent Internet Radio", Res.string.home_recent_internet_radio),
    HomeScreenSectionOption(HomeSectionIds.Stations, "Stations", Res.string.tv_home_stations),
    HomeScreenSectionOption(HomeSectionIds.RecentAlbums, "Recent Albums", Res.string.home_recent_albums),
    HomeScreenSectionOption(HomeSectionIds.FrequentlyPlayedAlbums, "Frequently Played Albums", Res.string.home_frequently_played_albums),
    HomeScreenSectionOption(HomeSectionIds.RandomAlbums, "Random Albums", Res.string.home_random_albums),
    HomeScreenSectionOption(HomeSectionIds.GenreSpotlight, "Genre Spotlight", Res.string.tv_home_genre_spotlight),
    HomeScreenSectionOption(HomeSectionIds.Decade, "Decade Spotlight", Res.string.tv_home_decade_spotlight),
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
