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
import app.naviamp.domain.provider.LibraryScanStatus
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.domain.provider.SonicPathMatch
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTracksClient
import app.naviamp.domain.popular.ArtistPopularTracksResult
import app.naviamp.domain.popular.NavidromeAgentMetadataSource
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistsClient
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class NavidromeProvider(
    private val connection: NavidromeConnection,
    private val httpClient: NavidromeHttpClient = createDefaultNavidromeHttpClient(connection.tlsSettings),
) : MediaProvider,
    ArtistPopularTracksClient,
    SimilarArtistsClient {
    override val id: ProviderId = ProviderId("navidrome")
    override val displayName: String = "Navidrome"
    override val source: String = NavidromeAgentMetadataSource
    override val cacheNamespace: String =
        "${id.value}:${connection.normalizedBaseUrl}:${connection.username}"
    private val customHeaders: Map<String, String> =
        connection.customHeaders
            .mapNotNull { header ->
                val normalized = header.normalized() ?: return@mapNotNull null
                val value = normalized.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                normalized.name to value
            }
            .toMap()
    private val baseCapabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
            supportsTrackFavorites = true,
            supportsArtistFavorites = true,
            supportsAlbumFavorites = true,
            supportsTrackRatings = true,
            supportsPlayReporting = true,
            supportsSmartPlaylists = true,
        )
    override var capabilities: ProviderCapabilities = baseCapabilities
        private set

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validateConnection(): ConnectionValidation {
        val response = get("ping.view")
        val root = response.subsonicResponse()
        capabilities = baseCapabilities.copy(
            supportsSonicSimilarity = supportsOpenSubsonicExtension(
                name = "sonicSimilarity",
                minimumVersion = 1,
            ),
        )

        return ConnectionValidation(
            serverVersion = root.stringValue("serverVersion"),
            apiVersion = root.stringValue("version"),
        )
    }

    override suspend fun libraryScanStatus(): LibraryScanStatus? {
        val response = runCatching { get("getScanStatus.view") }.getOrNull() ?: return null
        val scanStatus = response.subsonicResponse()["scanStatus"]?.jsonObject ?: return null
        return LibraryScanStatus(
            scanning = scanStatus.booleanValue("scanning"),
            count = scanStatus.intValue("count"),
            lastScan = scanStatus.stringValue("lastScan"),
            folderCount = scanStatus.intValue("folderCount"),
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

    override suspend fun popularTracks(artist: Artist, limit: Int): ArtistPopularTracksResult {
        val response = get(
            endpoint = "getTopSongs.view",
            params = mapOf(
                "artist" to artist.name,
                "count" to limit.coerceIn(1, 50).toString(),
            ),
        )
        val tracks = response.subsonicResponse()["topSongs"]
            ?.jsonObject
            ?.arrayValue("song")
            ?.mapNotNull { song -> (song as? JsonObject)?.toTrack() }
            .orEmpty()
        val candidates = tracks.mapIndexed { index, track ->
            ArtistPopularTrackCandidate(
                source = source,
                sourceTrackId = track.id.value,
                rank = index + 1,
                title = track.title,
                albumTitle = track.albumTitle,
                durationSeconds = track.durationSeconds,
            )
        }
        return ArtistPopularTracksResult(
            source = source,
            candidates = candidates,
            matchedTracksBySourceTrackId = tracks.associateBy { it.id.value },
        )
    }

    override suspend fun similarArtists(artist: Artist, limit: Int): List<SimilarArtistCandidate> {
        val info = artistInfoObject(
            artistId = artist.id,
            count = limit.coerceIn(1, 50),
            includeNotPresent = true,
        ) ?: return emptyList()
        return info.arrayValue("similarArtist")
            .mapNotNull { item ->
                val similarArtist = item as? JsonObject ?: return@mapNotNull null
                val name = similarArtist.stringValue("name")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SimilarArtistCandidate(
                    source = source,
                    sourceArtistId = similarArtist.stringValue("id") ?: name,
                    name = name,
                    imageUrl = similarArtist.stringValue("artistImageUrl")
                        ?: similarArtist.stringValue("largeImageUrl")
                        ?: similarArtist.stringValue("mediumImageUrl")
                        ?: similarArtist.stringValue("smallImageUrl")
                        ?: similarArtist.stringValue("coverArt")?.let(::coverArtUrl),
                    externalUrl = similarArtist.similarArtistExternalUrl(name),
                )
            }
    }

    private fun JsonObject.similarArtistExternalUrl(name: String): String =
        stringValue("lastFmUrl")
            ?: stringValue("url")
            ?: stringValue("artistUrl")
            ?: stringValue("musicBrainzId")?.takeIf { it.isNotBlank() }?.let { musicBrainzId ->
                "https://musicbrainz.org/artist/${musicBrainzId.urlEncode()}"
            }
            ?: "https://www.last.fm/music/${name.urlEncode()}"

    override suspend fun playlists(limit: Int): List<Playlist> {
        val response = get("getPlaylists.view")
        val playlists = response.subsonicResponse()["playlists"]
            ?.jsonObject
            ?.arrayValue("playlist")
            .orEmpty()
        val smartPlaylistIds = smartPlaylistIds()

        return playlists
            .mapNotNull { playlist ->
                (playlist as? JsonObject)?.toPlaylist(forceSmart = smartPlaylistIds.contains(playlist.stringValue("id")))
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

    override suspend fun createSmartPlaylist(definition: SmartPlaylistDefinition): Playlist {
        val body = definition.toNativePlaylistBody()
        val response = postNativeJson(
            endpoint = "playlist",
            body = json.encodeToString(JsonObject.serializer(), body),
        )
        return response.toNativeDataObject().toPlaylist(forceSmart = true)
    }

    override suspend fun updateSmartPlaylist(playlistId: String, definition: SmartPlaylistDefinition) {
        val body = definition.toNativePlaylistBody()
        putNativeJson(
            endpoint = "playlist/${playlistId.urlEncode()}",
            body = json.encodeToString(JsonObject.serializer(), body),
        )
    }

    override suspend fun smartPlaylistDefinition(playlistId: String): SmartPlaylistDefinition {
        val response = getNativeJson("playlist/${playlistId.urlEncode()}")
        return SmartPlaylistDefinition.fromJsonObject(response.toNativeDataObject())
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

    override suspend fun sonicSimilarTracks(trackId: TrackId, count: Int): List<Track> =
        sonicSimilarTrackMatches(trackId, count).map { match -> match.track }

    override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int): List<SonicSimilarTrack> =
        runCatching {
            val response = get(
                endpoint = "getSonicSimilarTracks.view",
                params = mapOf(
                    "id" to trackId.value,
                    "count" to count.coerceAtLeast(1).toString(),
                ),
            )
            response.subsonicResponse()
                .arrayValue("sonicMatch")
                .mapNotNull { match ->
                    val obj = match as? JsonObject ?: return@mapNotNull null
                    obj.get("entry")
                        ?.jsonObject
                        ?.toTrack()
                        ?.let { track ->
                            SonicSimilarTrack(
                                track = track,
                                similarity = obj.doubleValue("similarity"),
                            )
                        }
                }
                .filterNot { it.track.id == trackId }
        }.getOrDefault(emptyList())

    override suspend fun findSonicPath(
        startTrackId: TrackId,
        endTrackId: TrackId,
        count: Int,
    ): List<SonicPathMatch> =
        runCatching {
            val response = get(
                endpoint = "findSonicPath.view",
                params = mapOf(
                    "startSongId" to startTrackId.value,
                    "endSongId" to endTrackId.value,
                    "count" to count.coerceAtLeast(1).toString(),
                ),
            )
            response.subsonicResponse()
                .arrayValue("sonicMatch")
                .mapNotNull { match ->
                    val obj = match as? JsonObject ?: return@mapNotNull null
                    obj.get("entry")
                        ?.jsonObject
                        ?.toTrack()
                        ?.let { track ->
                            SonicPathMatch(
                                track = track,
                                similarity = obj.doubleValue("similarity"),
                            )
                        }
                }
        }.getOrDefault(emptyList())

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
        } + request.startPositionSeconds
            ?.takeIf { it > 0.0 }
            ?.let { mapOf("timeOffset" to it.toInt().toString()) }
            .orEmpty()

        return url("stream.view", params)
    }

    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
        get(
            endpoint = if (favorite) "star.view" else "unstar.view",
            params = mapOf("id" to trackId.value),
        )
    }

    override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
        get(
            endpoint = if (favorite) "star.view" else "unstar.view",
            params = mapOf("artistId" to artistId.value),
        )
    }

    override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
        get(
            endpoint = if (favorite) "star.view" else "unstar.view",
            params = mapOf("albumId" to albumId.value),
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

    fun ownsUrl(url: String): Boolean =
        url.startsWith("${connection.normalizedBaseUrl}/", ignoreCase = true)

    suspend fun bytes(url: String): ByteArray? =
        httpClient.getBytes(url, headers = customHeaders)

    suspend fun download(url: String, writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit): Boolean =
        httpClient.download(url, headers = customHeaders, writeChunk = writeChunk)

    override suspend fun downloadStream(
        url: String,
        httpClient: SharedHttpClient,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean =
        if (ownsUrl(url)) {
            download(url, writeChunk)
        } else {
            httpClient.download(url, writeChunk = writeChunk)
        }

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

    private suspend fun supportsOpenSubsonicExtension(name: String, minimumVersion: Int): Boolean =
        runCatching {
            val response = get("getOpenSubsonicExtensions.view")
            response.subsonicResponse()
                .arrayValue("openSubsonicExtensions")
                .any { extension ->
                    val item = extension as? JsonObject ?: return@any false
                    item.stringValue("name") == name &&
                        item.extensionVersions().any { version -> version >= minimumVersion }
                }
        }.getOrDefault(false)

    private fun JsonObject.extensionVersions(): List<Int> =
        arrayValue("versions").mapNotNull { version ->
            version.jsonPrimitive.intOrNull ?: version.jsonPrimitive.contentOrNull?.toIntOrNull()
        }.ifEmpty {
            listOfNotNull(intValue("version"))
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
        val info = artistInfoObject(artistId, count = 0, includeNotPresent = false)
            ?: return null

        return ArtistInfo(
            biography = info.stringValue("biography"),
            smallImageUrl = info.stringValue("smallImageUrl"),
            mediumImageUrl = info.stringValue("mediumImageUrl"),
            largeImageUrl = info.stringValue("largeImageUrl"),
        )
    }

    private suspend fun artistInfoObject(
        artistId: ArtistId,
        count: Int,
        includeNotPresent: Boolean,
    ): JsonObject? {
        val response = get(
            endpoint = "getArtistInfo2.view",
            params = mapOf(
                "id" to artistId.value,
                "count" to count.coerceIn(0, 50).toString(),
                "includeNotPresent" to includeNotPresent.toString(),
            ),
        )
        return response.subsonicResponse()["artistInfo2"]?.jsonObject
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
        val body = httpClient.get(url(endpoint, params), headers = customHeaders)
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

    private suspend fun postNativeJson(endpoint: String, body: String): JsonObject =
        nativeJsonResponse(
            httpClient.postJson(
                url = nativeApiUrl(endpoint),
                body = body,
                headers = customHeaders + nativeAuthHeaders(),
            ),
        )

    private suspend fun getNativeJson(endpoint: String): JsonObject =
        nativeJsonResponse(
            httpClient.get(
                url = nativeApiUrl(endpoint),
                headers = customHeaders + nativeAuthHeaders(),
            ),
        )

    private suspend fun putNativeJson(endpoint: String, body: String): JsonObject =
        nativeJsonResponse(
            httpClient.putJson(
                url = nativeApiUrl(endpoint),
                body = body,
                headers = customHeaders + nativeAuthHeaders(),
            ),
        )

    private fun nativeJsonResponse(body: String): JsonObject =
        json.parseToJsonElement(body).jsonObject

    private fun JsonObject.toNativeDataObject(): JsonObject =
        this["data"]?.jsonObject ?: this

    private fun nativeAuthHeaders(): Map<String, String> {
        val token = connection.nativeToken?.takeIf { it.isNotBlank() }
            ?: throw NavidromeException("Reconnect to Navidrome with your password before saving smart playlists.")
        return mapOf("x-nd-authorization" to "Bearer $token")
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

    private fun nativeApiUrl(endpoint: String): String =
        "${connection.normalizedBaseUrl}/api/${endpoint.trimStart('/')}"

    private fun SmartPlaylistDefinition.toNativePlaylistBody(): JsonObject =
        buildJsonObject {
            put("name", name.trim())
            comment?.trim()?.takeIf { it.isNotBlank() }?.let { put("comment", it) }
            isPublic?.let { put("public", it) }
            put("rules", toRulesJsonElement())
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
            favoritedAtIso8601 = stringValue("starred"),
        )

    private fun JsonObject.toArtist(): Artist =
        Artist(
            id = app.naviamp.domain.ArtistId(
                stringValue("id") ?: throw NavidromeException("Artist is missing an id."),
            ),
            name = stringValue("name") ?: "Unknown Artist",
            favoritedAtIso8601 = stringValue("starred"),
        )

    private suspend fun smartPlaylistIds(): Set<String> =
        runCatching {
            nativePlaylistObjects(getNativeJson("playlist"))
                .filter { it.isSmartPlaylistObject() }
                .mapNotNull { it.stringValue("id") }
                .toSet()
        }.getOrDefault(emptySet())

    private fun nativePlaylistObjects(response: JsonObject): List<JsonObject> {
        fun JsonObject.playlistArray(key: String): List<JsonObject>? =
            (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }

        val data = response["data"]
        return when (data) {
            is JsonArray -> data.mapNotNull { it as? JsonObject }
            is JsonObject -> data.playlistArray("playlists")
                ?: data.playlistArray("items")
                ?: data.playlistArray("rows")
                ?: listOf(data)
            else -> response.playlistArray("playlists")
                ?: response.playlistArray("items")
                ?: response.playlistArray("rows")
                ?: emptyList()
        }
    }

    private fun JsonObject.isSmartPlaylistObject(): Boolean =
        this["rules"] != null ||
            booleanValue("smart") == true ||
            booleanValue("smartPlaylist") == true ||
            stringValue("type")?.equals("smart", ignoreCase = true) == true

    private fun JsonObject.toPlaylist(forceSmart: Boolean = false): Playlist =
        Playlist(
            id = stringValue("id") ?: throw NavidromeException("Playlist is missing an id."),
            name = stringValue("name") ?: "Playlist",
            trackCount = intValue("songCount") ?: 0,
            durationSeconds = intValue("duration"),
            coverArtId = stringValue("coverArt"),
            isSmart = forceSmart || isSmartPlaylistObject(),
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
            bpm = intValue("bpm"),
            moods = moodValues(),
            playCount = intValue("playCount"),
            lastPlayedAtIso8601 = stringValue("played")
                ?: stringValue("lastPlayed")
                ?: stringValue("lastPlayedAt"),
        )

    private fun JsonObject.moodValues(): List<String> {
        val arrayValues = arrayValue("mood").mapNotNull { value ->
            runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
                ?: (value as? JsonObject)?.stringValue("name")
                ?: (value as? JsonObject)?.stringValue("value")
        }
        val scalarValues = stringValue("mood")
            ?.split(",", ";")
            ?.map { it.trim() }
            .orEmpty()
        return (arrayValues + scalarValues)
            .filter { it.isNotBlank() }
            .distinct()
    }

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
    suspend fun get(url: String, headers: Map<String, String>): String = get(url)
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        throw UnsupportedOperationException("POST is not supported by this Navidrome HTTP client.")
    }
    suspend fun putJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        throw UnsupportedOperationException("PUT is not supported by this Navidrome HTTP client.")
    }
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        throw UnsupportedOperationException("Binary GET is not supported by this Navidrome HTTP client.")
    }
    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        throw UnsupportedOperationException("Streaming download is not supported by this Navidrome HTTP client.")
    }
}

data class NavidromeApiCall(
    val method: String,
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

fun recordNavidromeApiCall(
    url: String,
    method: String = "GET",
    startedAt: Long,
    durationMillis: Long,
    success: Boolean,
    errorMessage: String?,
) {
    NavidromeApiCallHistory.record(
        NavidromeApiCall(
            method = method,
            endpoint = url.substringBefore("?").trimEnd('/').substringAfterLast('/').ifBlank { "unknown" },
            sanitizedUrl = url.sanitizedNavidromeUrl(),
            startedAtEpochMillis = startedAt,
            durationMillis = durationMillis.coerceAtLeast(0),
            success = success,
            errorMessage = errorMessage,
        ),
    )
}

class NavidromeException(message: String) : RuntimeException(message)

private fun List<Pair<String, String>>.toQueryString(): String =
    joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

private fun String.sanitizedNavidromeUrl(): String =
    runCatching {
        val pathAndQuery = substringAfter("://", this).substringAfter("/", "")
        val path = pathAndQuery.substringBefore("?")
        val query = pathAndQuery.substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .filter { it.isNotBlank() }
            .joinToString("&") { rawParam ->
                val key = rawParam.substringBefore("=")
                if (key in SensitiveNavidromeQueryKeys) {
                    "$key=<redacted>"
                } else {
                    rawParam
                }
            }
        buildString {
            append("/")
            append(path)
            if (query.isNotBlank()) {
                append("?")
                append(query)
            }
        }
    }.getOrDefault("<unparseable url>")

private val SensitiveNavidromeQueryKeys = setOf("u", "t", "s")

private fun JsonObject.stringValue(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

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
