package app.naviamp.domain.app

fun restoredNavigationRoute(
    savedRouteName: String?,
    hasConnection: Boolean,
    hasRestoredTrack: Boolean,
): NaviampRoute {
    if (!hasConnection) return NaviampRoute.Settings
    return when (val route = naviampRouteFromStoredName(savedRouteName)) {
        NaviampRoute.Player -> if (hasRestoredTrack) NaviampRoute.Player else NaviampRoute.Home
        NaviampRoute.AlbumDetail -> NaviampRoute.Home
        NaviampRoute.ArtistDetail -> NaviampRoute.Search
        NaviampRoute.PlaylistDetail -> NaviampRoute.Playlists
        else -> route
    }
}

fun restoredLastContentRoute(savedRouteName: String?): NaviampRoute =
    when (val route = naviampRouteFromStoredName(savedRouteName)) {
        NaviampRoute.Player,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        NaviampRoute.PlaylistDetail,
        -> NaviampRoute.Home
        else -> route
    }

fun naviampRouteFromStoredName(name: String?): NaviampRoute =
    when (name) {
        "InternetRadio" -> NaviampRoute.Radio
        else -> NaviampRoute.entries.firstOrNull { it.name == name } ?: NaviampRoute.Home
    }

