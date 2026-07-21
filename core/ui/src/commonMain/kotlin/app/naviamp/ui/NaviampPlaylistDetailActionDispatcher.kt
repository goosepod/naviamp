package app.naviamp.ui

/** Required execution boundary for every action emitted by the shared playlist-detail screen. */
data class ResolvedPlaylistDetailActionHandlers<T>(
    val onPlay: (T, Boolean) -> Unit,
    val onAddToQueue: (T) -> Unit,
    val onDownload: (T, String?) -> Unit,
    val onAddToPlaylist: (T, NaviampPlaylistChoiceUi) -> Unit,
    val onCreatePlaylistAndAdd: (T, String) -> Unit,
    val onCopy: (T, String, Boolean) -> Unit,
    val onRename: (T, String) -> Unit,
    val onDelete: (T) -> Unit,
)

enum class PlaylistDetailActionDispatchResult {
    Dispatched,
    MissingPlaylist,
    InvalidValue,
}

fun <T> dispatchResolvedPlaylistDetailAction(
    request: NaviampPlaylistDetailActionRequest,
    playlist: T?,
    handlers: ResolvedPlaylistDetailActionHandlers<T>,
): PlaylistDetailActionDispatchResult {
    playlist ?: return PlaylistDetailActionDispatchResult.MissingPlaylist
    return when (val command = request.command) {
        is NaviampPlaylistDetailCommand.Play -> handlers.onPlay(playlist, command.shuffle).dispatched()
        NaviampPlaylistDetailCommand.AddToQueue -> handlers.onAddToQueue(playlist).dispatched()
        is NaviampPlaylistDetailCommand.Download -> handlers.onDownload(playlist, command.value).dispatched()
        is NaviampPlaylistDetailCommand.AddToPlaylist ->
            handlers.onAddToPlaylist(playlist, command.choice).dispatched()
        is NaviampPlaylistDetailCommand.CreatePlaylistAndAdd -> command.name
            .takeIf(String::isNotBlank)
            ?.let { name -> handlers.onCreatePlaylistAndAdd(playlist, name).dispatched() }
            ?: PlaylistDetailActionDispatchResult.InvalidValue
        is NaviampPlaylistDetailCommand.Copy -> command.name
            .takeIf(String::isNotBlank)
            ?.let { name -> handlers.onCopy(playlist, name, command.deduplicate).dispatched() }
            ?: PlaylistDetailActionDispatchResult.InvalidValue
        is NaviampPlaylistDetailCommand.Rename -> command.name
            .takeIf(String::isNotBlank)
            ?.let { name -> handlers.onRename(playlist, name).dispatched() }
            ?: PlaylistDetailActionDispatchResult.InvalidValue
        NaviampPlaylistDetailCommand.Delete -> handlers.onDelete(playlist).dispatched()
    }
}

fun playlistDetailActionDispatchStatus(result: PlaylistDetailActionDispatchResult): String? =
    when (result) {
        PlaylistDetailActionDispatchResult.Dispatched -> null
        PlaylistDetailActionDispatchResult.MissingPlaylist -> "Playlist not found."
        PlaylistDetailActionDispatchResult.InvalidValue -> "Playlist action contains an invalid value."
    }

private fun Unit.dispatched(): PlaylistDetailActionDispatchResult =
    PlaylistDetailActionDispatchResult.Dispatched
