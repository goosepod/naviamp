package app.naviamp.android

import app.naviamp.domain.popular.PopularTracksHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AndroidPopularTracksHttpClient : PopularTracksHttpClient {
    override suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Naviamp/0.9.0")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
