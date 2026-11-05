package app.naviamp.presentation

/**
 * Resolves an operating-system back request into the same Core command used by shared UI controls.
 * A null result means the product has no transient screen to close and the host may leave the app.
 */
fun NaviampCoreState.systemBackCommand(): NaviampCoreCommand? = when {
    overlays.statsForNerdsVisible -> NaviampCoreCommand.Settings.CloseStats
    else -> when (app.naviamp.ui.naviampBackTarget(shell)) {
        app.naviamp.ui.NaviampBackTarget.NowPlaying -> NaviampCoreCommand.Navigation.CloseNowPlaying
        app.naviamp.ui.NaviampBackTarget.DiscoveryRequests -> NaviampCoreCommand.Home.DismissDiscoveryRequests
        app.naviamp.ui.NaviampBackTarget.DiscoveryArtist -> NaviampCoreCommand.Home.DismissDiscoveryArtist
        app.naviamp.ui.NaviampBackTarget.DiscoveryBrowser -> NaviampCoreCommand.Home.Browse(app.naviamp.domain.provider.DiscoveryBrowseAction.Close)
        app.naviamp.ui.NaviampBackTarget.HomeCollection -> NaviampCoreCommand.Home.CloseCollection
        app.naviamp.ui.NaviampBackTarget.Playlist -> NaviampCoreCommand.Navigation.BackFromPlaylist
        app.naviamp.ui.NaviampBackTarget.Album -> NaviampCoreCommand.Navigation.BackFromAlbum
        app.naviamp.ui.NaviampBackTarget.Artist -> NaviampCoreCommand.Navigation.BackFromArtist
        null -> null
    }
}
