package app.naviamp.ui

import androidx.compose.ui.graphics.vector.ImageVector

enum class NaviampAction(
    val label: String,
    val icon: ImageVector,
) {
    StartTrackRadio("Start track radio", NaviampTransportIcons.Radio),
    PlayTrackRadioNext("Play track radio next", NaviampIcons.Player),
    AddTrackRadioToQueue("Add track radio to queue", NaviampIcons.Queue),
    StartAlbumRadio("Start album radio", NaviampTransportIcons.Radio),
    StartArtistRadio("Start artist radio", NaviampTransportIcons.Radio),
    DownloadTrack("Download track", NaviampIcons.Downloads),
    DownloadAlbum("Download album", NaviampIcons.Downloads),
    DownloadPlaylist("Download playlist", NaviampIcons.Downloads),
    KeepPlaylistDownloaded("Keep downloaded", NaviampIcons.Downloads),
    RemoveDownload("Remove download", NaviampIcons.Trash),
    PlayNext("Play next", NaviampIcons.Player),
    AddToQueue("Add to queue", NaviampIcons.Queue),
    RemoveFromQueue("Remove from queue", NaviampIcons.Trash),
    AddToPlaylist("Add to playlist", NaviampIcons.Playlist),
    SaveQueueAsPlaylist("Save queue as playlist", NaviampIcons.Playlist),
    EmptyQueue("Empty queue", NaviampIcons.Trash),
    SleepTimer("Sleep timer", NaviampIcons.Clock),
    AddPlaylistToPlaylist("Add playlist to playlist", NaviampIcons.Playlist),
    RenamePlaylist("Rename playlist", NaviampIcons.Edit),
    EditSmartPlaylist("Edit smart playlist", NaviampIcons.Brain),
    DeletePlaylist("Delete playlist", NaviampIcons.Trash),
    EditStation("Edit station", NaviampIcons.Edit),
    DeleteStation("Delete station", NaviampIcons.Trash),
    TrackDetails("Track details", NaviampIcons.Info),
    ToggleFavorite("Favorite", NaviampTransportIcons.Heart),
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
    canToggleFavorite: Boolean = false,
    favoriteActive: Boolean = false,
    hasAlbum: Boolean = false,
    hasArtist: Boolean = false,
    canShowDetails: Boolean = true,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.PlayNext.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.StartTrackRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.PlayTrackRadioNext.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.AddTrackRadioToQueue.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.DownloadTrack.takeIf { canDownload }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
        NaviampAction.ToggleFavorite.takeIf { canToggleFavorite }?.toSpec(
            label = if (favoriteActive) "Unfavorite" else "Favorite",
        ),
        NaviampAction.GoToAlbum.takeIf { hasAlbum }?.toSpec(),
        NaviampAction.GoToArtist.takeIf { hasArtist }?.toSpec(),
        NaviampAction.TrackDetails.takeIf { canShowDetails }?.toSpec(),
    )

fun queueRowActions(): List<NaviampActionSpec> =
    listOf(
        NaviampAction.PlayNext.toSpec(),
        NaviampAction.StartTrackRadio.toSpec(),
        NaviampAction.PlayTrackRadioNext.toSpec(),
        NaviampAction.AddTrackRadioToQueue.toSpec(),
        NaviampAction.DownloadTrack.toSpec(),
        NaviampAction.AddToPlaylist.toSpec(),
        NaviampAction.GoToArtist.toSpec(),
        NaviampAction.GoToAlbum.toSpec(),
    )

fun upNextQueueRowActions(): List<NaviampActionSpec> =
    listOf(NaviampAction.RemoveFromQueue.toSpec()) + queueRowActions()

fun relatedTrackRowActions(): List<NaviampActionSpec> =
    listOf(
        NaviampAction.PlayNext.toSpec(),
        NaviampAction.AddToQueue.toSpec(),
    ) + queueRowActions()

fun albumRowActions(
    canStartRadio: Boolean = false,
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canFavorite: Boolean = false,
    favoriteActive: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.StartAlbumRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.DownloadAlbum.takeIf { canDownload }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
        NaviampAction.ToggleFavorite.takeIf { canFavorite }?.toSpec(
            label = if (favoriteActive) "Remove album favorite" else "Favorite album",
        ),
    )

fun artistRowActions(
    canStartRadio: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canFavorite: Boolean = false,
    favoriteActive: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.StartArtistRadio.takeIf { canStartRadio }?.toSpec(),
        NaviampAction.AddToQueue.takeIf { canAddToQueue }?.toSpec(),
        NaviampAction.AddToPlaylist.takeIf { canAddToPlaylist }?.toSpec(),
        NaviampAction.ToggleFavorite.takeIf { canFavorite }?.toSpec(
            label = if (favoriteActive) "Remove artist favorite" else "Favorite artist",
        ),
    )

fun playlistRowActions(
    canDownload: Boolean = false,
    canKeepDownloaded: Boolean = false,
    keepDownloadedActive: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canRename: Boolean = false,
    canEditSmartPlaylist: Boolean = false,
    canDelete: Boolean = false,
): List<NaviampActionSpec> =
    listOfNotNull(
        NaviampAction.DownloadPlaylist.takeIf { canDownload }?.toSpec(),
        NaviampAction.KeepPlaylistDownloaded.takeIf { canKeepDownloaded }?.toSpec(
            label = if (keepDownloadedActive) "Stop keeping downloaded" else "Keep downloaded",
        ),
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
    visualizerAvailable: Boolean,
    isLive: Boolean,
    hasDetails: Boolean,
    canAddToPlaylist: Boolean,
    canSaveQueueAsPlaylist: Boolean,
    canEmptyQueue: Boolean,
    sleepTimerLabel: String,
): List<NaviampActionSpec> =
    listOf(
        NaviampAction.ChangeVisualizer.toSpec(enabled = visualizerAvailable),
        NaviampAction.DownloadTrack.toSpec(enabled = !isLive),
        NaviampAction.TrackDetails.toSpec(enabled = hasDetails),
        NaviampAction.GoToAlbum.toSpec(enabled = !isLive),
        NaviampAction.GoToArtist.toSpec(enabled = !isLive),
        NaviampAction.AddToPlaylist.toSpec(enabled = canAddToPlaylist),
        NaviampAction.SaveQueueAsPlaylist.toSpec(enabled = canSaveQueueAsPlaylist),
        NaviampAction.EmptyQueue.toSpec(enabled = canEmptyQueue),
        NaviampAction.SleepTimer.toSpec(label = sleepTimerLabel),
    )

fun NaviampAction.toSpec(
    label: String = this.label,
    enabled: Boolean = true,
): NaviampActionSpec =
    NaviampActionSpec(action = this, label = label, icon = icon, enabled = enabled)
