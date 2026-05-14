package app.naviamp.android

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LrclibLyricsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AndroidLrclibLyricsClient(
    private val baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider() {
    protected override suspend fun responseBody(query: LrclibLyricsQuery): String? = withContext(Dispatchers.IO) {
        val queryString = query.parameters.joinToString("&") { (key, value) -> "$key=${value.urlEncoded()}" }

        val connection = (URL("$baseUrl/api/get?$queryString").openConnection() as HttpURLConnection).apply {
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

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())
}
