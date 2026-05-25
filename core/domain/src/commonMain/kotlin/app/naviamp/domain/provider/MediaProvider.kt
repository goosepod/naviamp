package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities
    val cacheNamespace: String
        get() = id.value

    suspend fun validateConnection(): ConnectionValidation
    suspend fun libraryScanStatus(): LibraryScanStatus? = null
    suspend fun recentlyAddedAlbums(limit: Int = 20): List<Album>
    suspend fun album(albumId: AlbumId): AlbumDetails
    suspend fun artist(artistId: ArtistId): ArtistDetails
    suspend fun artists(limit: Int = 50): List<Artist>
    suspend fun albums(limit: Int = 50, offset: Int = 0): List<Album> = emptyList()
    suspend fun albumList(type: AlbumListType, limit: Int = 20): List<Album> = emptyList()
    suspend fun albumsByGenre(genre: String, limit: Int = 20): List<Album> = emptyList()
    suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int = 20): List<Album> = emptyList()
    suspend fun tracks(limit: Int = 50): List<Track>
    suspend fun search(query: String, limit: Int = 20): MediaSearchResults
    suspend fun playlists(limit: Int = 20): List<Playlist> = emptyList()
    suspend fun playlistTracks(playlistId: String): List<Track> = emptyList()
    suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        throw UnsupportedOperationException("Playlist creation is not supported by $displayName.")
    }
    suspend fun createSmartPlaylist(definition: SmartPlaylistDefinition): Playlist {
        throw UnsupportedOperationException("Smart playlist creation is not supported by $displayName.")
    }
    suspend fun updateSmartPlaylist(playlistId: String, definition: SmartPlaylistDefinition) {
        throw UnsupportedOperationException("Smart playlist edits are not supported by $displayName.")
    }
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        throw UnsupportedOperationException("Playlist edits are not supported by $displayName.")
    }
    suspend fun renamePlaylist(playlistId: String, name: String) {
        throw UnsupportedOperationException("Playlist edits are not supported by $displayName.")
    }
    suspend fun deletePlaylist(playlistId: String) {
        throw UnsupportedOperationException("Playlist deletion is not supported by $displayName.")
    }
    suspend fun genres(limit: Int = 50): List<Genre> = emptyList()
    suspend fun randomSongs(
        limit: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Track> = emptyList()
    suspend fun internetRadioStations(): List<InternetRadioStation> = emptyList()
    suspend fun createInternetRadioStation(
        name: String,
        streamUrl: String,
        homePageUrl: String?,
    ): InternetRadioStation {
        throw UnsupportedOperationException("Internet radio stations are not supported by $displayName.")
    }
    suspend fun updateInternetRadioStation(station: InternetRadioStation) {
        throw UnsupportedOperationException("Internet radio station edits are not supported by $displayName.")
    }
    suspend fun deleteInternetRadioStation(stationId: String) {
        throw UnsupportedOperationException("Internet radio station deletion is not supported by $displayName.")
    }
    suspend fun artistRadio(artistId: ArtistId, count: Int = 50): List<Track> = emptyList()
    suspend fun albumRadio(albumId: AlbumId, count: Int = 50): List<Track> = emptyList()
    suspend fun trackRadio(trackId: TrackId, count: Int = 50): List<Track> = emptyList()
    suspend fun lyrics(trackId: TrackId): Lyrics? = null
    suspend fun reportNowPlaying(trackId: TrackId) = Unit
    suspend fun reportPlayed(trackId: TrackId, playedAtEpochMillis: Long) = Unit
    suspend fun streamUrl(request: StreamRequest): String
    suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
        throw UnsupportedOperationException("Track favorites are not supported by $displayName.")
    }
    suspend fun setTrackRating(trackId: TrackId, rating: Int?) {
        throw UnsupportedOperationException("Track ratings are not supported by $displayName.")
    }
    fun coverArtUrl(coverArtId: String): String
}

data class MediaSearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

enum class AlbumListType(val providerValue: String) {
    Newest("newest"),
    Random("random"),
    Frequent("frequent"),
    Recent("recent"),
    Starred("starred"),
}

data class ProviderCapabilities(
    val supportsStreamingTranscode: Boolean,
    val supportsDownloadTranscode: Boolean,
    val supportsArtistRadio: Boolean,
    val supportsAlbumRadio: Boolean,
    val supportsTrackRadio: Boolean,
    val supportsTrackFavorites: Boolean = false,
    val supportsTrackRatings: Boolean = false,
    val supportsPlayReporting: Boolean = false,
    val supportsSmartPlaylists: Boolean = false,
)

data class ConnectionValidation(
    val serverVersion: String?,
    val apiVersion: String?,
)

data class LibraryScanStatus(
    val scanning: Boolean?,
    val count: Int?,
    val lastScan: String?,
    val folderCount: Int?,
) {
    val signature: String?
        get() = lastScan?.takeIf { it.isNotBlank() }
}
