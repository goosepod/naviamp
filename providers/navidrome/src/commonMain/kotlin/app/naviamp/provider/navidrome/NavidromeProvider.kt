package app.naviamp.provider.navidrome

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    override val cacheNamespace: String =
        "${id.value}:${connection.normalizedBaseUrl}:${connection.username}"
    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = false,
            supportsTrackRadio = false,
            supportsTrackFavorites = true,
            supportsTrackRatings = true,
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
            obj.toAlbum()
        }
    }

    override suspend fun album(albumId: AlbumId): AlbumDetails {
        val response = get(
            endpoint = "getAlbum.view",
            params = mapOf("id" to albumId.value),
        )
        val album = response.subsonicResponse()["album"]?.jsonObject
            ?: throw NavidromeException("Album was not found.")
        val songs = album["song"] as? JsonArray ?: JsonArray(emptyList())

        return AlbumDetails(
            album = album.toAlbum(),
            tracks = songs.mapNotNull { song ->
                (song as? JsonObject)?.toTrack()
            },
        )
    }

    override suspend fun artist(artistId: ArtistId): ArtistDetails {
        val response = get(
            endpoint = "getArtist.view",
            params = mapOf("id" to artistId.value),
        )
        val artist = response.subsonicResponse()["artist"]?.jsonObject
            ?: throw NavidromeException("Artist was not found.")
        val albums = artist["album"] as? JsonArray ?: JsonArray(emptyList())
        val info = runCatching { artistInfo(artistId) }.getOrNull()

        return ArtistDetails(
            artist = artist.toArtist(),
            albums = albums.mapNotNull { album ->
                (album as? JsonObject)?.toAlbum()
            },
            info = info,
        )
    }

    override suspend fun artists(limit: Int): List<Artist> {
        val response = get("getArtists.view")
        val artistsRoot = response.subsonicResponse()["artists"]?.jsonObject
            ?: return emptyList()
        val indexes = artistsRoot.arrayValue("index")
        return indexes
            .flatMap { index ->
                (index as? JsonObject)?.arrayValue("artist").orEmpty()
            }
            .mapNotNull { artist ->
                (artist as? JsonObject)?.toArtist()
            }
            .take(limit)
    }

    override suspend fun albums(limit: Int, offset: Int): List<Album> {
        val response = get(
            endpoint = "getAlbumList2.view",
            params = mapOf(
                "type" to "alphabeticalByName",
                "size" to limit.toString(),
                "offset" to offset.toString(),
            ),
        )
        val albumList = response.subsonicResponse()["albumList2"]?.jsonObject
        val albums = albumList?.get("album") as? JsonArray ?: return emptyList()

        return albums.mapNotNull { album ->
            (album as? JsonObject)?.toAlbum()
        }
    }

    override suspend fun tracks(limit: Int): List<Track> = emptyList()

    override suspend fun search(query: String, limit: Int): MediaSearchResults {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return MediaSearchResults()

        val response = get(
            endpoint = "search3.view",
            params = mapOf(
                "query" to trimmedQuery,
                "artistCount" to limit.toString(),
                "albumCount" to limit.toString(),
                "songCount" to limit.toString(),
            ),
        )
        val searchResult = response.subsonicResponse()["searchResult3"]?.jsonObject
            ?: return MediaSearchResults()

        return MediaSearchResults(
            artists = searchResult.arrayValue("artist").mapNotNull { artist ->
                (artist as? JsonObject)?.toArtist()
            },
            albums = searchResult.arrayValue("album").mapNotNull { album ->
                (album as? JsonObject)?.toAlbum()
            },
            tracks = searchResult.arrayValue("song").mapNotNull { song ->
                (song as? JsonObject)?.toTrack()
            },
        )
    }

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

    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
        get(
            endpoint = if (favorite) "star.view" else "unstar.view",
            params = mapOf("id" to trackId.value),
        )
    }

    override suspend fun setTrackRating(trackId: TrackId, rating: Int?) {
        val normalizedRating = rating?.coerceIn(1, 5) ?: 0
        get(
            endpoint = "setRating.view",
            params = mapOf(
                "id" to trackId.value,
                "rating" to normalizedRating.toString(),
            ),
        )
    }

    override fun coverArtUrl(coverArtId: String): String =
        url("getCoverArt.view", mapOf("id" to coverArtId))

    private suspend fun artistInfo(artistId: ArtistId): ArtistInfo? {
        val response = get(
            endpoint = "getArtistInfo2.view",
            params = mapOf(
                "id" to artistId.value,
                "count" to "0",
            ),
        )
        val info = response.subsonicResponse()["artistInfo2"]?.jsonObject
            ?: return null

        return ArtistInfo(
            biography = info.stringValue("biography"),
            smallImageUrl = info.stringValue("smallImageUrl"),
            mediumImageUrl = info.stringValue("mediumImageUrl"),
            largeImageUrl = info.stringValue("largeImageUrl"),
        )
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

    private fun JsonObject.toAlbum(): Album =
        Album(
            id = AlbumId(stringValue("id") ?: throw NavidromeException("Album is missing an id.")),
            title = stringValue("name") ?: stringValue("title") ?: "Unknown Album",
            artistName = stringValue("artist") ?: "Unknown Artist",
            coverArtId = stringValue("coverArt"),
            recentlyAddedAtIso8601 = stringValue("created"),
            releaseYear = intValue("year"),
        )

    private fun JsonObject.toArtist(): Artist =
        Artist(
            id = app.naviamp.domain.ArtistId(
                stringValue("id") ?: throw NavidromeException("Artist is missing an id."),
            ),
            name = stringValue("name") ?: "Unknown Artist",
        )

    private fun JsonObject.toTrack(): Track =
        Track(
            id = TrackId(stringValue("id") ?: throw NavidromeException("Track is missing an id.")),
            title = stringValue("title") ?: "Unknown Track",
            artistId = stringValue("artistId")?.let { ArtistId(it) },
            artistName = stringValue("artist") ?: "Unknown Artist",
            albumId = stringValue("albumId")?.let { AlbumId(it) },
            albumTitle = stringValue("album"),
            albumReleaseYear = intValue("year"),
            durationSeconds = intValue("duration"),
            coverArtId = stringValue("coverArt"),
            audioInfo = AudioInfo(
                codec = stringValue("suffix")?.uppercase(),
                bitrateKbps = intValue("bitRate"),
                contentType = stringValue("contentType"),
            ),
            replayGain = null,
            favoritedAtIso8601 = stringValue("starred"),
            userRating = intValue("userRating")?.takeIf { it in 1..5 },
        )
}

interface NavidromeHttpClient {
    suspend fun get(url: String): String
}

data class NavidromeApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

object NavidromeApiCallHistory {
    private const val MaxCalls = 150
    private val lock = Any()
    private val calls = ArrayDeque<NavidromeApiCall>()

    fun record(call: NavidromeApiCall) {
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    fun recent(limit: Int = 50): List<NavidromeApiCall> =
        synchronized(lock) {
            calls.takeLast(limit.coerceAtLeast(0)).asReversed()
        }
}

class JavaNavidromeHttpClient : NavidromeHttpClient {
    private val client = HttpClient.newHttpClient()

    override suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) {
                    throw NavidromeException("Navidrome returned HTTP ${response.statusCode()}.")
                }
                response.body().also {
                    recordApiCall(
                        url = url,
                        startedAt = startedAt,
                        success = true,
                        errorMessage = null,
                    )
                }
            } catch (exception: Exception) {
                recordApiCall(
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = exception.message ?: exception::class.simpleName,
                )
                throw exception
            }
        }

    private fun recordApiCall(
        url: String,
        startedAt: Long,
        success: Boolean,
        errorMessage: String?,
    ) {
        NavidromeApiCallHistory.record(
            NavidromeApiCall(
                endpoint = url.navidromeEndpoint(),
                sanitizedUrl = url.sanitizedNavidromeUrl(),
                startedAtEpochMillis = startedAt,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                success = success,
                errorMessage = errorMessage,
            ),
        )
    }
}

class NavidromeException(message: String) : RuntimeException(message)

private fun String.navidromeEndpoint(): String =
    runCatching {
        URI.create(this).path.substringAfterLast('/')
    }.getOrDefault("unknown")

private fun String.sanitizedNavidromeUrl(): String =
    runCatching {
        val uri = URI.create(this)
        buildString {
            append(uri.path)
            val query = uri.rawQuery
                ?.split("&")
                ?.joinToString("&") { rawParam ->
                    val key = rawParam.substringBefore("=")
                    if (key in setOf("u", "t", "s")) {
                        "$key=<redacted>"
                    } else {
                        rawParam
                    }
                }
            if (!query.isNullOrBlank()) {
                append("?")
                append(query)
            }
        }
    }.getOrDefault("<unparseable url>")

private fun Map<String, String>.toQueryString(): String =
    entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun JsonObject.intValue(key: String): Int? =
    stringValue(key)?.toIntOrNull()

private fun JsonObject.arrayValue(key: String): JsonArray =
    this[key] as? JsonArray ?: JsonArray(emptyList())
