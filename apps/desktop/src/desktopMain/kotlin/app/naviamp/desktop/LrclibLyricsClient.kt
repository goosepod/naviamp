package app.naviamp.desktop

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LrclibLyricsQuery
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class LrclibLyricsClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider() {
    protected override suspend fun responseBody(query: LrclibLyricsQuery): String? {
        val url = "$baseUrl/api/get?" + query.parameters.joinToString("&") { (key, value) ->
            "${key}=${value.urlEncoded()}"
        }

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

        return response.body()
    }
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8)
