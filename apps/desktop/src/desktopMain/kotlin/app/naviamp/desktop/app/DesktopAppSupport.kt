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
        "Artist favorites" to supportsArtistFavorites,
        "Album favorites" to supportsAlbumFavorites,
        "Track ratings" to supportsTrackRatings,
        "Play reporting" to supportsPlayReporting,
        "Smart playlists" to supportsSmartPlaylists,
        "Sonic similarity" to supportsSonicSimilarity,
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
        NaviampRoute.AlbumMix -> DesktopAppRoute.AlbumMix
        NaviampRoute.GenreMix -> DesktopAppRoute.GenreMix
        NaviampRoute.SonicPath -> DesktopAppRoute.SonicPath
        NaviampRoute.SonicMix -> DesktopAppRoute.SonicMix
        NaviampRoute.Radio -> DesktopAppRoute.InternetRadio
        NaviampRoute.Downloads -> DesktopAppRoute.Downloads
        NaviampRoute.Settings -> DesktopAppRoute.Settings
    }
