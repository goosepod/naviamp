package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.popular.SimilarArtistMatch

/** Resolves shared detail action IDs against the current domain snapshot. */
data class SharedDetailActionSources(
    val selectedAlbum: Album? = null,
    val albumDetail: AlbumDetails? = null,
    val selectedArtist: Artist? = null,
    val artistDetail: ArtistDetails? = null,
    val artistPopularTracks: List<Track> = emptyList(),
    val artistSimilarArtists: List<SimilarArtistMatch> = emptyList(),
) {
    fun album(id: String): Album? =
        albumDetail?.album?.takeIf { it.id.value == id }
            ?: selectedAlbum?.takeIf { it.id.value == id }
            ?: artistDetail?.albums?.firstOrNull { it.id.value == id }

    fun artist(id: String): Artist? =
        artistDetail?.artist?.takeIf { it.id.value == id }
            ?: selectedArtist?.takeIf { it.id.value == id }

    fun albumTrack(id: String): Pair<Int, Track>? {
        val tracks = albumDetail?.tracks.orEmpty()
        val index = tracks.indexOfFirst { it.id.value == id }
        return tracks.getOrNull(index)?.let { index to it }
    }

    fun popularTrack(id: String): Track? =
        artistPopularTracks.firstOrNull { it.id.value == id }

    fun artistAlbums(ids: List<String>): List<Album> =
        ids.mapNotNull { id -> artistDetail?.albums?.firstOrNull { it.id.value == id } }

    fun similarArtist(item: SharedSimilarArtistUi): Pair<Artist?, String?> {
        val match = artistSimilarArtists.firstOrNull { candidate ->
            candidate.candidate.sourceArtistId == item.id || candidate.matchedArtist?.id?.value == item.localArtistId
        }
        return match?.matchedArtist to (match?.candidate?.externalUrl ?: item.externalUrl)
    }
}

/** Resolves shared playlist and track IDs against the current domain snapshot. */
data class SharedPlaylistActionSources(
    val playlists: List<Playlist> = emptyList(),
    val playlistTracksById: Map<String, List<Track>> = emptyMap(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
) {
    fun playlist(id: String): Playlist? =
        playlists.firstOrNull { it.id == id }
            ?: selectedPlaylist?.takeIf { it.id == id }

    fun selectedTrack(id: String): Pair<Int, Track>? {
        val index = selectedPlaylistTracks.indexOfFirst { it.id.value == id }
        return selectedPlaylistTracks.getOrNull(index)?.let { index to it }
    }

    fun selectedTracks(rows: List<SharedTrackRowUi>): List<Track>? {
        val tracksById = selectedPlaylistTracks.associateBy { it.id.value }
        val resolved = rows.mapNotNull { tracksById[it.id] }
        return resolved.takeIf { it.size == rows.size }
    }
}

/** Resolves shared Internet Radio actions against the current station snapshot. */
data class SharedInternetRadioActionSources(
    val stations: List<InternetRadioStation> = emptyList(),
) {
    fun station(id: String): InternetRadioStation? =
        stations.firstOrNull { it.id == id }

    fun station(edit: NaviampInternetRadioStationEditUi): InternetRadioStation =
        edit.toInternetRadioStation()
}

/** Executes a shared track-row request after resolving its stable ID to the current domain track. */
data class ResolvedTrackRowActionHandlers(
    val onSelect: (Int, Track) -> Unit = { _, _ -> },
    val onPlayNext: (Track) -> Unit = {},
    val onStartRadio: (Int, Track) -> Unit = { _, _ -> },
    val onPlayTrackRadioNext: (Track) -> Unit = {},
    val onAddTrackRadioToQueue: (Track) -> Unit = {},
    val onAddToQueue: (Int, Track) -> Unit = { _, _ -> },
    val onDownload: (Int, Track) -> Unit = { _, _ -> },
    val onAddToPlaylist: (Int, Track, NaviampPlaylistChoiceUi?) -> Unit = { _, _, _ -> },
    val onCreatePlaylistAndAdd: (Track, String) -> Unit = { _, _ -> },
    val onToggleFavorite: (Track) -> Unit = {},
    val onGoToAlbum: (Track) -> Unit = {},
    val onGoToArtist: (Track, String?, String?) -> Unit = { _, _, _ -> },
)

fun handleResolvedTrackRowAction(
    request: SharedTrackRowActionRequest,
    tracks: List<Track>,
    handlers: ResolvedTrackRowActionHandlers,
) {
    val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
    val track = tracks.getOrNull(index) ?: return
    when (request.action) {
        SharedTrackRowAction.Select -> handlers.onSelect(index, track)
        SharedTrackRowAction.PlayNext -> handlers.onPlayNext(track)
        SharedTrackRowAction.StartRadio -> handlers.onStartRadio(index, track)
        SharedTrackRowAction.PlayTrackRadioNext -> handlers.onPlayTrackRadioNext(track)
        SharedTrackRowAction.AddTrackRadioToQueue -> handlers.onAddTrackRadioToQueue(track)
        SharedTrackRowAction.AddToQueue -> handlers.onAddToQueue(index, track)
        SharedTrackRowAction.Download -> handlers.onDownload(index, track)
        SharedTrackRowAction.AddToPlaylist -> handlers.onAddToPlaylist(index, track, request.playlistChoice)
        SharedTrackRowAction.CreatePlaylistAndAdd ->
            request.playlistName?.let { name -> handlers.onCreatePlaylistAndAdd(track, name) }
        SharedTrackRowAction.ToggleFavorite -> handlers.onToggleFavorite(track)
        SharedTrackRowAction.GoToAlbum -> handlers.onGoToAlbum(track)
        SharedTrackRowAction.GoToArtist -> handlers.onGoToArtist(track, request.artistId, request.artistName)
    }
}

/** Executes shared media-item policy against a domain object already resolved by the host snapshot. */
data class ResolvedMediaItemActionHandlers<T>(
    val onSelect: ((T) -> Unit)?,
    val onPlay: ((T, Boolean) -> Unit)?,
    val onStartRadio: ((T) -> Unit)?,
    val onFindSimilar: ((T) -> Unit)?,
    val onAddToQueue: ((T) -> Unit)?,
    val onDownload: ((T, String?) -> Unit)?,
    val onAddToPlaylist: ((T, NaviampPlaylistChoiceUi) -> Unit)?,
    val onCreatePlaylistAndAdd: ((T, String) -> Unit)?,
    val onCopy: ((T, String, Boolean) -> Unit)?,
    val onToggleFavorite: ((T) -> Unit)?,
    val onRename: ((T, String) -> Unit)?,
    val onEditSmartPlaylist: ((T) -> Unit)?,
    val onDelete: ((T) -> Unit)?,
    val onEditStation: ((T) -> Unit)?,
    val onDeleteStation: ((T) -> Unit)?,
)

enum class MediaItemActionDispatchResult {
    Dispatched,
    MissingItem,
    UnsupportedAction,
    InvalidValue,
}

fun <T> handleResolvedMediaItemAction(
    request: SharedMediaItemActionRequest,
    item: T?,
    handlers: ResolvedMediaItemActionHandlers<T>,
) : MediaItemActionDispatchResult {
    item ?: return MediaItemActionDispatchResult.MissingItem
    return when (request.action) {
        SharedMediaItemAction.Select -> handlers.onSelect.dispatch { it(item) }
        SharedMediaItemAction.Play -> handlers.onPlay.dispatch { it(item, false) }
        SharedMediaItemAction.Shuffle -> handlers.onPlay.dispatch { it(item, true) }
        SharedMediaItemAction.StartRadio -> handlers.onStartRadio.dispatch { it(item) }
        SharedMediaItemAction.FindSimilar -> handlers.onFindSimilar.dispatch { it(item) }
        SharedMediaItemAction.AddToQueue -> handlers.onAddToQueue.dispatch { it(item) }
        SharedMediaItemAction.Download -> handlers.onDownload.dispatch { it(item, request.textValue) }
        SharedMediaItemAction.AddToPlaylist -> handlers.onAddToPlaylist.dispatchWithValue(request.playlistChoice) {
            handler, choice -> handler(item, choice)
        }
        SharedMediaItemAction.CreatePlaylistAndAdd -> handlers.onCreatePlaylistAndAdd.dispatchWithText(request.playlistName) {
            handler, name -> handler(item, name)
        }
        SharedMediaItemAction.CopyPlaylist,
        SharedMediaItemAction.CopyPlaylistDeduplicated,
        -> handlers.onCopy.dispatchWithText(request.playlistName) { handler, name ->
            handler(item, name, request.action == SharedMediaItemAction.CopyPlaylistDeduplicated)
        }
        SharedMediaItemAction.ToggleFavorite -> handlers.onToggleFavorite.dispatch { it(item) }
        SharedMediaItemAction.Rename -> handlers.onRename.dispatchWithText(request.textValue) { handler, name ->
            handler(item, name)
        }
        SharedMediaItemAction.EditSmartPlaylist -> handlers.onEditSmartPlaylist.dispatch { it(item) }
        SharedMediaItemAction.Delete -> handlers.onDelete.dispatch { it(item) }
        SharedMediaItemAction.EditStation -> handlers.onEditStation.dispatch { it(item) }
        SharedMediaItemAction.DeleteStation -> handlers.onDeleteStation.dispatch { it(item) }
    }
}

fun mediaItemActionDispatchStatus(result: MediaItemActionDispatchResult): String? = when (result) {
    MediaItemActionDispatchResult.Dispatched -> null
    MediaItemActionDispatchResult.MissingItem -> "Media item not found."
    MediaItemActionDispatchResult.UnsupportedAction -> "This action is not available for this media item."
    MediaItemActionDispatchResult.InvalidValue -> "Media item action contains an invalid value."
}

private inline fun <T> T?.dispatch(action: (T) -> Unit): MediaItemActionDispatchResult =
    this?.let { action(it); MediaItemActionDispatchResult.Dispatched }
        ?: MediaItemActionDispatchResult.UnsupportedAction

private inline fun <T, V> T?.dispatchWithValue(
    value: V?,
    action: (T, V) -> Unit,
): MediaItemActionDispatchResult = when {
    this == null -> MediaItemActionDispatchResult.UnsupportedAction
    value == null -> MediaItemActionDispatchResult.InvalidValue
    else -> action(this, value).let { MediaItemActionDispatchResult.Dispatched }
}

private inline fun <T> T?.dispatchWithText(
    value: String?,
    action: (T, String) -> Unit,
): MediaItemActionDispatchResult = dispatchWithValue(value?.takeIf(String::isNotBlank), action)
