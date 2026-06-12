package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist

data class ArtistDetailNavigation(
    val backStack: List<Artist>,
    val backRoute: DesktopAppRoute,
)

fun resolveAlbumDetailBackRoute(
    currentRoute: DesktopAppRoute,
    currentBackRoute: DesktopAppRoute,
    lastContentRoute: DesktopAppRoute,
    backRouteOverride: DesktopAppRoute?,
): DesktopAppRoute =
    backRouteOverride ?: when (currentRoute) {
        DesktopAppRoute.AlbumDetail -> currentBackRoute
        DesktopAppRoute.ArtistDetail -> DesktopAppRoute.ArtistDetail
        DesktopAppRoute.Player -> lastContentRoute
        else -> currentRoute
    }

fun artistDetailNavigation(
    artist: Artist,
    currentArtist: Artist?,
    currentRoute: DesktopAppRoute,
    currentBackStack: List<Artist>,
    currentBackRoute: DesktopAppRoute,
    lastContentRoute: DesktopAppRoute,
    backRouteOverride: DesktopAppRoute?,
    pushCurrentArtist: Boolean,
): ArtistDetailNavigation {
    val backStack = if (pushCurrentArtist && currentRoute == DesktopAppRoute.ArtistDetail) {
        currentArtist
            ?.takeIf { it.id != artist.id }
            ?.let { currentBackStack + it }
            ?: currentBackStack
    } else if (currentRoute != DesktopAppRoute.ArtistDetail) {
        emptyList()
    } else {
        currentBackStack
    }
    val backRoute = backRouteOverride ?: when (currentRoute) {
        DesktopAppRoute.ArtistDetail -> currentBackRoute
        DesktopAppRoute.Player -> lastContentRoute
        else -> currentRoute
    }
    return ArtistDetailNavigation(
        backStack = backStack,
        backRoute = backRoute,
    )
}
