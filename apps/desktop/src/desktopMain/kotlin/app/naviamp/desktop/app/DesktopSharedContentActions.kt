package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest

internal fun resolveDesktopMediaItemAction(
    request: SharedMediaItemActionRequest,
    artists: List<Artist> = emptyList(),
    albums: List<Album> = emptyList(),
    onArtistAction: (SharedMediaItemActionRequest, Artist) -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest, Album) -> Unit = { _, _ -> },
) {
    when (request.kind) {
        SharedMediaItemKind.Artist -> artists
            .firstOrNull { artist -> artist.id.value == request.item.id }
            ?.let { artist -> onArtistAction(request, artist) }
        SharedMediaItemKind.Album -> albums
            .firstOrNull { album -> album.id.value == request.item.id }
            ?.let { album -> onAlbumAction(request, album) }
        else -> Unit
    }
}

internal fun resolveDesktopTrackAction(
    request: SharedTrackRowActionRequest,
    tracks: List<Track>,
    onTrackAction: (SharedTrackRowActionRequest, Int, Track) -> Unit,
) {
    val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
    tracks.getOrNull(index)?.let { track -> onTrackAction(request, index, track) }
}
