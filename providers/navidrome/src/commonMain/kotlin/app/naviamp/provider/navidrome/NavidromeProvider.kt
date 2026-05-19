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
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class NavidromeProvider(
    private val connection: NavidromeConnection,
    private val httpClient: NavidromeHttpClient = createDefaultNavidromeHttpClient(connection.tlsSettings),
) : MediaProvider {
    override val id: ProviderId = ProviderId("navidrome")
    override val displayName: String = "Navidrome"
    override val cacheNamespace: String =
        "${id.value}:${connection.normalizedBaseUrl}:${connection.username}"
    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
            supportsTrackFavorites = true,
            supportsTrackRatings = true,
            supportsPlayReporting = true,
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

    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
        albumList(AlbumListType.Newest, limit)

    override suspend fun albumList(type: AlbumListType, limit: Int): List<Album> =
        albumList(
            type = type.providerValue,
            limit = limit,
        )

    override suspend fun albumsByGenre(genre: String, limit: Int): List<Album> =
        albumList(
            type = "byGenre",
            limit = limit,
            extraParams = mapOf("genre" to genre),
        )

    override suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int): List<Album> =
        albumList(
            type = "byYear",
            limit = limit,
            extraParams = mapOf(
                "fromYear" to fromYear.toString(),
                "toYear" to toYear.toString(),
            ),
        )

    private suspend fun albumList(
        type: String,
        limit: Int,
        extraParams: Map<String, String> = emptyMap(),
    ): List<Album> {
        val response = get(
            endpoint = "getAlbumList2.view",
            params = mapOf(
                "type" to type,
                "size" to limit.toString(),
            ) + extraParams,
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

    override suspend fun playlists(limit: Int): List<Playlist> {
        val response = get("getPlaylists.view")
        val playlists = response.subsonicResponse()["playlists"]
            ?.jsonObject
            ?.arrayValue("playlist")
            .orEmpty()

        return playlists
            .mapNotNull { playlist ->
                (playlist as? JsonObject)?.toPlaylist()
            }
            .take(limit)
    }

    override suspend fun playlistTracks(playlistId: String): List<Track> {
        val response = get(
            endpoint = "getPlaylist.view",
            params = mapOf("id" to playlistId),
        )
        val playlist = response.subsonicResponse()["playlist"]?.jsonObject
            ?: return emptyList()
        return playlist.arrayValue("entry").mapNotNull { entry ->
            (entry as? JsonObject)?.toTrack()
        }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw NavidromeException("Playlist name is required.")
        val response = get(
            endpoint = "createPlaylist.view",
            params = listOf("name" to trimmedName) + trackIds.map { "songId" to it.value },
        )
        val playlist = response.subsonicResponse()["playlist"]?.jsonObject
        if (playlist != null) return playlist.toPlaylist()

        return Playlist(
            id = trimmedName,
            name = trimmedName,
            trackCount = trackIds.size,
        )
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        get(
            endpoint = "updatePlaylist.view",
            params = listOf("playlistId" to playlistId) + trackIds.map { "songIdToAdd" to it.value },
        )
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw NavidromeException("Playlist name is required.")
        get(
            endpoint = "updatePlaylist.view",
            params = listOf(
                "playlistId" to playlistId,
                "name" to trimmedName,
            ),
        )
    }

    override suspend fun deletePlaylist(playlistId: String) {
        get(
            endpoint = "deletePlaylist.view",
            params = listOf("id" to playlistId),
        )
    }

    override suspend fun genres(limit: Int): List<Genre> {
        val response = get("getGenres.view")
        val genres = response.subsonicResponse()["genres"]
            ?.jsonObject
            ?.arrayValue("genre")
            .orEmpty()

        return genres
            .mapNotNull { genre ->
                val primitiveName = runCatching { genre.jsonPrimitive.content }.getOrNull()
                val obj = genre as? JsonObject
                if (obj == null) {
                    primitiveName?.let { Genre(name = it) } ?: return@mapNotNull null
                } else {
                    Genre(
                        name = obj.stringValue("value")
                            ?: obj.stringValue("name")
                            ?: primitiveName
                            ?: return@mapNotNull null,
                        albumCount = obj.intValue("albumCount"),
                        trackCount = obj.intValue("songCount"),
                    )
                }
            }
            .sortedWith(compareByDescending<Genre> { it.albumCount ?: 0 }.thenBy { it.name.lowercase() })
            .take(limit)
    }

    override suspend fun randomSongs(
        limit: Int,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
    ): List<Track> {
        val params = buildMap {
            put("size", limit.coerceAtLeast(1).toString())
            genre?.takeIf { it.isNotBlank() }?.let { put("genre", it) }
            fromYear?.let { put("fromYear", it.toString()) }
            toYear?.let { put("toYear", it.toString()) }
        }
        val response = get(
            endpoint = "getRandomSongs.view",
            params = params,
        )
        val songs = response.subsonicResponse()["randomSongs"]
            ?.jsonObject
            ?.arrayValue("song")
            .orEmpty()
        return songs.mapNotNull { song ->
            (song as? JsonObject)?.toTrack()
        }
    }

    override suspend fun internetRadioStations(): List<InternetRadioStation> {
        val response = get("getInternetRadioStations.view")
        val stations = response.subsonicResponse()["internetRadioStations"]
            ?.jsonObject
            ?.arrayValue("internetRadioStation")
            .orEmpty()
        return stations.mapNotNull { station ->
            (station as? JsonObject)?.toInternetRadioStation()
        }
    }

    override suspend fun createInternetRadioStation(
        name: String,
        streamUrl: String,
        homePageUrl: String?,
    ): InternetRadioStation {
        val trimmedName = name.trim()
        val trimmedStreamUrl = streamUrl.trim()
        if (trimmedName.isBlank()) throw NavidromeException("Station name is required.")
        if (trimmedStreamUrl.isBlank()) throw NavidromeException("Stream URL is required.")
        get(
            endpoint = "createInternetRadioStation.view",
            params = internetRadioParams(
                name = trimmedName,
                streamUrl = trimmedStreamUrl,
                homePageUrl = homePageUrl,
            ),
        )
        return internetRadioStations().firstOrNull {
            it.name == trimmedName && it.streamUrl == trimmedStreamUrl
        } ?: InternetRadioStation(
            id = trimmedStreamUrl,
            name = trimmedName,
            streamUrl = trimmedStreamUrl,
            homePageUrl = homePageUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun updateInternetRadioStation(station: InternetRadioStation) {
        val trimmedName = station.name.trim()
        val trimmedStreamUrl = station.streamUrl.trim()
        if (trimmedName.isBlank()) throw NavidromeException("Station name is required.")
        if (trimmedStreamUrl.isBlank()) throw NavidromeException("Stream URL is required.")
        get(
            endpoint = "updateInternetRadioStation.view",
            params = listOf("id" to station.id) + internetRadioParams(
                name = trimmedName,
                streamUrl = trimmedStreamUrl,
                homePageUrl = station.homePageUrl,
            ),
        )
    }

    override suspend fun deleteInternetRadioStation(stationId: String) {
        get(
            endpoint = "deleteInternetRadioStation.view",
            params = listOf("id" to stationId),
        )
    }

    override suspend fun artistRadio(artistId: ArtistId, count: Int): List<Track> =
        similarSongs(
            endpoint = "getSimilarSongs2.view",
            responseKey = "similarSongs2",
            id = artistId.value,
            count = count,
        ).ifEmpty {
            artistRadioFallback(artistId, count)
        }

    override suspend fun albumRadio(albumId: AlbumId, count: Int): List<Track> =
        similarSongs(
            endpoint = "getSimilarSongs.view",
            responseKey = "similarSongs",
            id = albumId.value,
            count = count,
        ).ifEmpty {
            albumRadioFallback(albumId, count)
        }

    override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> =
        similarSongs(
            endpoint = "getSimilarSongs.view",
            responseKey = "similarSongs",
            id = trackId.value,
            count = count,
        )
            .filterNot { it.id == trackId }
            .ifEmpty {
                trackRadioFallback(trackId, count)
            }

    override suspend fun lyrics(trackId: TrackId): Lyrics? {
        val response = runCatching {
            get(
                endpoint = "getLyricsBySongId.view",
                params = mapOf("id" to trackId.value),
            )
        }.getOrNull() ?: return null
        val lyricsList = response.subsonicResponse()["lyricsList"]?.jsonObject ?: return null
        val structuredLyrics = lyricsList["structuredLyrics"] as? JsonArray ?: return null
        return structuredLyrics
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it.toLyrics() }
            .sortedWith(
                compareByDescending<Lyrics> { it.hasTimedLines }
                    .thenByDescending { it.lines.size },
            )
            .firstOrNull()
    }

    override suspend fun reportNowPlaying(trackId: TrackId) {
        scrobble(
            trackId = trackId,
            submission = false,
            playedAtEpochMillis = null,
        )
    }

    override suspend fun reportPlayed(trackId: TrackId, playedAtEpochMillis: Long) {
        scrobble(
            trackId = trackId,
            submission = true,
            playedAtEpochMillis = playedAtEpochMillis,
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

    private suspend fun similarSongs(
        endpoint: String,
        responseKey: String,
        id: String,
        count: Int,
    ): List<Track> =
        runCatching {
            val response = get(
                endpoint = endpoint,
                params = mapOf(
                    "id" to id,
                    "count" to count.coerceAtLeast(1).toString(),
                ),
            )
            response.subsonicResponse()[responseKey]
                ?.jsonObject
                ?.arrayValue("song")
                .orEmpty()
                .mapNotNull { song ->
                    (song as? JsonObject)?.toTrack()
                }
        }.getOrDefault(emptyList())

    private suspend fun artistRadioFallback(artistId: ArtistId, count: Int): List<Track> =
        runCatching {
            artist(artistId).albums
                .flatMap { album -> album(album.id).tracks }
                .distinctBy { it.id }
                .shuffled()
                .take(count.coerceAtLeast(1))
        }.getOrDefault(emptyList())

    private suspend fun albumRadioFallback(albumId: AlbumId, count: Int): List<Track> =
        runCatching {
            album(albumId).tracks
                .shuffled()
                .take(count.coerceAtLeast(1))
        }.getOrDefault(emptyList())

    private suspend fun trackRadioFallback(trackId: TrackId, count: Int): List<Track> {
        val seed = runCatching { song(trackId) }.getOrNull() ?: return emptyList()
        val albumTracks = seed.albumId?.let { albumId ->
            runCatching { album(albumId).tracks }.getOrDefault(emptyList())
        }.orEmpty()
        val artistTracks = seed.artistId?.let { artistId ->
            runCatching {
                artist(artistId).albums.flatMap { album -> album(album.id).tracks }
            }.getOrDefault(emptyList())
        }.orEmpty()

        return (albumTracks + artistTracks)
            .distinctBy { it.id }
            .filterNot { it.id == trackId }
            .shuffled()
            .take(count.coerceAtLeast(1))
    }

    private suspend fun song(trackId: TrackId): Track? {
        val response = get(
            endpoint = "getSong.view",
            params = mapOf("id" to trackId.value),
        )
        return response.subsonicResponse()["song"]?.jsonObject?.toTrack()
    }

    private suspend fun scrobble(
        trackId: TrackId,
        submission: Boolean,
        playedAtEpochMillis: Long?,
    ) {
        get(
            endpoint = "scrobble.view",
            params = buildMap {
                put("id", trackId.value)
                put("submission", submission.toString())
                playedAtEpochMillis?.let { put("time", it.toString()) }
            },
        )
    }

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
    ): JsonObject =
        get(endpoint, params.entries.map { it.key to it.value })

    private suspend fun get(
        endpoint: String,
        params: List<Pair<String, String>>,
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
    ): String =
        url(endpoint, params.entries.map { it.key to it.value })

    private fun url(
        endpoint: String,
        params: List<Pair<String, String>>,
    ): String {
        val allParams = authParams.toList() + params
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

    private fun internetRadioParams(
        name: String,
        streamUrl: String,
        homePageUrl: String?,
    ): List<Pair<String, String>> =
        buildList {
            add("name" to name)
            add("streamUrl" to streamUrl)
            homePageUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("homePageUrl" to it)
            }
        }

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

    private fun JsonObject.toPlaylist(): Playlist =
        Playlist(
            id = stringValue("id") ?: throw NavidromeException("Playlist is missing an id."),
            name = stringValue("name") ?: "Playlist",
            trackCount = intValue("songCount") ?: 0,
            durationSeconds = intValue("duration"),
            coverArtId = stringValue("coverArt"),
        )

    private fun JsonObject.toInternetRadioStation(): InternetRadioStation =
        InternetRadioStation(
            id = stringValue("id") ?: throw NavidromeException("Internet radio station is missing an id."),
            name = stringValue("name") ?: "Internet Radio",
            streamUrl = stringValue("streamUrl") ?: throw NavidromeException("Internet radio station is missing a stream URL."),
            homePageUrl = stringValue("homePageUrl"),
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
                bitDepth = intValue("bitDepth"),
                samplingRateHz = intValue("samplingRate"),
            ),
            replayGain = replayGainValue(),
            favoritedAtIso8601 = stringValue("starred"),
            userRating = intValue("userRating")?.takeIf { it in 1..5 },
        )

    private fun JsonObject.replayGainValue(): ReplayGain? {
        val replayGain = this["replayGain"]?.jsonObject
        fun value(vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { key ->
                replayGain?.doubleValue(key) ?: doubleValue(key)
            }

        val trackGain = value("trackGain", "trackGainDb", "replayGainTrackGain", "replaygainTrackGain")
        val albumGain = value("albumGain", "albumGainDb", "replayGainAlbumGain", "replaygainAlbumGain")
        val trackPeak = value("trackPeak", "replayGainTrackPeak", "replaygainTrackPeak")
        val albumPeak = value("albumPeak", "replayGainAlbumPeak", "replaygainAlbumPeak")
        if (trackGain == null && albumGain == null && trackPeak == null && albumPeak == null) return null
        return ReplayGain(
            trackGainDb = trackGain,
            albumGainDb = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak,
        )
    }

    private fun JsonObject.toLyrics(): Lyrics? {
        val lineArray = this["line"] as? JsonArray ?: return null
        val lines = lineArray
            .mapNotNull { it as? JsonObject }
            .mapNotNull { line ->
                val value = line.stringValue("value")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LyricLine(
                    startMillis = line.longValue("start"),
                    text = value,
                )
            }
        if (lines.isEmpty()) return null
        return Lyrics(
            source = LyricsSource.Provider,
            synced = booleanValue("synced") ?: lines.any { it.startMillis != null },
            lines = lines,
            displayArtist = stringValue("displayArtist"),
            displayTitle = stringValue("displayTitle"),
            language = stringValue("lang"),
            offsetMillis = intValue("offset") ?: 0,
        )
    }
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

class NavidromeException(message: String) : RuntimeException(message)

private fun List<Pair<String, String>>.toQueryString(): String =
    joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun JsonObject.intValue(key: String): Int? =
    stringValue(key)?.toIntOrNull()

private fun JsonObject.longValue(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull ?: stringValue(key)?.toLongOrNull()

private fun JsonObject.doubleValue(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull ?: stringValue(key)?.toDoubleOrNull()

private fun JsonObject.booleanValue(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull ?: stringValue(key)?.toBooleanStrictOrNull()

private fun JsonObject.arrayValue(key: String): JsonArray =
    this[key] as? JsonArray ?: JsonArray(emptyList())
