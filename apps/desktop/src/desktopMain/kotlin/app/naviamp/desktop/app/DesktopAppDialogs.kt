package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist

@Composable
fun DesktopAppDialogs(
    appColors: DesktopAppColors,
    addToPlaylistTarget: AddToPlaylistTarget?,
    playlists: List<Playlist>,
    addToPlaylistStatus: String?,
    playlistPendingRename: Playlist?,
    playlistPendingDelete: Playlist?,
    isNewInternetRadioStationDialogOpen: Boolean,
    internetRadioStationPendingEdit: InternetRadioStation?,
    internetRadioStationPendingDelete: InternetRadioStation?,
    onDismissAddToPlaylist: () -> Unit,
    onAddToExistingPlaylist: (target: AddToPlaylistTarget, playlist: Playlist) -> Unit,
    onCreateAndAddToPlaylist: (target: AddToPlaylistTarget, name: String) -> Unit,
    onDismissRenamePlaylist: () -> Unit,
    onRenamePlaylist: (playlist: Playlist, name: String) -> Unit,
    onDismissDeletePlaylist: () -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onDismissNewInternetRadioStation: () -> Unit,
    onSaveInternetRadioStation: (InternetRadioStation) -> Unit,
    onDismissEditInternetRadioStation: () -> Unit,
    onDismissDeleteInternetRadioStation: () -> Unit,
    onDeleteInternetRadioStation: (InternetRadioStation) -> Unit,
) {
    addToPlaylistTarget?.let { target ->
        DesktopAddToPlaylistDialog(
            appColors = appColors,
            target = target,
            playlists = playlists,
            status = addToPlaylistStatus,
            onDismiss = onDismissAddToPlaylist,
            onAddToExisting = { playlist ->
                onAddToExistingPlaylist(target, playlist)
            },
            onCreateAndAdd = { name ->
                onCreateAndAddToPlaylist(target, name)
            },
        )
    }
    playlistPendingRename?.let { playlist ->
        DesktopRenamePlaylistDialog(
            playlist = playlist,
            onDismiss = onDismissRenamePlaylist,
            onConfirm = { name -> onRenamePlaylist(playlist, name) },
        )
    }
    playlistPendingDelete?.let { playlist ->
        DesktopDeletePlaylistDialog(
            playlist = playlist,
            onDismiss = onDismissDeletePlaylist,
            onConfirm = { onDeletePlaylist(playlist) },
        )
    }
    if (isNewInternetRadioStationDialogOpen) {
        DesktopInternetRadioStationDialog(
            initialStation = null,
            onDismiss = onDismissNewInternetRadioStation,
            onConfirm = onSaveInternetRadioStation,
        )
    }
    internetRadioStationPendingEdit?.let { station ->
        DesktopInternetRadioStationDialog(
            initialStation = station,
            onDismiss = onDismissEditInternetRadioStation,
            onConfirm = onSaveInternetRadioStation,
        )
    }
    internetRadioStationPendingDelete?.let { station ->
        DesktopDeleteInternetRadioStationDialog(
            station = station,
            onDismiss = onDismissDeleteInternetRadioStation,
            onConfirm = { onDeleteInternetRadioStation(station) },
        )
    }
}
