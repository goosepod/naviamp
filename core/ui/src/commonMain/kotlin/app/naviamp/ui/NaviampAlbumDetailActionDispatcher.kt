package app.naviamp.ui

data class ResolvedAlbumDetailActionHandlers<T>(
    val onPlay: (T, Boolean) -> Unit,
    val onStartRadio: (T) -> Unit,
    val onDownload: (T) -> Unit,
    val onAddToQueue: (T) -> Unit,
    val onAddToPlaylist: (T, NaviampPlaylistChoiceUi) -> Unit,
    val onCreatePlaylistAndAdd: (T, String) -> Unit,
    val onToggleFavorite: (T) -> Unit,
)

enum class AlbumDetailActionDispatchResult {
    Dispatched,
    MissingAlbum,
    InvalidValue,
}

fun <T> dispatchResolvedAlbumDetailAction(
    request: NaviampAlbumDetailActionRequest,
    album: T?,
    handlers: ResolvedAlbumDetailActionHandlers<T>,
): AlbumDetailActionDispatchResult {
    album ?: return AlbumDetailActionDispatchResult.MissingAlbum
    return when (val command = request.command) {
        is NaviampAlbumDetailCommand.Play -> handlers.onPlay(album, command.shuffle).dispatchedAlbumAction()
        NaviampAlbumDetailCommand.StartRadio -> handlers.onStartRadio(album).dispatchedAlbumAction()
        NaviampAlbumDetailCommand.Download -> handlers.onDownload(album).dispatchedAlbumAction()
        NaviampAlbumDetailCommand.AddToQueue -> handlers.onAddToQueue(album).dispatchedAlbumAction()
        is NaviampAlbumDetailCommand.AddToPlaylist ->
            handlers.onAddToPlaylist(album, command.choice).dispatchedAlbumAction()
        is NaviampAlbumDetailCommand.CreatePlaylistAndAdd -> command.name
            .takeIf(String::isNotBlank)
            ?.let { handlers.onCreatePlaylistAndAdd(album, it).dispatchedAlbumAction() }
            ?: AlbumDetailActionDispatchResult.InvalidValue
        NaviampAlbumDetailCommand.ToggleFavorite -> handlers.onToggleFavorite(album).dispatchedAlbumAction()
    }
}

fun albumDetailActionDispatchStatus(result: AlbumDetailActionDispatchResult): String? =
    when (result) {
        AlbumDetailActionDispatchResult.Dispatched -> null
        AlbumDetailActionDispatchResult.MissingAlbum -> "Album not found."
        AlbumDetailActionDispatchResult.InvalidValue -> "Album action contains an invalid value."
    }

private fun Unit.dispatchedAlbumAction(): AlbumDetailActionDispatchResult =
    AlbumDetailActionDispatchResult.Dispatched
