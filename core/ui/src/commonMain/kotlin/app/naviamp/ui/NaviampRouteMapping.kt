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
        SharedRoute.Radio -> NaviampRoute.Radio
        SharedRoute.Downloads -> NaviampRoute.Downloads
        SharedRoute.Settings -> NaviampRoute.Settings
    }
