package app.naviamp.ui

import app.naviamp.domain.app.NaviampRoute

fun NaviampRoute.toSharedRoute(): SharedRoute =
    when (this) {
        NaviampRoute.Home -> SharedRoute.Home
        NaviampRoute.Playlists,
        NaviampRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        NaviampRoute.Library -> SharedRoute.Library
        NaviampRoute.Search,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        NaviampRoute.Player,
        -> SharedRoute.Search
        NaviampRoute.ArtistMix -> SharedRoute.ArtistMix
        NaviampRoute.AlbumMix -> SharedRoute.AlbumMix
        NaviampRoute.GenreMix -> SharedRoute.GenreMix
        NaviampRoute.SonicPath -> SharedRoute.SonicPath
        NaviampRoute.SonicMix -> SharedRoute.SonicMix
        NaviampRoute.Radio -> SharedRoute.Radio
        NaviampRoute.Downloads -> SharedRoute.Downloads
        NaviampRoute.Settings -> SharedRoute.Settings
    }

fun SharedRoute.toNaviampRoute(): NaviampRoute =
    when (this) {
        SharedRoute.Home -> NaviampRoute.Home
        SharedRoute.Playlists -> NaviampRoute.Playlists
        SharedRoute.Library -> NaviampRoute.Library
        SharedRoute.Search -> NaviampRoute.Search
        SharedRoute.ArtistMix -> NaviampRoute.ArtistMix
        SharedRoute.AlbumMix -> NaviampRoute.AlbumMix
        SharedRoute.GenreMix -> NaviampRoute.GenreMix
        SharedRoute.SonicPath -> NaviampRoute.SonicPath
        SharedRoute.SonicMix -> NaviampRoute.SonicMix
        SharedRoute.Radio -> NaviampRoute.Radio
        SharedRoute.Downloads -> NaviampRoute.Downloads
        SharedRoute.Settings -> NaviampRoute.Settings
    }
