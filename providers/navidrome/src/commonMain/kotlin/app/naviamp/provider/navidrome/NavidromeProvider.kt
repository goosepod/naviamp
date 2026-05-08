package app.naviamp.provider.navidrome

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.AlbumId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class NavidromeProvider(
    private val connection: NavidromeConnection,
    private val httpClient: NavidromeHttpClient = JavaNavidromeHttpClient(),
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

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validateConnection(): ConnectionValidation {
        val response = get("ping.view")
        val root = response.subsonicResponse()

        return ConnectionValidation(
            serverVersion = root.stringValue("serverVersion"),
            apiVersion = root.stringValue("version"),
        )
    }

    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> {
        val response = get(
            endpoint = "getAlbumList2.view",
            params = mapOf(
                "type" to "newest",
                "size" to limit.toString(),
            ),
        )
        val albumList = response.subsonicResponse()["albumList2"]?.jsonObject
        val albums = albumList?.get("album") as? JsonArray ?: return emptyList()

        return albums.mapNotNull { album ->
            val obj = album as? JsonObject ?: return@mapNotNull null
            Album(
                id = AlbumId(obj.stringValue("id") ?: return@mapNotNull null),
                title = obj.stringValue("name") ?: obj.stringValue("title") ?: "Unknown Album",
                artistName = obj.stringValue("artist") ?: "Unknown Artist",
                coverArtId = obj.stringValue("coverArt"),
                recentlyAddedAtIso8601 = obj.stringValue("created"),
            )
        }
    }

    override suspend fun artists(limit: Int): List<Artist> = emptyList()

    override suspend fun tracks(limit: Int): List<Track> = emptyList()

    override suspend fun streamUrl(request: StreamRequest): String {
        val params = when (val quality = request.quality) {
            StreamQuality.Original -> mapOf("id" to request.trackId.value)
            is StreamQuality.Transcoded -> {
                val format = quality.codec.toNavidromeFormat()
                mapOf(
                    "id" to request.trackId.value,
                    "format" to format,
                    "maxBitRate" to quality.bitrateKbps.toString(),
                )
            }
        }

        return url("stream.view", params)
    }

    private suspend fun get(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
    ): JsonObject {
        val body = httpClient.get(url(endpoint, params))
        val root = json.parseToJsonElement(body).jsonObject
        val response = root["subsonic-response"]?.jsonObject
            ?: throw NavidromeException("Response was not a Subsonic response.")
        val status = response.stringValue("status")

        if (status == "failed") {
            val error = response["error"]?.jsonObject
            val message = error?.stringValue("message") ?: "Navidrome request failed."
            throw NavidromeException(message)
        }

        return root
    }

    private fun JsonObject.subsonicResponse(): JsonObject =
        this["subsonic-response"]?.jsonObject
            ?: throw NavidromeException("Response was not a Subsonic response.")

    private fun url(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
    ): String {
        val allParams = authParams + params
        return "${connection.normalizedBaseUrl}/rest/$endpoint?${allParams.toQueryString()}"
    }

    private val authParams: Map<String, String>
        get() = mapOf(
            "u" to connection.username,
            "t" to connection.token,
            "s" to connection.salt,
            "v" to "1.16.1",
            "c" to "Naviamp",
            "f" to "json",
        )

    private fun AudioCodec.toNavidromeFormat(): String =
        when (this) {
            AudioCodec.Opus -> "opus"
            AudioCodec.Mp3 -> "mp3"
            AudioCodec.Aac -> "aac"
        }
}

interface NavidromeHttpClient {
    suspend fun get(url: String): String
}

class JavaNavidromeHttpClient : NavidromeHttpClient {
    private val client = HttpClient.newHttpClient()

    override suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw NavidromeException("Navidrome returned HTTP ${response.statusCode()}.")
            }
            response.body()
        }
}

class NavidromeException(message: String) : RuntimeException(message)

private fun Map<String, String>.toQueryString(): String =
    entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonElement)?.jsonPrimitive?.content
