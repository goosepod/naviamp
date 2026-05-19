package app.naviamp.domain.popular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeezerPopularTracksClient(
    private val httpClient: PopularTracksHttpClient,
    private val baseUrl: String = "https://api.deezer.com",
) : ArtistPopularTracksClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun popularTracks(artistName: String, limit: Int): List<ArtistPopularTrackCandidate> {
        val artistId = deezerArtistId(artistName) ?: return emptyList()
        val response = httpClient.get("$baseUrl/artist/$artistId/top?limit=${limit.coerceIn(1, 50)}")
            ?: return emptyList()
        return json.decodeFromString<DeezerTracksResponse>(response).data.mapIndexed { index, track ->
            ArtistPopularTrackCandidate(
                source = DeezerPopularTrackSource,
                sourceTrackId = track.id.toString(),
                rank = index + 1,
                title = track.titleShort?.takeIf { it.isNotBlank() } ?: track.title,
                albumTitle = track.album?.title,
                durationSeconds = track.duration,
            )
        }
    }

    private suspend fun deezerArtistId(artistName: String): Long? {
        val query = artistName.trim().takeIf { it.isNotEmpty() } ?: return null
        val response = httpClient.get("$baseUrl/search/artist?q=${query.urlEncoded()}")
            ?: return null
        val artists = json.decodeFromString<DeezerArtistSearchResponse>(response).data
        return artists.firstOrNull { it.name.equals(query, ignoreCase = true) }?.id
            ?: artists.firstOrNull()?.id
    }
}

interface PopularTracksHttpClient {
    suspend fun get(url: String): String?
}

expect fun String.urlEncoded(): String

@Serializable
private data class DeezerArtistSearchResponse(
    val data: List<DeezerArtistDto> = emptyList(),
)

@Serializable
private data class DeezerArtistDto(
    val id: Long,
    val name: String,
)

@Serializable
private data class DeezerTracksResponse(
    val data: List<DeezerTrackDto> = emptyList(),
)

@Serializable
private data class DeezerTrackDto(
    val id: Long,
    val title: String,
    @SerialName("title_short") val titleShort: String? = null,
    val duration: Int? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
private data class DeezerAlbumDto(
    val title: String? = null,
)
