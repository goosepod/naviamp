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
): DesktopAppRoute =
    restoredNavigationRoute(
        savedRouteName = savedRouteName,
        hasConnection = hasConnection,
        hasRestoredTrack = hasRestoredTrack,
    ).toAppRoute()

fun restoredLastContentRoute(savedRouteName: String?): DesktopAppRoute =
    restoredSharedLastContentRoute(savedRouteName).toAppRoute()

private fun NaviampRoute.toAppRoute(): DesktopAppRoute =
    when (this) {
        NaviampRoute.Player -> DesktopAppRoute.Player
        NaviampRoute.Home -> DesktopAppRoute.Home
        NaviampRoute.Playlists -> DesktopAppRoute.Playlists
        NaviampRoute.PlaylistDetail -> DesktopAppRoute.PlaylistDetail
        NaviampRoute.AlbumDetail -> DesktopAppRoute.AlbumDetail
        NaviampRoute.ArtistDetail -> DesktopAppRoute.ArtistDetail
        NaviampRoute.Library -> DesktopAppRoute.Library
        NaviampRoute.Search -> DesktopAppRoute.Search
        NaviampRoute.ArtistMix -> DesktopAppRoute.ArtistMix
        NaviampRoute.Radio -> DesktopAppRoute.InternetRadio
        NaviampRoute.Downloads -> DesktopAppRoute.Downloads
        NaviampRoute.Settings -> DesktopAppRoute.Settings
    }
