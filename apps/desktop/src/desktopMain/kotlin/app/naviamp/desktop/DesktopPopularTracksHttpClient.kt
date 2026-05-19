package app.naviamp.desktop

import app.naviamp.domain.popular.PopularTracksHttpClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DesktopPopularTracksHttpClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : PopularTracksHttpClient {
    override suspend fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)")
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null
        if (response.statusCode() !in 200..299) return null
        return response.body()
    }
}
