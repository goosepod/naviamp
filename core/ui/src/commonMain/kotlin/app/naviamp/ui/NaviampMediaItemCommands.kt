package app.naviamp.ui

sealed interface NaviampMediaItemCommand {
    data class Album(val command: NaviampArtistAlbumCommand) : NaviampMediaItemCommand
    data class Artist(val command: NaviampArtistMediaCommand) : NaviampMediaItemCommand
    data class Playlist(val command: NaviampPlaylistMediaCommand) : NaviampMediaItemCommand
}

sealed interface NaviampArtistMediaCommand {
    data object Select : NaviampArtistMediaCommand
    data object StartRadio : NaviampArtistMediaCommand
    data object FindSimilar : NaviampArtistMediaCommand
    data object AddToQueue : NaviampArtistMediaCommand
    data class AddToPlaylist(val choice: NaviampPlaylistChoiceUi) : NaviampArtistMediaCommand
    data class CreatePlaylistAndAdd(val name: String) : NaviampArtistMediaCommand
    data object ToggleFavorite : NaviampArtistMediaCommand
}

sealed interface NaviampPlaylistMediaCommand {
    data object Select : NaviampPlaylistMediaCommand
    data class Detail(val command: NaviampPlaylistDetailCommand) : NaviampPlaylistMediaCommand
    data object EditSmartPlaylist : NaviampPlaylistMediaCommand
}

data class NaviampMediaItemActionRequest(
    val item: SharedMediaItemUi,
    val command: NaviampMediaItemCommand,
)

data class ResolvedArtistMediaActionHandlers<T>(
    val onSelect: (T) -> Unit,
    val onStartRadio: (T) -> Unit,
    val onFindSimilar: (T) -> Unit,
    val onAddToQueue: ((T) -> Unit)?,
    val onAddToPlaylist: ((T, NaviampPlaylistChoiceUi) -> Unit)?,
    val onCreatePlaylistAndAdd: ((T, String) -> Unit)?,
    val onToggleFavorite: (T) -> Unit,
)

data class ResolvedPlaylistMediaActionHandlers<T>(
    val onSelect: (T) -> Unit,
    val detail: ResolvedPlaylistDetailActionHandlers<T>,
    val onEditSmartPlaylist: ((T) -> Unit)?,
)

fun <T> dispatchResolvedAlbumMediaAction(
    request: NaviampMediaItemActionRequest,
    command: NaviampMediaItemCommand.Album,
    album: T?,
    handlers: ResolvedArtistAlbumActionHandlers<T>,
): MediaItemActionDispatchResult = when (
    dispatchResolvedArtistAlbumAction(
        NaviampArtistAlbumActionRequest(request.item, command.command),
        album,
        handlers,
    )
) {
    ArtistAlbumActionDispatchResult.Dispatched -> MediaItemActionDispatchResult.Dispatched
    ArtistAlbumActionDispatchResult.MissingAlbum -> MediaItemActionDispatchResult.MissingItem
    ArtistAlbumActionDispatchResult.InvalidValue -> MediaItemActionDispatchResult.InvalidValue
}

fun <T> dispatchResolvedArtistMediaAction(
    command: NaviampMediaItemCommand.Artist,
    artist: T?,
    handlers: ResolvedArtistMediaActionHandlers<T>,
): MediaItemActionDispatchResult {
    artist ?: return MediaItemActionDispatchResult.MissingItem
    return when (val action = command.command) {
        NaviampArtistMediaCommand.Select -> handlers.onSelect(artist).mediaDispatched()
        NaviampArtistMediaCommand.StartRadio -> handlers.onStartRadio(artist).mediaDispatched()
        NaviampArtistMediaCommand.FindSimilar -> handlers.onFindSimilar(artist).mediaDispatched()
        NaviampArtistMediaCommand.AddToQueue -> handlers.onAddToQueue.mediaOptional { it(artist) }
        is NaviampArtistMediaCommand.AddToPlaylist -> handlers.onAddToPlaylist.mediaOptional {
            it(artist, action.choice)
        }
        is NaviampArtistMediaCommand.CreatePlaylistAndAdd -> handlers.onCreatePlaylistAndAdd.mediaOptional {
            it(artist, action.name)
        }
        NaviampArtistMediaCommand.ToggleFavorite -> handlers.onToggleFavorite(artist).mediaDispatched()
    }
}

fun <T> dispatchResolvedPlaylistMediaAction(
    request: NaviampMediaItemActionRequest,
    command: NaviampMediaItemCommand.Playlist,
    playlist: T?,
    handlers: ResolvedPlaylistMediaActionHandlers<T>,
): MediaItemActionDispatchResult {
    playlist ?: return MediaItemActionDispatchResult.MissingItem
    return when (val action = command.command) {
        NaviampPlaylistMediaCommand.Select -> handlers.onSelect(playlist).mediaDispatched()
        NaviampPlaylistMediaCommand.EditSmartPlaylist -> handlers.onEditSmartPlaylist.mediaOptional { it(playlist) }
        is NaviampPlaylistMediaCommand.Detail -> when (
            dispatchResolvedPlaylistDetailAction(
                NaviampPlaylistDetailActionRequest(request.item, action.command),
                playlist,
                handlers.detail,
            )
        ) {
            PlaylistDetailActionDispatchResult.Dispatched -> MediaItemActionDispatchResult.Dispatched
            PlaylistDetailActionDispatchResult.MissingPlaylist -> MediaItemActionDispatchResult.MissingItem
            PlaylistDetailActionDispatchResult.InvalidValue -> MediaItemActionDispatchResult.InvalidValue
        }
    }
}

private fun Unit.mediaDispatched() = MediaItemActionDispatchResult.Dispatched

private inline fun <T> T?.mediaOptional(action: (T) -> Unit): MediaItemActionDispatchResult =
    this?.let { action(it); MediaItemActionDispatchResult.Dispatched }
        ?: MediaItemActionDispatchResult.UnsupportedAction
