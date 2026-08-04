package app.naviamp.ui

data class ResolvedArtistDetailActionHandlers<T>(
    val onPlayCatalog: (T, List<SharedMediaItemUi>, Boolean) -> Unit,
    val onStartRadio: (T) -> Unit,
    val onAddToQueue: (T) -> Unit,
    val onAddToPlaylist: (T, NaviampPlaylistChoiceUi) -> Unit,
    val onCreatePlaylistAndAdd: (T, String) -> Unit,
    val onToggleFavorite: (T) -> Unit,
    val onPlayPopular: (T) -> Unit,
    val onStartPopularRadio: (T) -> Unit,
    val onAddPopularToQueue: (T) -> Unit,
    val onFindSimilar: (T) -> Unit,
    val onSelectSimilar: (T, SharedSimilarArtistUi) -> Unit,
    val onOpenSimilarExternal: (T, String) -> Unit,
)

enum class ArtistDetailActionDispatchResult {
    Dispatched,
    MissingArtist,
    InvalidValue,
}

fun <T> dispatchResolvedArtistDetailAction(
    request: NaviampArtistDetailActionRequest,
    artist: T?,
    handlers: ResolvedArtistDetailActionHandlers<T>,
): ArtistDetailActionDispatchResult {
    artist ?: return ArtistDetailActionDispatchResult.MissingArtist
    return when (val command = request.command) {
        is NaviampArtistDetailCommand.PlayCatalog ->
            handlers.onPlayCatalog(artist, command.albums, command.shuffle).dispatchedArtistAction()
        NaviampArtistDetailCommand.StartRadio -> handlers.onStartRadio(artist).dispatchedArtistAction()
        NaviampArtistDetailCommand.AddToQueue -> handlers.onAddToQueue(artist).dispatchedArtistAction()
        is NaviampArtistDetailCommand.AddToPlaylist ->
            handlers.onAddToPlaylist(artist, command.choice).dispatchedArtistAction()
        is NaviampArtistDetailCommand.CreatePlaylistAndAdd -> command.name
            .takeIf(String::isNotBlank)
            ?.let { handlers.onCreatePlaylistAndAdd(artist, it).dispatchedArtistAction() }
            ?: ArtistDetailActionDispatchResult.InvalidValue
        NaviampArtistDetailCommand.ToggleFavorite -> handlers.onToggleFavorite(artist).dispatchedArtistAction()
        NaviampArtistDetailCommand.PlayPopular -> handlers.onPlayPopular(artist).dispatchedArtistAction()
        NaviampArtistDetailCommand.StartPopularRadio ->
            handlers.onStartPopularRadio(artist).dispatchedArtistAction()
        NaviampArtistDetailCommand.AddPopularToQueue ->
            handlers.onAddPopularToQueue(artist).dispatchedArtistAction()
        NaviampArtistDetailCommand.FindSimilar -> handlers.onFindSimilar(artist).dispatchedArtistAction()
        is NaviampArtistDetailCommand.SelectSimilar ->
            handlers.onSelectSimilar(artist, command.artist).dispatchedArtistAction()
        is NaviampArtistDetailCommand.OpenSimilarExternal -> command.url
            .takeIf(String::isNotBlank)
            ?.let { handlers.onOpenSimilarExternal(artist, it).dispatchedArtistAction() }
            ?: ArtistDetailActionDispatchResult.InvalidValue
    }
}

fun artistDetailActionDispatchStatus(result: ArtistDetailActionDispatchResult): String? =
    when (result) {
        ArtistDetailActionDispatchResult.Dispatched -> null
        ArtistDetailActionDispatchResult.MissingArtist -> "Artist not found."
        ArtistDetailActionDispatchResult.InvalidValue -> "Artist action contains an invalid value."
    }

data class ResolvedArtistAlbumActionHandlers<T>(
    val onSelect: (T) -> Unit,
    val onStartRadio: (T) -> Unit,
    val onDownload: (T) -> Unit,
    val onAddToQueue: (T) -> Unit,
    val onAddToPlaylist: (T, NaviampPlaylistChoiceUi) -> Unit,
    val onCreatePlaylistAndAdd: (T, String) -> Unit,
    val onToggleFavorite: (T) -> Unit,
)

enum class ArtistAlbumActionDispatchResult {
    Dispatched,
    MissingAlbum,
    InvalidValue,
}

fun <T> dispatchResolvedArtistAlbumAction(
    request: NaviampArtistAlbumActionRequest,
    album: T?,
    handlers: ResolvedArtistAlbumActionHandlers<T>,
): ArtistAlbumActionDispatchResult {
    album ?: return ArtistAlbumActionDispatchResult.MissingAlbum
    return when (val command = request.command) {
        NaviampArtistAlbumCommand.Select -> handlers.onSelect(album).dispatchedArtistAlbumAction()
        NaviampArtistAlbumCommand.StartRadio -> handlers.onStartRadio(album).dispatchedArtistAlbumAction()
        NaviampArtistAlbumCommand.Download -> handlers.onDownload(album).dispatchedArtistAlbumAction()
        NaviampArtistAlbumCommand.AddToQueue -> handlers.onAddToQueue(album).dispatchedArtistAlbumAction()
        is NaviampArtistAlbumCommand.AddToPlaylist ->
            handlers.onAddToPlaylist(album, command.choice).dispatchedArtistAlbumAction()
        is NaviampArtistAlbumCommand.CreatePlaylistAndAdd -> command.name
            .takeIf(String::isNotBlank)
            ?.let { handlers.onCreatePlaylistAndAdd(album, it).dispatchedArtistAlbumAction() }
            ?: ArtistAlbumActionDispatchResult.InvalidValue
        NaviampArtistAlbumCommand.ToggleFavorite -> handlers.onToggleFavorite(album).dispatchedArtistAlbumAction()
    }
}

fun artistAlbumActionDispatchStatus(result: ArtistAlbumActionDispatchResult): String? =
    when (result) {
        ArtistAlbumActionDispatchResult.Dispatched -> null
        ArtistAlbumActionDispatchResult.MissingAlbum -> "Album not found."
        ArtistAlbumActionDispatchResult.InvalidValue -> "Album action contains an invalid value."
    }

private fun Unit.dispatchedArtistAction(): ArtistDetailActionDispatchResult =
    ArtistDetailActionDispatchResult.Dispatched

private fun Unit.dispatchedArtistAlbumAction(): ArtistAlbumActionDispatchResult =
    ArtistAlbumActionDispatchResult.Dispatched
