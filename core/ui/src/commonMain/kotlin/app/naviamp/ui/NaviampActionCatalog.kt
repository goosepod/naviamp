package app.naviamp.ui

import androidx.compose.ui.graphics.vector.ImageVector

enum class NaviampAction(
    val label: String,
    val icon: ImageVector,
) {
    StartTrackRadio("Start track radio", NaviampTransportIcons.Radio),
    StartAlbumRadio("Start album radio", NaviampTransportIcons.Radio),
    StartArtistRadio("Start artist radio", NaviampTransportIcons.Radio),
    DownloadTrack("Download track", NaviampIcons.Downloads),
    DownloadAlbum("Download album", NaviampIcons.Downloads),
    DownloadPlaylist("Download playlist", NaviampIcons.Downloads),
    RemoveDownload("Remove download", NaviampIcons.Trash),
    AddToQueue("Add to queue", NaviampIcons.Queue),
    AddToPlaylist("Add to playlist", NaviampIcons.Playlist),
    AddPlaylistToPlaylist("Add playlist to playlist", NaviampIcons.Playlist),
    RenamePlaylist("Rename playlist", NaviampIcons.Edit),
    EditSmartPlaylist("Edit smart playlist", NaviampIcons.Brain),
    DeletePlaylist("Delete playlist", NaviampIcons.Trash),
    EditStation("Edit station", NaviampIcons.Edit),
    DeleteStation("Delete station", NaviampIcons.Trash),
    TrackDetails("Track details", NaviampIcons.Info),
    GoToAlbum("Go to album", NaviampIcons.Album),
    GoToArtist("Go to artist", NaviampIcons.Artist),
    ShowLyrics("Show lyrics", NaviampTransportIcons.Lyrics),
    HideLyrics("Hide lyrics", NaviampTransportIcons.Lyrics),
    ShowVisualizer("Show visualizer", NaviampTransportIcons.Visualizer),
    ChangeVisualizer("Change visualizer", NaviampTransportIcons.Visualizer),
    HideVisualizer("Show album art", NaviampIcons.Album),
    TrackPreference("Track preference", NaviampTransportIcons.Heart),
}

data class NaviampActionSpec(
    val action: NaviampAction,
    val label: String = action.label,
    val icon: ImageVector = action.icon,
    val enabled: Boolean = true,
)

fun trackRowActions(
    canStartRadio: Boolean = false,
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.StartTrackRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.DownloadTrack.takeIf { canDownload }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
    )

fun queueRowActions(): List<NaviampActionSpec> =
    trackRowActions(
        canStartRadio = true,
        canDownload = true,
        canAddToPlaylist = true,
    )

fun albumRowActions(
    canStartRadio: Boolean = false,
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.StartAlbumRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.DownloadAlbum.takeIf { canDownload }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
    )

fun artistRowActions(
    canStartRadio: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.StartArtistRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
    )

fun playlistRowActions(
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canRename: Boolean = false,
    canEditSmartPlaylist: Boolean = false,
    canDelete: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.DownloadPlaylist.takeIf { canDownload }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddPlaylistToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
        NaviampAction.RenamePlaylist.takeIf { canRename }?.toSpec(),
        NaviampAction.EditSmartPlaylist.takeIf { canEditSmartPlaylist }?.toSpec(),
        NaviampAction.DeletePlaylist.takeIf { canDelete }?.toSpec(),
    )

fun downloadRowActions(
    canRemove: Boolean = true,
    canAddToPlaylist: Boolean = true,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.RemoveDownload.takeIf { canRemove }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
    )

fun stationRowActions(
    canEdit: Boolean = false,
    canDelete: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.EditStation.takeIf { canEdit }?.toSpec(),
        NaviampAction.DeleteStation.takeIf { canDelete }?.toSpec(),
    )

fun nowPlayingTrackMenuActions(
    lyricsVisible: Boolean,
    lyricsAvailable: Boolean,
    visualizerVisible: Boolean,
    visualizerAvailable: Boolean,
    isLive: Boolean,
    hasDetails: Boolean,
    trackPreferenceLabel: String,
    canSetTrackPreference: Boolean,
    canStartRadio: Boolean,
    canAddToPlaylist: Boolean,
): List<NaviampActionSpec> =
    listOf(
        if (lyricsVisible) {
            NaviampAction.HideLyrics.toSpec(enabled = lyricsAvailable)
        } else {
            NaviampAction.ShowLyrics.toSpec(enabled = lyricsAvailable)
        },
        if (visualizerVisible) {
            NaviampAction.HideVisualizer.toSpec(enabled = visualizerAvailable)
        } else {
            NaviampAction.ShowVisualizer.toSpec(enabled = visualizerAvailable)
        },
        NaviampAction.ChangeVisualizer.toSpec(enabled = visualizerAvailable),
        NaviampAction.DownloadTrack.toSpec(enabled = !isLive),
        NaviampAction.TrackDetails.toSpec(enabled = hasDetails),
        NaviampAction.TrackPreference.toSpec(label = trackPreferenceLabel, enabled = canSetTrackPreference),
        NaviampAction.StartTrackRadio.toSpec(enabled = canStartRadio),
        NaviampAction.GoToAlbum.toSpec(enabled = !isLive),
        NaviampAction.GoToArtist.toSpec(enabled = !isLive),
        NaviampAction.AddToPlaylist.toSpec(enabled = canAddToPlaylist),
    )

fun NaviampAction.toSpec(
    label: String = this.label,
    enabled: Boolean = true,
): NaviampActionSpec =
    NaviampActionSpec(action = this, label = label, icon = icon, enabled = enabled)
