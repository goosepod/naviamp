package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.Playlist
import app.naviamp.ui.AddToPlaylistDialog
import app.naviamp.ui.DeletePlaylistDialog
import app.naviamp.ui.RenamePlaylistDialog
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toPlaylistChoiceUi

@Composable
fun DesktopAppDialogs(
    appColors: DesktopAppColors,
    addToPlaylistTarget: AddToPlaylistTarget?,
    playlists: List<Playlist>,
    addToPlaylistStatus: String?,
    playlistPendingRename: Playlist?,
    playlistPendingDelete: Playlist?,
    onDismissAddToPlaylist: () -> Unit,
    onAddToExistingPlaylist: (target: AddToPlaylistTarget, playlist: Playlist) -> Unit,
    onCreateAndAddToPlaylist: (target: AddToPlaylistTarget, name: String) -> Unit,
    onDismissRenamePlaylist: () -> Unit,
    onRenamePlaylist: (playlist: Playlist, name: String) -> Unit,
    onDismissDeletePlaylist: () -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
) {
    addToPlaylistTarget?.let { target ->
        AddToPlaylistDialog(
            title = target.title,
            colors = appColors,
            playlists = playlists.map { it.toPlaylistChoiceUi() },
            status = addToPlaylistStatus,
            onDismissRequest = onDismissAddToPlaylist,
            onAddToExisting = { choice ->
                playlists.firstOrNull { it.id == choice.id }?.let { playlist ->
                    onAddToExistingPlaylist(target, playlist)
                }
            },
            onCreateAndAdd = { name ->
                onCreateAndAddToPlaylist(target, name)
            },
        )
    }
    playlistPendingRename?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist.toSharedPlaylistItem(),
            colors = appColors,
            onDismiss = onDismissRenamePlaylist,
            onConfirm = { name -> onRenamePlaylist(playlist, name) },
        )
    }
    playlistPendingDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist.toSharedPlaylistItem(),
            colors = appColors,
            onDismiss = onDismissDeletePlaylist,
            onConfirm = { onDeletePlaylist(playlist) },
        )
    }
}

private fun Playlist.toSharedPlaylistItem(): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = "$trackCount tracks",
    )
