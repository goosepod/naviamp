package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun validateConnection(): ConnectionValidation
    suspend fun recentlyAddedAlbums(limit: Int = 20): List<Album>
    suspend fun album(albumId: AlbumId): AlbumDetails
    suspend fun artist(artistId: ArtistId): ArtistDetails
    suspend fun artists(limit: Int = 50): List<Artist>
    suspend fun tracks(limit: Int = 50): List<Track>
    suspend fun search(query: String, limit: Int = 20): MediaSearchResults
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

data class ProviderCapabilities(
    val supportsStreamingTranscode: Boolean,
    val supportsDownloadTranscode: Boolean,
    val supportsArtistRadio: Boolean,
    val supportsTrackRadio: Boolean,
    val supportsTrackFavorites: Boolean = false,
    val supportsTrackRatings: Boolean = false,
)

data class ConnectionValidation(
    val serverVersion: String?,
    val apiVersion: String?,
)
