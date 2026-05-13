package app.naviamp.android

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AndroidLrclibLyricsClient(
    private val baseUrl: String = "https://lrclib.net",
) {
    suspend fun lyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val duration = track.durationSeconds ?: return@withContext null
        if (track.title.isBlank() || track.artistName.isBlank()) return@withContext null

        val query = listOf(
            "track_name" to track.title,
            "artist_name" to track.artistName,
            "album_name" to track.albumTitle.orEmpty(),
            "duration" to duration.toString(),
        ).joinToString("&") { (key, value) -> "$key=${value.urlEncoded()}" }

        val connection = (URL("$baseUrl/api/get?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Naviamp/0.9.0")
        }

        try {
            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (statusCode !in 200..299) return@withContext null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val artistName = json.optString("artistName").takeIf { it.isNotBlank() }
            val trackName = json.optString("trackName").takeIf { it.isNotBlank() }

            json.optString("syncedLyrics")
                .takeIf { it.isNotBlank() }
                ?.let { synced ->
                    lyricsFromText(synced)?.let { lyrics ->
                        return@withContext lyrics.copy(
                            displayArtist = artistName,
                            displayTitle = trackName,
                        )
                    }
                }

            json.optString("plainLyrics")
                .takeIf { it.isNotBlank() }
                ?.let { plain ->
                    lyricsFromText(plain)?.copy(
                        displayArtist = artistName,
                        displayTitle = trackName,
                    )
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun lyricsFromText(text: String): Lyrics? {
        val lines = text.lineSequence()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isBlank()) return@mapNotNull null
                val match = LrcTimestampRegex.find(line)
                if (match == null) {
                    LyricLine(startMillis = null, text = line)
                } else {
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    val lyricText = line.substring(match.range.last + 1).trim()
                    if (lyricText.isBlank()) return@mapNotNull null
                    LyricLine(
                        startMillis = minutes * 60_000L + seconds * 1_000L + fraction,
                        text = lyricText,
                    )
                }
            }
            .toList()
        if (lines.isEmpty()) return null
        return Lyrics(
            source = LyricsSource.Lrclib,
            synced = lines.any { it.startMillis != null },
            lines = lines,
        )
    }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        val LrcTimestampRegex = Regex("""^\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    }
}
