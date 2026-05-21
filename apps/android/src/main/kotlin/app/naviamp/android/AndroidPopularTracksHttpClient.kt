package app.naviamp.android

import app.naviamp.domain.network.SharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AndroidSharedHttpClient : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (!headers.containsKey("Accept")) setRequestProperty("Accept", "application/json")
            if (!headers.containsKey("User-Agent")) setRequestProperty("User-Agent", "Naviamp/0.9.0")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class AndroidPopularTracksHttpClient : SharedHttpClient by AndroidSharedHttpClient()
