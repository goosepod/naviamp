package app.naviamp.ui

import androidx.compose.runtime.compositionLocalOf

/** Host-reported display surface; all resulting product composition remains shared. */
enum class NaviampApplicationSurface {
    Standard,
    Television,
}

val LocalNaviampApplicationSurface = compositionLocalOf { NaviampApplicationSurface.Standard }

/** Small, stable set of destinations intended for ten-foot navigation. */
enum class NaviampTelevisionDestination(
    val label: String,
    val route: SharedRoute?,
) {
    Home("Home", SharedRoute.Home),
    Library("Library", SharedRoute.Library),
    Playlists("Playlists", SharedRoute.Playlists),
    Search("Search", SharedRoute.Search),
    NowPlaying("Now Playing", null),
    Settings("Settings", SharedRoute.Settings),
}

fun naviampTelevisionDestinations(): List<NaviampTelevisionDestination> = listOf(
    NaviampTelevisionDestination.Home,
    NaviampTelevisionDestination.Library,
    NaviampTelevisionDestination.Search,
)

fun naviampSelectedTelevisionDestination(
    selectedRoute: SharedRoute,
    nowPlayingOpen: Boolean,
): NaviampTelevisionDestination? {
    if (nowPlayingOpen) return NaviampTelevisionDestination.NowPlaying
    return NaviampTelevisionDestination.entries.firstOrNull { it.route == selectedRoute }
}

fun naviampTelevisionBackRoute(
    selectedRoute: SharedRoute,
    transientContentOpen: Boolean,
): SharedRoute? = SharedRoute.Home.takeIf {
    !transientContentOpen && selectedRoute != SharedRoute.Home
}
