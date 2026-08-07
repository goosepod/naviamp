package app.naviamp.presentation

/**
 * Resolves an operating-system back request into the same Core command used by shared UI controls.
 * A null result means the product has no transient screen to close and the host may leave the app.
 */
fun NaviampCoreState.systemBackCommand(): NaviampCoreCommand? = when {
    overlays.statsForNerdsVisible -> NaviampCoreCommand.Settings.CloseStats
    shell.shellChrome.nowPlayingOpen -> NaviampCoreCommand.Navigation.CloseNowPlaying
    shell.playlistDetail.selectedPlaylist != null -> NaviampCoreCommand.Navigation.BackFromPlaylist
    shell.albumDetail.selectedAlbum != null -> NaviampCoreCommand.Navigation.BackFromAlbum
    shell.artistDetail.selectedArtist != null -> NaviampCoreCommand.Navigation.BackFromArtist
    else -> null
}
