package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities
    val cacheNamespace: String
        get() = id.value

    suspend fun validateConnection(): ConnectionValidation
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
    suspend fun genres(limit: Int = 50): List<Genre> = emptyList()
    suspend fun randomSongs(
        limit: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Track> = emptyList()
    suspend fun artistRadio(artistId: ArtistId, count: Int = 50): List<Track> = emptyList()
    suspend fun albumRadio(albumId: AlbumId, count: Int = 50): List<Track> = emptyList()
    suspend fun trackRadio(trackId: TrackId, count: Int = 50): List<Track> = emptyList()
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
)

data class ConnectionValidation(
    val serverVersion: String?,
    val apiVersion: String?,
)
