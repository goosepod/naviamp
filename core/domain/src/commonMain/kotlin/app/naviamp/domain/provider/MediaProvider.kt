package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track

interface MediaProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun recentlyAddedAlbums(limit: Int = 20): List<Album>
    suspend fun artists(limit: Int = 50): List<Artist>
    suspend fun tracks(limit: Int = 50): List<Track>
    suspend fun streamUrl(request: StreamRequest): String
}

data class ProviderCapabilities(
    val supportsStreamingTranscode: Boolean,
    val supportsDownloadTranscode: Boolean,
    val supportsArtistRadio: Boolean,
    val supportsTrackRadio: Boolean,
)

