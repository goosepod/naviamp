package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.app.restoredNavigationRoute
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.app.restoredLastContentRoute as restoredSharedLastContentRoute

fun ProviderCapabilities.asStatsMap(): Map<String, Boolean> =
    mapOf(
        "Streaming transcode" to supportsStreamingTranscode,
        "Download transcode" to supportsDownloadTranscode,
        "Artist radio" to supportsArtistRadio,
        "Album radio" to supportsAlbumRadio,
        "Track radio" to supportsTrackRadio,
        "Track favorites" to supportsTrackFavorites,
        "Track ratings" to supportsTrackRatings,
        "Play reporting" to supportsPlayReporting,
    )

fun restoredRoute(
    savedRouteName: String?,
    hasConnection: Boolean,
    hasRestoredTrack: Boolean,
): AppRoute =
    restoredNavigationRoute(
        savedRouteName = savedRouteName,
        hasConnection = hasConnection,
        hasRestoredTrack = hasRestoredTrack,
    ).toAppRoute()

fun restoredLastContentRoute(savedRouteName: String?): AppRoute =
    restoredSharedLastContentRoute(savedRouteName).toAppRoute()

private fun NaviampRoute.toAppRoute(): AppRoute =
    when (this) {
        NaviampRoute.Player -> AppRoute.Player
        NaviampRoute.Home -> AppRoute.Home
        NaviampRoute.Playlists -> AppRoute.Playlists
        NaviampRoute.PlaylistDetail -> AppRoute.PlaylistDetail
        NaviampRoute.AlbumDetail -> AppRoute.AlbumDetail
        NaviampRoute.ArtistDetail -> AppRoute.ArtistDetail
        NaviampRoute.Library -> AppRoute.Library
        NaviampRoute.Search -> AppRoute.Search
        NaviampRoute.Radio -> AppRoute.InternetRadio
        NaviampRoute.Downloads -> AppRoute.Downloads
        NaviampRoute.Settings -> AppRoute.Settings
    }
