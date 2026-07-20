package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.NaviampMiniPlayerContent
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingPresentationUi

@Composable
internal fun DesktopShellChrome(
    appColors: DesktopAppColors,
    presentation: NaviampNowPlayingPresentationUi,
    nowPlayingActions: NaviampNowPlayingActions,
    showMiniPlayer: Boolean,
    supportsDownloads: Boolean,
    selectedRoute: NaviampRoute,
    albumDetailBackRoute: NaviampRoute,
    artistDetailBackRoute: NaviampRoute,
    onOpenPlayer: () -> Unit,
    onRouteSelected: (NaviampRoute) -> Unit,
) {
    presentation.miniNowPlaying?.takeIf { showMiniPlayer }?.let { miniNowPlaying ->
        NaviampMiniPlayerContent(
            nowPlaying = miniNowPlaying,
            colors = appColors,
            actions = nowPlayingActions,
            onOpen = onOpenPlayer,
        )
    }
    DesktopBottomNavigationBar(
        appColors = appColors,
        supportsDownloads = supportsDownloads,
        selectedRoute = desktopBottomNavigationRoute(
            selectedRoute = selectedRoute,
            albumDetailBackRoute = albumDetailBackRoute,
            artistDetailBackRoute = artistDetailBackRoute,
        ),
        onRouteSelected = onRouteSelected,
    )
}

internal fun desktopBottomNavigationRoute(
    selectedRoute: NaviampRoute,
    albumDetailBackRoute: NaviampRoute,
    artistDetailBackRoute: NaviampRoute,
): NaviampRoute = when (selectedRoute) {
    NaviampRoute.AlbumDetail -> if (albumDetailBackRoute == NaviampRoute.ArtistDetail) {
        artistDetailBackRoute
    } else {
        albumDetailBackRoute
    }
    NaviampRoute.ArtistDetail -> artistDetailBackRoute
    NaviampRoute.PlaylistDetail -> NaviampRoute.Playlists
    else -> selectedRoute
}
