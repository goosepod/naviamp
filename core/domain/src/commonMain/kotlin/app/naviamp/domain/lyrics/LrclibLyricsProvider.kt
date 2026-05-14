package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

abstract class LrclibLyricsProvider : LyricsProvider {
    final override val id: String = "lrclib"

    final override suspend fun lyrics(track: Track): Lyrics? {
        val query = LrclibLyricsQuery.fromTrack(track) ?: return null
        val responseBody = responseBody(query) ?: return null
        return parseResponse(responseBody)
    }

    protected abstract suspend fun responseBody(query: LrclibLyricsQuery): String?

    protected fun parseResponse(body: String): Lyrics? {
        val response = runCatching {
            LrclibJson.decodeFromString<LrclibLyricsResponse>(body)
        }.getOrNull() ?: return null

        response.syncedLyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { synced ->
                lyricsFromText(
                    source = LyricsSource.Lrclib,
                    text = synced,
                    displayArtist = response.artistName,
                    displayTitle = response.trackName,
                )?.let { return it }
            }

        return response.plainLyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { plain ->
                lyricsFromText(
                    source = LyricsSource.Lrclib,
                    text = plain,
                    displayArtist = response.artistName,
                    displayTitle = response.trackName,
                )
            }
    }
}

data class LrclibLyricsQuery(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Int,
) {
    val parameters: List<Pair<String, String>>
        get() = listOf(
            "track_name" to trackName,
            "artist_name" to artistName,
            "album_name" to albumName,
            "duration" to durationSeconds.toString(),
        )

    companion object {
        fun fromTrack(track: Track): LrclibLyricsQuery? {
            val duration = track.durationSeconds ?: return null
            if (track.title.isBlank() || track.artistName.isBlank()) return null
            return LrclibLyricsQuery(
                trackName = track.title,
                artistName = track.artistName,
                albumName = track.albumTitle.orEmpty(),
                durationSeconds = duration,
            )
        }
    }
}

private val LrclibJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class LrclibLyricsResponse(
    val trackName: String? = null,
    val artistName: String? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
