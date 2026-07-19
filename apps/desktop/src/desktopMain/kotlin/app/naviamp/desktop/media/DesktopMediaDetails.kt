package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute

import app.naviamp.domain.Album
import app.naviamp.domain.Artist

data class ArtistDetailNavigation(
    val backStack: List<Artist>,
    val backRoute: NaviampRoute,
)

fun resolveAlbumDetailBackRoute(
    currentRoute: NaviampRoute,
    currentBackRoute: NaviampRoute,
    lastContentRoute: NaviampRoute,
    backRouteOverride: NaviampRoute?,
): NaviampRoute =
    backRouteOverride ?: when (currentRoute) {
        NaviampRoute.AlbumDetail -> currentBackRoute
        NaviampRoute.ArtistDetail -> NaviampRoute.ArtistDetail
        NaviampRoute.Player -> lastContentRoute
        else -> currentRoute
    }

fun artistDetailNavigation(
    artist: Artist,
    currentArtist: Artist?,
    currentRoute: NaviampRoute,
    currentBackStack: List<Artist>,
    currentBackRoute: NaviampRoute,
    lastContentRoute: NaviampRoute,
    backRouteOverride: NaviampRoute?,
    pushCurrentArtist: Boolean,
): ArtistDetailNavigation {
    val backStack = if (pushCurrentArtist && currentRoute == NaviampRoute.ArtistDetail) {
        currentArtist
            ?.takeIf { it.id != artist.id }
            ?.let { currentBackStack + it }
            ?: currentBackStack
    } else if (currentRoute != NaviampRoute.ArtistDetail) {
        emptyList()
    } else {
        currentBackStack
    }
    val backRoute = backRouteOverride ?: when (currentRoute) {
        NaviampRoute.ArtistDetail -> currentBackRoute
        NaviampRoute.Player -> lastContentRoute
        else -> currentRoute
    }
    return ArtistDetailNavigation(
        backStack = backStack,
        backRoute = backRoute,
    )
}
