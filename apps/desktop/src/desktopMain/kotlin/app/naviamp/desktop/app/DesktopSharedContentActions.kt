package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampInternetRadioStationEditUi
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.toInternetRadioStation

data class DesktopDetailActionSources(
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

data class DesktopPlaylistActionSources(
    val playlists: List<app.naviamp.domain.Playlist> = emptyList(),
    val playlistTracksById: Map<String, List<Track>> = emptyMap(),
    val selectedPlaylist: app.naviamp.domain.Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
) {
    fun playlist(id: String): app.naviamp.domain.Playlist? =
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

data class DesktopInternetRadioActionSources(
    val stations: List<InternetRadioStation> = emptyList(),
) {
    fun station(id: String): InternetRadioStation? =
        stations.firstOrNull { it.id == id }

    fun station(edit: NaviampInternetRadioStationEditUi): InternetRadioStation =
        edit.toInternetRadioStation()
}

internal fun desktopInternetRadioActions(
    actionSources: DesktopInternetRadioActionSources,
    onRefresh: () -> Unit,
    onPlayStation: (InternetRadioStation) -> Unit,
    onSaveStation: (InternetRadioStation) -> Unit,
    onDeleteStation: (InternetRadioStation) -> Unit,
): NaviampInternetRadioActions = NaviampInternetRadioActions(
    onRefresh = onRefresh,
    onStationAction = { request ->
        actionSources.station(request.station.id)?.let { station ->
            when (request.action) {
                StationRowAction.Select -> onPlayStation(station)
                StationRowAction.Edit -> Unit
                StationRowAction.Delete -> onDeleteStation(station)
            }
        }
    },
    onSaveStation = { edit -> onSaveStation(actionSources.station(edit)) },
)

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
