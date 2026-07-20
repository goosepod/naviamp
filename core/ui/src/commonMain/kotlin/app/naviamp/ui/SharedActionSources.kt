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
