package app.naviamp.provider.navidrome

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderCapabilities

class NavidromeProvider(
    private val connection: NavidromeConnection,
) : MediaProvider {
    override val id: ProviderId = ProviderId("navidrome")
    override val displayName: String = "Navidrome"
    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = false,
            supportsTrackRadio = false,
        )

    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()

    override suspend fun artists(limit: Int): List<Artist> = emptyList()

    override suspend fun tracks(limit: Int): List<Track> = emptyList()

    override suspend fun streamUrl(request: StreamRequest): String {
        val suffix = when (val quality = request.quality) {
            StreamQuality.Original -> "stream.view?id=${request.trackId.value}"
            is StreamQuality.Transcoded -> {
                val format = quality.codec.toNavidromeFormat()
                "stream.view?id=${request.trackId.value}&format=$format&maxBitRate=${quality.bitrateKbps}"
            }
        }

        return "${connection.normalizedBaseUrl}/rest/$suffix"
    }

    private fun AudioCodec.toNavidromeFormat(): String =
        when (this) {
            AudioCodec.Opus -> "opus"
            AudioCodec.Mp3 -> "mp3"
            AudioCodec.Aac -> "aac"
        }
}

