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

fun dispatchLegacyMediaItemAction(
    request: SharedMediaItemActionRequest,
    onCommand: (NaviampMediaItemActionRequest) -> Unit,
    onRejected: (NaviampMediaItemCommandConversion) -> Unit = {},
) {
    when (val conversion = request.toNaviampMediaItemCommand()) {
        is NaviampMediaItemCommandConversion.Converted -> onCommand(conversion.request)
        NaviampMediaItemCommandConversion.Unsupported,
        NaviampMediaItemCommandConversion.InvalidValue,
        -> onRejected(conversion)
    }
}

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

sealed interface NaviampMediaItemCommandConversion {
    data class Converted(val request: NaviampMediaItemActionRequest) : NaviampMediaItemCommandConversion
    data object Unsupported : NaviampMediaItemCommandConversion
    data object InvalidValue : NaviampMediaItemCommandConversion
}

fun SharedMediaItemActionRequest.toNaviampMediaItemCommand(): NaviampMediaItemCommandConversion =
    when (kind) {
        SharedMediaItemKind.Album -> albumCommand()
        SharedMediaItemKind.Artist -> artistCommand()
        SharedMediaItemKind.Playlist -> playlistCommand()
        SharedMediaItemKind.Unknown,
        SharedMediaItemKind.RadioStation,
        SharedMediaItemKind.MixBuilder,
        -> NaviampMediaItemCommandConversion.Unsupported
    }

private fun SharedMediaItemActionRequest.albumCommand(): NaviampMediaItemCommandConversion = when (action) {
    SharedMediaItemAction.Select -> converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select))
    SharedMediaItemAction.StartRadio -> converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.StartRadio))
    SharedMediaItemAction.Download -> converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Download))
    SharedMediaItemAction.AddToQueue -> converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.AddToQueue))
    SharedMediaItemAction.AddToPlaylist -> playlistChoice
        ?.let { converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.AddToPlaylist(it))) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.CreatePlaylistAndAdd -> playlistName.validName()
        ?.let { converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.CreatePlaylistAndAdd(it))) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.ToggleFavorite -> converted(NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.ToggleFavorite))
    else -> NaviampMediaItemCommandConversion.Unsupported
}

private fun SharedMediaItemActionRequest.artistCommand(): NaviampMediaItemCommandConversion = when (action) {
    SharedMediaItemAction.Select -> converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select))
    SharedMediaItemAction.StartRadio -> converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.StartRadio))
    SharedMediaItemAction.FindSimilar -> converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.FindSimilar))
    SharedMediaItemAction.AddToQueue -> converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.AddToQueue))
    SharedMediaItemAction.AddToPlaylist -> playlistChoice
        ?.let { converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.AddToPlaylist(it))) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.CreatePlaylistAndAdd -> playlistName.validName()
        ?.let { converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.CreatePlaylistAndAdd(it))) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.ToggleFavorite -> converted(NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.ToggleFavorite))
    else -> NaviampMediaItemCommandConversion.Unsupported
}

private fun SharedMediaItemActionRequest.playlistCommand(): NaviampMediaItemCommandConversion = when (action) {
    SharedMediaItemAction.Select -> convertedPlaylist(NaviampPlaylistDetailCommand.Play(shuffle = false), select = true)
    SharedMediaItemAction.Play -> convertedPlaylist(NaviampPlaylistDetailCommand.Play(shuffle = false))
    SharedMediaItemAction.Shuffle -> convertedPlaylist(NaviampPlaylistDetailCommand.Play(shuffle = true))
    SharedMediaItemAction.AddToQueue -> convertedPlaylist(NaviampPlaylistDetailCommand.AddToQueue)
    SharedMediaItemAction.Download -> convertedPlaylist(NaviampPlaylistDetailCommand.Download(textValue))
    SharedMediaItemAction.AddToPlaylist -> playlistChoice
        ?.let { convertedPlaylist(NaviampPlaylistDetailCommand.AddToPlaylist(it)) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.CreatePlaylistAndAdd -> playlistName.validName()
        ?.let { convertedPlaylist(NaviampPlaylistDetailCommand.CreatePlaylistAndAdd(it)) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.CopyPlaylist,
    SharedMediaItemAction.CopyPlaylistDeduplicated,
    -> playlistName.validName()
        ?.let {
            convertedPlaylist(
                NaviampPlaylistDetailCommand.Copy(
                    name = it,
                    deduplicate = action == SharedMediaItemAction.CopyPlaylistDeduplicated,
                ),
            )
        }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.Rename -> textValue.validName()
        ?.let { convertedPlaylist(NaviampPlaylistDetailCommand.Rename(it)) }
        ?: NaviampMediaItemCommandConversion.InvalidValue
    SharedMediaItemAction.EditSmartPlaylist -> converted(NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.EditSmartPlaylist))
    SharedMediaItemAction.Delete -> convertedPlaylist(NaviampPlaylistDetailCommand.Delete)
    else -> NaviampMediaItemCommandConversion.Unsupported
}

private fun SharedMediaItemActionRequest.convertedPlaylist(
    command: NaviampPlaylistDetailCommand,
    select: Boolean = false,
): NaviampMediaItemCommandConversion = if (select) {
    converted(NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Select))
} else {
    converted(NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Detail(command)))
}

private fun SharedMediaItemActionRequest.converted(command: NaviampMediaItemCommand) =
    NaviampMediaItemCommandConversion.Converted(NaviampMediaItemActionRequest(item, command))

private fun String?.validName(): String? = this?.takeIf(String::isNotBlank)
