package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun validateConnection(): ConnectionValidation
    suspend fun recentlyAddedAlbums(limit: Int = 20): List<Album>
    suspend fun album(albumId: AlbumId): AlbumDetails
    suspend fun artists(limit: Int = 50): List<Artist>
    suspend fun tracks(limit: Int = 50): List<Track>
    suspend fun search(query: String, limit: Int = 20): MediaSearchResults
    suspend fun streamUrl(request: StreamRequest): String
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
)

data class ConnectionValidation(
    val serverVersion: String?,
    val apiVersion: String?,
)
