package app.naviamp.provider.jellyfin

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistCredit
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Genre
import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.CoverArtSize
import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.PlaybackReportState
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.urlEncodedParameter
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTracksClient
import app.naviamp.domain.popular.ArtistPopularTracksResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class JellyfinMusicLibrary(
    val id: String,
    val name: String,
)

/** Shared Jellyfin music adapter. Hosts provide only the HTTP/TLS engine through the factory. */
class JellyfinProvider(
    connection: JellyfinConnection,
    sessionServices: JellyfinSessionServiceFactory,
) : MediaProvider, ArtistPopularTracksClient {
    private val service = sessionServices.create(connection.tlsSettings)
    private var activeConnection = connection
    private val playbackMutex = Mutex()
    private var playbackSessionCounter = 0L
    private var playbackSession: JellyfinPlaybackSession? = null
    val connection: JellyfinConnection
        get() = activeConnection

    override val id = ProviderId(ProviderIdJellyfin)
    override val displayName: String = "Jellyfin"
    override val source: String = JellyfinMetadataSource
    override val selectedMusicFolderIds: List<String> = connection.selectedMusicFolderIds
    override val cacheNamespace: String = buildString {
        append("$ProviderIdJellyfin:${connection.normalizedBaseUrl}:${connection.username}")
        if (selectedMusicFolderIds.isNotEmpty()) {
            append(":libraries=")
            append(selectedMusicFolderIds.joinToString(","))
        }
    }
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = true,
        supportsDownloadTranscode = true,
        supportsArtistRadio = true,
        supportsAlbumRadio = true,
        supportsTrackRadio = true,
        supportsTrackFavorites = true,
        supportsArtistFavorites = true,
        supportsAlbumFavorites = true,
        supportsPlayReporting = true,
    )

    override suspend fun validateConnection(): ConnectionValidation =
        ConnectionValidation(
            serverVersion = connection.serverVersion,
            apiVersion = connection.serverVersion,
        )

    suspend fun musicLibraries(): List<JellyfinMusicLibrary> =
        service.getJson(
            readyConnection(),
            path = "UserViews",
            parameters = listOf("userId" to connection.userId),
        ).items().mapNotNull { item ->
            if (!item.string("CollectionType").equals("music", ignoreCase = true)) return@mapNotNull null
            JellyfinMusicLibrary(
                id = item.string("Id") ?: return@mapNotNull null,
                name = item.string("Name") ?: "Music",
            )
        }

    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
        albumPage(
            request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
            sortBy = "DateCreated",
            sortOrder = "Descending",
        ).items

    override suspend fun artists(limit: Int): List<Artist> =
        artistsPage(MediaPageRequest(limit = limit.coerceIn(1, 200))).items

    override suspend fun artistsPage(request: MediaPageRequest): MediaPage<Artist> =
        itemPage(
            request = request,
            includeItemTypes = "MusicArtist",
            mapper = { it.toArtist() },
        )

    override suspend fun albums(limit: Int, offset: Int): List<Album> =
        albumsPage(MediaPageRequest(offset = offset, limit = limit.coerceIn(1, 200))).items

    override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> = albumPage(request)

    override suspend fun albumList(type: AlbumListType, limit: Int): List<Album> {
        val request = MediaPageRequest(limit = limit.coerceIn(1, 200))
        val (sortBy, sortOrder) = when (type) {
            AlbumListType.Newest -> "DateCreated" to "Descending"
            AlbumListType.Random -> "Random" to "Ascending"
            AlbumListType.Frequent -> "PlayCount" to "Descending"
            AlbumListType.Recent -> "DatePlayed" to "Descending"
            AlbumListType.Starred -> "SortName" to "Ascending"
        }
        return albumPage(
            request = request,
            sortBy = sortBy,
            sortOrder = sortOrder,
            extraParameters = if (type == AlbumListType.Starred) listOf("isFavorite" to "true") else emptyList(),
        ).items
    }

    override suspend fun albumsByGenre(genre: String, limit: Int): List<Album> =
        albumPage(
            request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
            extraParameters = listOf("genres" to genre.trim()),
        ).items

    override suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int): List<Album> =
        albumPage(
            request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
            extraParameters = listOf("years" to (fromYear..toYear).joinToString(",")),
        ).items

    private suspend fun albumPage(
        request: MediaPageRequest,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        albumArtistId: String? = null,
        searchTerm: String? = null,
        extraParameters: List<Pair<String, String>> = emptyList(),
    ): MediaPage<Album> = itemPage(
        request = request,
        includeItemTypes = "MusicAlbum",
        sortBy = sortBy,
        sortOrder = sortOrder,
        extraParameters = buildList {
            albumArtistId?.let { add("albumArtistIds" to it) }
            searchTerm?.let { add("searchTerm" to it) }
            addAll(extraParameters)
        },
        mapper = { it.toAlbum() },
    )

    override suspend fun tracks(limit: Int): List<Track> =
        tracksPage(MediaPageRequest(limit = limit.coerceIn(1, 200))).items

    override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> =
        trackPage(request)

    override suspend fun genres(limit: Int): List<Genre> =
        service.getJson(
            readyConnection(),
            path = "Genres",
            parameters = buildList {
                add("userId" to connection.userId)
                add("includeItemTypes" to "Audio")
                add("recursive" to "true")
                add("limit" to limit.coerceIn(1, 200).toString())
                selectedMusicFolderIds.singleOrNull()?.let { add("parentId" to it) }
            },
        ).items().mapNotNull { item -> item.string("Name")?.let(::Genre) }

    override suspend fun randomSongs(
        limit: Int,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
    ): List<Track> = itemPage(
        request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
        includeItemTypes = "Audio",
        sortBy = "Random",
        extraParameters = buildList {
            genre?.trim()?.takeIf(String::isNotEmpty)?.let { add("genres" to it) }
            if (fromYear != null && toYear != null) add("years" to (fromYear..toYear).joinToString(","))
        },
        mapper = { it.toTrack() },
    ).items

    override suspend fun artistRadio(artistId: ArtistId, count: Int): List<Track> =
        instantMix("Artists/${artistId.value}/InstantMix", count)

    override suspend fun albumRadio(albumId: AlbumId, count: Int): List<Track> =
        instantMix("Albums/${albumId.value}/InstantMix", count)

    override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> =
        instantMix("Songs/${trackId.value}/InstantMix", count)

    override suspend fun genreRadio(genre: String, count: Int): List<Track> =
        instantMix("MusicGenres/${genre.jellyfinPathSegment()}/InstantMix", count)

    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) =
        setFavorite(trackId.value, favorite)

    override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) =
        setFavorite(artistId.value, favorite)

    override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) =
        setFavorite(albumId.value, favorite)

    override suspend fun popularTracks(artist: Artist, limit: Int): ArtistPopularTracksResult {
        val tracks = itemPage(
            request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
            includeItemTypes = "Audio",
            sortBy = "PlayCount",
            sortOrder = "Descending",
            extraParameters = listOf("artistIds" to artist.id.value),
            mapper = { it.toTrack() },
        ).items
        return ArtistPopularTracksResult(
            source = source,
            candidates = tracks.mapIndexed { index, track ->
                ArtistPopularTrackCandidate(
                    source = source,
                    sourceTrackId = track.id.value,
                    rank = index + 1,
                    title = track.title,
                    albumTitle = track.albumTitle,
                    durationSeconds = track.durationSeconds,
                )
            },
            matchedTracksBySourceTrackId = tracks.associateBy { it.id.value },
        )
    }

    private suspend fun trackPage(
        request: MediaPageRequest,
        parentId: String? = selectedMusicFolderIds.singleOrNull(),
        searchTerm: String? = null,
    ): MediaPage<Track> = itemPage(
        request = request,
        includeItemTypes = "Audio",
        parentId = parentId,
        extraParameters = buildList {
            searchTerm?.let { add("searchTerm" to it) }
        },
        mapper = { it.toTrack() },
    )

    override suspend fun album(albumId: AlbumId): AlbumDetails {
        val albumObject = item(albumId.value)
        val tracks = trackPage(
            request = MediaPageRequest(limit = 200),
            parentId = albumId.value,
        ).items
        return AlbumDetails(albumObject.toAlbum(), tracks)
    }

    override suspend fun artist(artistId: ArtistId): ArtistDetails {
        val artistObject = item(artistId.value)
        val albums = albumPage(
            request = MediaPageRequest(limit = 200),
            albumArtistId = artistId.value,
        ).items
        return ArtistDetails(artistObject.toArtist(), albums)
    }

    override suspend fun search(query: String, limit: Int): MediaSearchResults {
        val normalized = query.trim()
        if (normalized.isEmpty()) return MediaSearchResults()
        val page = MediaPageRequest(limit = limit.coerceIn(1, 200))
        return MediaSearchResults(
            artists = itemPage(
                request = page,
                includeItemTypes = "MusicArtist",
                extraParameters = listOf("searchTerm" to normalized),
                mapper = { it.toArtist() },
            ).items,
            albums = albumPage(page, searchTerm = normalized).items,
            tracks = trackPage(page, searchTerm = normalized).items,
        )
    }

    override suspend fun searchArtistsPage(query: String, request: MediaPageRequest): MediaPage<Artist> =
        itemPage(
            request = request,
            includeItemTypes = "MusicArtist",
            extraParameters = listOf("searchTerm" to query.trim()),
            mapper = { it.toArtist() },
        )

    override suspend fun searchAlbumsPage(query: String, request: MediaPageRequest): MediaPage<Album> =
        albumPage(request, searchTerm = query.trim())

    override suspend fun searchTracksPage(query: String, request: MediaPageRequest): MediaPage<Track> =
        trackPage(request, searchTerm = query.trim())

    override suspend fun playlists(limit: Int): List<Playlist> =
        itemPage(
            request = MediaPageRequest(limit = limit.coerceIn(1, 200)),
            includeItemTypes = "Playlist",
            parentId = null,
            mapper = { it.toPlaylist() },
        ).items

    override suspend fun playlistTracks(playlistId: String): List<Track> =
        playlistItems(playlistId).map { it.toTrack() }

    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Playlist name is required." }
        val connection = readyConnection()
        val response = service.postJson(
            connection = connection,
            path = "Playlists",
            body = buildJsonObject {
                put("Name", trimmedName)
                put("Ids", JsonArray(trackIds.map { JsonPrimitive(it.value) }))
                put("UserId", connection.userId)
                put("MediaType", "Audio")
                put("IsPublic", false)
            }.toString(),
        )
        return Playlist(
            id = response.string("Id") ?: throw JellyfinException("Jellyfin did not return the new playlist id."),
            name = trimmedName,
            trackCount = trackIds.size,
        )
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        val connection = readyConnection()
        service.post(
            connection = connection,
            path = "Playlists/$playlistId/Items",
            parameters = listOf(
                "ids" to trackIds.joinToString(",") { it.value },
                "userId" to connection.userId,
            ),
        )
    }

    override suspend fun replacePlaylistTracks(
        playlistId: String,
        currentTrackIds: List<TrackId>,
        trackIds: List<TrackId>,
    ) {
        val entryIds = playlistItems(playlistId).mapNotNull { it.string("PlaylistItemId") }
        if (entryIds.isNotEmpty()) {
            service.delete(
                connection = readyConnection(),
                path = "Playlists/$playlistId/Items",
                parameters = listOf("entryIds" to entryIds.joinToString(",")),
            )
        }
        addTracksToPlaylist(playlistId, trackIds)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Playlist name is required." }
        service.postJsonWithoutResponse(
            connection = readyConnection(),
            path = "Playlists/$playlistId",
            body = buildJsonObject { put("Name", trimmedName) }.toString(),
        )
    }

    override suspend fun deletePlaylist(playlistId: String) {
        service.delete(readyConnection(), path = "Items/$playlistId")
    }

    override suspend fun reportNowPlaying(trackId: TrackId) {
        playbackMutex.withLock {
            if (playbackSession?.trackId != trackId) startPlaybackSession(trackId)
        }
    }

    override suspend fun reportPlaybackState(
        trackId: TrackId,
        state: PlaybackReportState,
        positionSeconds: Double?,
    ) {
        playbackMutex.withLock {
            val session = playbackSession
                ?.takeIf { it.trackId == trackId }
                ?: startPlaybackSession(trackId)
            val positionTicks = positionSeconds
                ?.takeIf { it >= 0.0 }
                ?.times(JellyfinTicksPerSecond)
                ?.toLong()
                ?: 0L
            when (state) {
                PlaybackReportState.Stopped -> {
                    service.postJsonWithoutResponse(
                        connection = readyConnection(),
                        path = "Sessions/Playing/Stopped",
                        body = playbackReportBody(session, positionTicks).toString(),
                    )
                    playbackSession = null
                }
                PlaybackReportState.Starting,
                PlaybackReportState.Playing,
                PlaybackReportState.Paused,
                -> service.postJsonWithoutResponse(
                    connection = readyConnection(),
                    path = "Sessions/Playing/Progress",
                    body = playbackReportBody(
                        session = session,
                        positionTicks = positionTicks,
                        isPaused = state == PlaybackReportState.Paused,
                    ).toString(),
                )
            }
        }
    }

    override suspend fun lyrics(trackId: TrackId): Lyrics? =
        runCatching {
            service.getJson(
                connection = readyConnection(),
                path = "Audio/${trackId.value}/Lyrics",
            ).toLyrics()
        }.getOrNull()

    override suspend fun streamUrl(request: StreamRequest): String {
        val authentication = listOf(
            "api_key" to connection.accessToken,
            "deviceId" to connection.deviceId,
        )
        return when (val quality = request.quality) {
            StreamQuality.Original -> connection.apiUrl(
                path = "Audio/${request.trackId.value}/stream",
                parameters = listOf("static" to "true") + authentication,
            )
            is StreamQuality.Transcoded -> {
                val codec = quality.codec.jellyfinCodec()
                val bitrate = (quality.bitrateKbps.coerceAtLeast(1) * 1_000).toString()
                connection.apiUrl(
                    path = "Audio/${request.trackId.value}/universal",
                    parameters = buildList {
                        addAll(authentication)
                        add("userId" to connection.userId)
                        add("container" to codec)
                        add("transcodingContainer" to codec)
                        add("transcodingProtocol" to "http")
                        add("audioCodec" to codec)
                        add("maxStreamingBitrate" to bitrate)
                        add("audioBitRate" to bitrate)
                        add("enableRedirection" to "false")
                        request.startPositionSeconds
                            ?.takeIf { it > 0.0 }
                            ?.let { add("startTimeTicks" to (it * JellyfinTicksPerSecond).toLong().toString()) }
                    },
                )
            }
        }
    }

    override fun coverArtUrl(coverArtId: String): String =
        coverArtUrl(coverArtId, CoverArtSize.Thumbnail)

    override fun coverArtUrl(coverArtId: String, size: CoverArtSize): String =
        connection.apiUrl(
            path = "Items/$coverArtId/Images/Primary",
            parameters = listOf(
                "maxWidth" to size.pixels.toString(),
                "quality" to "90",
            ),
        )

    override suspend fun bytesForOwnedUrl(url: String): ByteArray? = service.getBytes(connection, url)

    override suspend fun downloadStream(
        url: String,
        httpClient: SharedHttpClient,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = service.download(readyConnection(), url, writeChunk)

    private suspend fun item(id: String): JsonObject =
        service.getJson(
            readyConnection(),
            path = "Items/$id",
            parameters = listOf("userId" to connection.userId),
        )

    private suspend fun playlistItems(playlistId: String): List<JsonObject> {
        val connection = readyConnection()
        return service.getJson(
            connection = connection,
            path = "Playlists/$playlistId/Items",
            parameters = listOf(
                "userId" to connection.userId,
                "limit" to JellyfinPlaylistItemLimit.toString(),
                "fields" to JellyfinItemFields,
                "enableUserData" to "true",
                "imageTypeLimit" to "1",
            ),
        ).items()
    }

    private suspend fun startPlaybackSession(trackId: TrackId): JellyfinPlaybackSession {
        val session = JellyfinPlaybackSession(
            trackId = trackId,
            id = "naviamp-${activeConnection.deviceId}-${++playbackSessionCounter}",
        )
        playbackSession = session
        service.postJsonWithoutResponse(
            connection = readyConnection(),
            path = "Sessions/Playing",
            body = playbackReportBody(session, positionTicks = 0L).toString(),
        )
        return session
    }

    private fun playbackReportBody(
        session: JellyfinPlaybackSession,
        positionTicks: Long,
        isPaused: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("ItemId", session.trackId.value)
        put("PlaySessionId", session.id)
        put("PositionTicks", positionTicks.coerceAtLeast(0L))
        put("PlayMethod", "DirectPlay")
        put("CanSeek", true)
        put("IsPaused", isPaused)
    }

    private suspend fun instantMix(path: String, count: Int): List<Track> {
        val connection = readyConnection()
        return service.getJson(
            connection,
            path = path,
            parameters = listOf(
                "userId" to connection.userId,
                "limit" to count.coerceIn(1, 200).toString(),
                "fields" to JellyfinItemFields,
                "enableUserData" to "true",
                "imageTypeLimit" to "1",
            ),
        ).items().map { it.toTrack() }
    }

    private suspend fun setFavorite(itemId: String, favorite: Boolean) {
        service.setFavorite(readyConnection(), itemId, favorite)
    }

    private suspend fun <T> itemPage(
        request: MediaPageRequest,
        includeItemTypes: String,
        parentId: String? = selectedMusicFolderIds.singleOrNull(),
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        extraParameters: List<Pair<String, String>> = emptyList(),
        mapper: (JsonObject) -> T,
    ): MediaPage<T> {
        val connection = readyConnection()
        val response = service.getJson(
            connection,
            path = "Items",
            parameters = buildList {
                add("userId" to connection.userId)
                add("recursive" to "true")
                add("includeItemTypes" to includeItemTypes)
                add("startIndex" to request.offset.toString())
                add("limit" to request.limit.toString())
                add("sortBy" to sortBy)
                add("sortOrder" to sortOrder)
                add("fields" to JellyfinItemFields)
                add("enableUserData" to "true")
                add("imageTypeLimit" to "1")
                parentId?.let { add("parentId" to it) }
                addAll(extraParameters)
            },
        )
        val items = response.items().map(mapper)
        val total = response.int("TotalRecordCount")
        return MediaPage(
            items = items,
            offset = request.offset,
            limit = request.limit,
            hasMore = total?.let { request.offset + items.size < it } ?: (items.size == request.limit),
        )
    }

    private suspend fun readyConnection(): JellyfinConnection {
        if (activeConnection.userId == PendingJellyfinUserId) {
            activeConnection = service.restore(activeConnection)
        }
        return activeConnection
    }

    private fun JsonObject.toArtist(): Artist = Artist(
        id = ArtistId(string("Id") ?: throw JellyfinException("Jellyfin artist is missing an id.")),
        name = string("Name") ?: "Unknown Artist",
        favoritedAtIso8601 = favoriteMarker(),
    )

    private fun JsonObject.toAlbum(): Album = Album(
        id = AlbumId(string("Id") ?: throw JellyfinException("Jellyfin album is missing an id.")),
        title = string("Name") ?: "Unknown Album",
        artistName = string("AlbumArtist")
            ?: artistCredits("AlbumArtists").joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
        coverArtId = coverArtId(),
        recentlyAddedAtIso8601 = string("DateCreated"),
        releaseYear = int("ProductionYear"),
        favoritedAtIso8601 = favoriteMarker(),
        artistCredits = artistCredits("AlbumArtists"),
    )

    private fun JsonObject.toTrack(): Track {
        val audioStream = array("MediaStreams")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("Type").equals("Audio", ignoreCase = true) }
        val mediaSource = array("MediaSources").firstOrNull() as? JsonObject
        val credits = artistCredits("ArtistItems")
        return Track(
            id = TrackId(string("Id") ?: throw JellyfinException("Jellyfin track is missing an id.")),
            title = string("Name") ?: "Unknown Track",
            artistId = credits.firstOrNull()?.id,
            artistName = array("Artists").stringValues().joinToString(", ")
                .ifBlank { credits.joinToString(", ") { it.name } }
                .ifBlank { "Unknown Artist" },
            albumId = string("AlbumId")?.let(::AlbumId),
            albumTitle = string("Album"),
            albumReleaseYear = int("ProductionYear"),
            durationSeconds = long("RunTimeTicks")?.div(JellyfinTicksPerSecond)?.toInt(),
            coverArtId = coverArtId(),
            audioInfo = AudioInfo(
                codec = audioStream?.string("Codec") ?: mediaSource?.string("Container"),
                bitrateKbps = (audioStream?.int("BitRate") ?: mediaSource?.int("Bitrate"))?.div(1_000),
                contentType = mediaSource?.string("Container"),
                bitDepth = audioStream?.int("BitDepth"),
                samplingRateHz = audioStream?.int("SampleRate"),
            ),
            replayGain = null,
            favoritedAtIso8601 = favoriteMarker(),
            bpm = int("Bpm"),
            genres = array("Genres").stringValues(),
            playCount = objectValue("UserData")?.int("PlayCount"),
            lastPlayedAtIso8601 = objectValue("UserData")?.string("LastPlayedDate"),
            musicFolderId = string("ParentId"),
            artistCredits = credits,
        )
    }

    private fun JsonObject.toPlaylist(): Playlist = Playlist(
        id = string("Id") ?: throw JellyfinException("Jellyfin playlist is missing an id."),
        name = string("Name") ?: "Untitled Playlist",
        trackCount = int("ChildCount") ?: int("RecursiveItemCount") ?: 0,
        durationSeconds = long("RunTimeTicks")?.div(JellyfinTicksPerSecond)?.toInt(),
        coverArtId = coverArtId(),
    )

    private fun JsonObject.toLyrics(): Lyrics? {
        val metadata = objectValue("Metadata")
        val lyricRows = array("Lyrics")
            .mapNotNull { it as? JsonObject }
            .mapNotNull { row ->
                val text = row.rawString("Text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                row to LyricLine(
                    startMillis = row.long("Start")?.jellyfinTicksToMillis(),
                    text = text,
                )
            }
        val lines = lyricRows.map { it.second }
        if (lines.isEmpty()) return null
        val cueLines = lyricRows.mapIndexedNotNull { index, (row, line) ->
            val cues = row.array("Cues").mapNotNull { cueValue ->
                val cue = cueValue as? JsonObject ?: return@mapNotNull null
                val startPosition = cue.int("Position")?.coerceIn(0, line.text.length) ?: return@mapNotNull null
                val endPosition = cue.int("EndPosition")?.coerceIn(startPosition, line.text.length)
                    ?: return@mapNotNull null
                LyricCue(
                    startMillis = cue.long("Start")?.jellyfinTicksToMillis(),
                    endMillis = cue.long("End")?.jellyfinTicksToMillis(),
                    text = line.text.substring(startPosition, endPosition),
                    byteStart = line.text.utf8ByteOffset(startPosition),
                    byteEnd = line.text.utf8ByteOffset(endPosition),
                )
            }
            if (cues.isEmpty()) return@mapIndexedNotNull null
            LyricCueLine(
                lineIndex = index,
                startMillis = line.startMillis,
                endMillis = cues.lastOrNull()?.endMillis ?: lines.getOrNull(index + 1)?.startMillis,
                text = line.text,
                cues = cues,
            )
        }
        return Lyrics(
            source = LyricsSource.Provider,
            synced = metadata?.boolean("IsSynced") ?: lines.any { it.startMillis != null },
            lines = lines,
            displayArtist = metadata?.string("Artist"),
            displayTitle = metadata?.string("Title"),
            offsetMillis = metadata?.long("Offset")?.jellyfinTicksToMillis()?.toInt() ?: 0,
            kind = "jellyfin",
            cueLines = cueLines,
        )
    }

    private fun JsonObject.artistCredits(key: String): List<ArtistCredit> =
        array(key).mapNotNull { value ->
            val artist = value as? JsonObject ?: return@mapNotNull null
            val name = artist.string("Name") ?: return@mapNotNull null
            ArtistCredit(artist.string("Id")?.let(::ArtistId), name)
        }.distinctBy { it.id?.value ?: it.name.lowercase() }

    private fun JsonObject.coverArtId(): String? = when {
        objectValue("ImageTags")?.string("Primary") != null -> string("Id")
        string("PrimaryImageItemId") != null -> string("PrimaryImageItemId")
        string("AlbumPrimaryImageTag") != null -> string("AlbumId")
        else -> null
    }

    private fun JsonObject.favoriteMarker(): String? =
        "favorite".takeIf { objectValue("UserData")?.boolean("IsFavorite") == true }
}

private fun JsonObject.items(): List<JsonObject> =
    array("Items").mapNotNull { it as? JsonObject }

private fun JsonObject.string(name: String): String? =
    runCatching { this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.getOrNull()

private fun JsonObject.rawString(name: String): String? =
    runCatching { this[name]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.int(name: String): Int? =
    runCatching { this[name]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonObject.long(name: String): Long? =
    runCatching { this[name]?.jsonPrimitive?.longOrNull }.getOrNull()

@Suppress("unused")
private fun JsonObject.double(name: String): Double? =
    runCatching { this[name]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull()

private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun List<JsonElement>.stringValues(): List<String> =
    mapNotNull { value -> runCatching { value.jsonPrimitive.contentOrNull?.trim() }.getOrNull() }
        .filter(String::isNotEmpty)
        .distinct()

private const val JellyfinTicksPerSecond = 10_000_000L
private const val JellyfinPlaylistItemLimit = 10_000
private const val JellyfinMetadataSource = "jellyfin"
internal const val PendingJellyfinUserId = "pending"
private const val JellyfinItemFields =
    "DateCreated,Genres,MediaSources,MediaStreams,PrimaryImageAspectRatio,Overview,ParentId"

private fun String.jellyfinPathSegment(): String = urlEncodedParameter().replace("+", "%20")

private fun Long.jellyfinTicksToMillis(): Long = this / 10_000L

private fun AudioCodec.jellyfinCodec(): String = when (this) {
    AudioCodec.Opus -> "opus"
    AudioCodec.Mp3 -> "mp3"
    AudioCodec.Aac -> "aac"
}

private fun String.utf8ByteOffset(characterIndex: Int): Int =
    substring(0, characterIndex.coerceIn(0, length)).encodeToByteArray().size

private data class JellyfinPlaybackSession(
    val trackId: TrackId,
    val id: String,
)
