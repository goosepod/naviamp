package app.naviamp.desktop

import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class LrclibLyricsClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val baseUrl: String = "https://lrclib.net",
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun lyrics(track: Track): Lyrics? {
        val duration = track.durationSeconds ?: return null
        if (track.title.isBlank() || track.artistName.isBlank()) return null

        val url = "$baseUrl/api/get?" + listOf(
            "track_name" to track.title,
            "artist_name" to track.artistName,
            "album_name" to track.albumTitle.orEmpty(),
            "duration" to duration.toString(),
        ).joinToString("&") { (key, value) -> "${key}=${value.urlEncoded()}" }

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)")
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) return null

        val result = runCatching {
            json.decodeFromString<LrclibLyricsResponse>(response.body())
        }.getOrNull() ?: return null

        result.syncedLyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { synced ->
                lyricsFromText(
                    source = LyricsSource.Lrclib,
                    text = synced,
                )?.let { lyrics ->
                    return lyrics.copy(
                        displayArtist = result.artistName,
                        displayTitle = result.trackName,
                    )
                }
            }

        return result.plainLyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { plain ->
                lyricsFromText(
                    source = LyricsSource.Lrclib,
                    text = plain,
                )?.copy(
                    displayArtist = result.artistName,
                    displayTitle = result.trackName,
                )
            }
    }
}

@Serializable
private data class LrclibLyricsResponse(
    val trackName: String? = null,
    val artistName: String? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8)
