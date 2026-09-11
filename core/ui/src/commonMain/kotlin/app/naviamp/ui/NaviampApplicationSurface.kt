package app.naviamp.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

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
    Radio("Radio", SharedRoute.Radio),
    Search("Search", SharedRoute.Search),
    NowPlaying("Now Playing", null),
    Settings("Settings", SharedRoute.Settings),
}

fun naviampTelevisionDestinations(nowPlayingAvailable: Boolean = false): List<NaviampTelevisionDestination> =
    buildList {
        add(NaviampTelevisionDestination.Home)
        if (nowPlayingAvailable) add(NaviampTelevisionDestination.NowPlaying)
        add(NaviampTelevisionDestination.Library)
        add(NaviampTelevisionDestination.Playlists)
        add(NaviampTelevisionDestination.Radio)
        add(NaviampTelevisionDestination.Search)
    }

fun naviampSelectedTelevisionDestination(
    selectedRoute: SharedRoute,
    nowPlayingOpen: Boolean,
    nowPlayingPreview: Boolean = false,
): NaviampTelevisionDestination? {
    if (nowPlayingOpen || nowPlayingPreview) return NaviampTelevisionDestination.NowPlaying
    return NaviampTelevisionDestination.entries.firstOrNull { it.route == selectedRoute }
}

fun naviampTelevisionNavigationEntersContent(
    key: Key,
    type: KeyEventType,
): Boolean = key == Key.DirectionDown && type == KeyEventType.KeyDown

fun naviampTelevisionNavigationFocusDestination(
    selected: NaviampTelevisionDestination?,
    settingsSelected: Boolean,
    destinations: List<NaviampTelevisionDestination>,
): NaviampTelevisionDestination = when {
    settingsSelected -> NaviampTelevisionDestination.Settings
    selected != null && naviampTelevisionVisibleOwner(selected) in destinations ->
        naviampTelevisionVisibleOwner(selected)
    else -> destinations.firstOrNull() ?: NaviampTelevisionDestination.Home
}

fun naviampTelevisionVisibleOwner(
    destination: NaviampTelevisionDestination,
): NaviampTelevisionDestination = destination

fun naviampTelevisionBackRoute(
    selectedRoute: SharedRoute,
    transientContentOpen: Boolean,
): SharedRoute? = SharedRoute.Home.takeIf {
    !transientContentOpen && selectedRoute != SharedRoute.Home
}
