package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.desktop.cache.NaviampCacheDatabase
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class DesktopCache(
    private val databasePath: Path = defaultCacheDatabasePath(),
    private val maxImageCacheBytes: Long = 500L * 1024L * 1024L,
    private val maxHotImageBytes: Long = 32L * 1024L * 1024L,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val database = createDatabase(databasePath)
    private val queries = database.naviampCacheQueries
    private val hotImages = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var hotImageBytes: Long = 0

    suspend fun imageBytes(url: String): ByteArray {
        hotImage(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val now = nowMillis()
            queries.selectImage(url).executeAsOneOrNull()?.let { bytes ->
                queries.touchImage(now, url)
                putHotImage(url, bytes)
                return@withContext bytes
            }

            val bytes = URI.create(url).toURL().openStream().use { stream ->
                stream.readBytes()
            }
            queries.upsertImage(
                url = url,
                bytes = bytes,
                size_bytes = bytes.size.toLong(),
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            putHotImage(url, bytes)
            trimImageStore()
            bytes
        }
    }

    suspend fun recentlyAddedAlbums(
        provider: MediaProvider,
        limit: Int,
    ): List<Album> =
        cached(
            provider = provider,
            resourceType = "recentlyAddedAlbums",
            resourceId = limit.toString(),
            decode = { json.decodeFromString<List<AlbumDto>>(it).map { dto -> dto.toAlbum() } },
            encode = { json.encodeToString(it.map { album -> AlbumDto.fromAlbum(album) }) },
            fetch = { provider.recentlyAddedAlbums(limit) },
        )

    suspend fun album(
        provider: MediaProvider,
        albumId: AlbumId,
    ): AlbumDetails =
        cached(
            provider = provider,
            resourceType = "album",
            resourceId = albumId.value,
            decode = { json.decodeFromString<AlbumDetailsDto>(it).toAlbumDetails() },
            encode = { json.encodeToString(AlbumDetailsDto.fromAlbumDetails(it)) },
            fetch = { provider.album(albumId) },
        )

    suspend fun artist(
        provider: MediaProvider,
        artistId: ArtistId,
    ): ArtistDetails =
        cached(
            provider = provider,
            resourceType = "artist",
            resourceId = artistId.value,
            decode = { json.decodeFromString<ArtistDetailsDto>(it).toArtistDetails() },
            encode = { json.encodeToString(ArtistDetailsDto.fromArtistDetails(it)) },
            fetch = { provider.artist(artistId) },
        )

    suspend fun search(
        provider: MediaProvider,
        query: String,
        limit: Int,
    ): MediaSearchResults =
        cached(
            provider = provider,
            resourceType = "search",
            resourceId = "${query.trim().lowercase()}:$limit",
            decode = { json.decodeFromString<MediaSearchResultsDto>(it).toMediaSearchResults() },
            encode = { json.encodeToString(MediaSearchResultsDto.fromMediaSearchResults(it)) },
            fetch = { provider.search(query, limit) },
        )

    fun updateTrack(updatedTrack: Track) {
        queries.transaction {
            val albumRows = queries.selectResponsesByType("album").executeAsList()
            albumRows.forEach { row ->
                val details = json.decodeFromString<AlbumDetailsDto>(row.payload).toAlbumDetails()
                val updatedDetails = details.copy(
                    tracks = details.tracks.map { track ->
                        if (track.id == updatedTrack.id) updatedTrack else track
                    },
                )
                if (updatedDetails != details) {
                    queries.updateResponsePayload(
                        payload = json.encodeToString(AlbumDetailsDto.fromAlbumDetails(updatedDetails)),
                        cache_key = row.cache_key,
                    )
                }
            }

            val searchRows = queries.selectResponsesByType("search").executeAsList()
            searchRows.forEach { row ->
                val results = json.decodeFromString<MediaSearchResultsDto>(row.payload).toMediaSearchResults()
                val updatedResults = results.copy(
                    tracks = results.tracks.map { track ->
                        if (track.id == updatedTrack.id) updatedTrack else track
                    },
                )
                if (updatedResults != results) {
                    queries.updateResponsePayload(
                        payload = json.encodeToString(MediaSearchResultsDto.fromMediaSearchResults(updatedResults)),
                        cache_key = row.cache_key,
                    )
                }
            }
        }
    }

    fun clearProviderData() {
        queries.clearResponses()
    }

    fun clearAll() {
        synchronized(hotImages) {
            hotImages.clear()
            hotImageBytes = 0
        }
        queries.transaction {
            queries.clearResponses()
            queries.clearImages()
        }
    }

    fun stats(): CacheStats =
        CacheStats(
            databasePath = databasePath.toAbsolutePath().toString(),
            imageCount = queries.imageCacheCount().executeAsOne(),
            imageBytes = queries.imageCacheSize().executeAsOne(),
            responseCount = queries.responseCacheCount().executeAsOne(),
            hotImageCount = synchronized(hotImages) { hotImages.size },
            hotImageBytes = synchronized(hotImages) { hotImageBytes },
            maxImageBytes = maxImageCacheBytes,
            maxHotImageBytes = maxHotImageBytes,
        )

    private suspend fun <T> cached(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T {
        val key = cacheKey(provider, resourceType, resourceId)
        queries.selectResponse(key).executeAsOneOrNull()?.let { payload ->
            queries.touchResponse(nowMillis(), key)
            return decode(payload)
        }

        val value = fetch()
        val now = nowMillis()
        queries.upsertResponse(
            cache_key = key,
            provider_id = provider.cacheNamespace,
            resource_type = resourceType,
            resource_id = resourceId,
            payload = encode(value),
            created_at_epoch_millis = now,
            last_accessed_epoch_millis = now,
        )
        return value
    }

    private fun cacheKey(provider: MediaProvider, resourceType: String, resourceId: String): String =
        "${provider.cacheNamespace}:$resourceType:$resourceId"

    private fun hotImage(url: String): ByteArray? =
        synchronized(hotImages) {
            hotImages[url]
        }

    private fun putHotImage(url: String, bytes: ByteArray) {
        synchronized(hotImages) {
            hotImages.remove(url)?.let { hotImageBytes -= it.size.toLong() }
            hotImages[url] = bytes
            hotImageBytes += bytes.size.toLong()
            trimHotImages()
        }
    }

    private fun trimHotImages() {
        val iterator = hotImages.entries.iterator()
        while (hotImageBytes > maxHotImageBytes && iterator.hasNext()) {
            val entry = iterator.next()
            hotImageBytes -= entry.value.size.toLong()
            iterator.remove()
        }
    }

    private fun trimImageStore() {
        var cacheSize = queries.imageCacheSize().executeAsOne()
        if (cacheSize <= maxImageCacheBytes) return

        queries.oldestImages(100).executeAsList().forEach { image ->
            if (cacheSize <= maxImageCacheBytes) return
            queries.deleteImage(image.url)
            cacheSize -= image.size_bytes
        }
    }
}

object DesktopCaches {
    val session = DesktopCache()
}

data class CacheStats(
    val databasePath: String,
    val imageCount: Long,
    val imageBytes: Long,
    val responseCount: Long,
    val hotImageCount: Int,
    val hotImageBytes: Long,
    val maxImageBytes: Long,
    val maxHotImageBytes: Long,
)

private fun createDatabase(path: Path): NaviampCacheDatabase {
    Files.createDirectories(path.parent)
    val exists = path.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
    if (!exists) {
        NaviampCacheDatabase.Schema.create(driver)
    }
    return NaviampCacheDatabase(driver)
}

private fun defaultCacheDatabasePath(): Path =
    defaultAppDataDirectory().resolve("cache.db")

private fun defaultAppDataDirectory(): Path {
    val os = System.getProperty("os.name").lowercase()
    val home = Path.of(System.getProperty("user.home"))

    return when {
        os.contains("mac") -> home.resolve("Library").resolve("Application Support").resolve("Naviamp")
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString())
            .resolve("Naviamp")
        else -> Path.of(System.getenv("XDG_CACHE_HOME") ?: home.resolve(".cache").toString())
            .resolve("naviamp")
    }
}

private fun nowMillis(): Long =
    System.currentTimeMillis()

@Serializable
private data class MediaSearchResultsDto(
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
) {
    fun toMediaSearchResults(): MediaSearchResults =
        MediaSearchResults(
            artists = artists.map { it.toArtist() },
            albums = albums.map { it.toAlbum() },
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromMediaSearchResults(results: MediaSearchResults): MediaSearchResultsDto =
            MediaSearchResultsDto(
                artists = results.artists.map { ArtistDto.fromArtist(it) },
                albums = results.albums.map { AlbumDto.fromAlbum(it) },
                tracks = results.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
private data class ArtistDetailsDto(
    val artist: ArtistDto,
    val albums: List<AlbumDto>,
    val info: ArtistInfoDto? = null,
) {
    fun toArtistDetails(): ArtistDetails =
        ArtistDetails(
            artist = artist.toArtist(),
            albums = albums.map { it.toAlbum() },
            info = info?.toArtistInfo(),
        )

    companion object {
        fun fromArtistDetails(details: ArtistDetails): ArtistDetailsDto =
            ArtistDetailsDto(
                artist = ArtistDto.fromArtist(details.artist),
                albums = details.albums.map { AlbumDto.fromAlbum(it) },
                info = details.info?.let { ArtistInfoDto.fromArtistInfo(it) },
            )
    }
}

@Serializable
private data class AlbumDetailsDto(
    val album: AlbumDto,
    val tracks: List<TrackDto>,
) {
    fun toAlbumDetails(): AlbumDetails =
        AlbumDetails(
            album = album.toAlbum(),
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromAlbumDetails(details: AlbumDetails): AlbumDetailsDto =
            AlbumDetailsDto(
                album = AlbumDto.fromAlbum(details.album),
                tracks = details.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
private data class ArtistDto(
    val id: String,
    val name: String,
) {
    fun toArtist(): Artist =
        Artist(
            id = ArtistId(id),
            name = name,
        )

    companion object {
        fun fromArtist(artist: Artist): ArtistDto =
            ArtistDto(
                id = artist.id.value,
                name = artist.name,
            )
    }
}

@Serializable
private data class ArtistInfoDto(
    val biography: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
) {
    fun toArtistInfo(): ArtistInfo =
        ArtistInfo(
            biography = biography,
            smallImageUrl = smallImageUrl,
            mediumImageUrl = mediumImageUrl,
            largeImageUrl = largeImageUrl,
        )

    companion object {
        fun fromArtistInfo(info: ArtistInfo): ArtistInfoDto =
            ArtistInfoDto(
                biography = info.biography,
                smallImageUrl = info.smallImageUrl,
                mediumImageUrl = info.mediumImageUrl,
                largeImageUrl = info.largeImageUrl,
            )
    }
}

@Serializable
private data class AlbumDto(
    val id: String,
    val title: String,
    val artistName: String,
    val coverArtId: String? = null,
    val recentlyAddedAtIso8601: String? = null,
    val releaseYear: Int? = null,
) {
    fun toAlbum(): Album =
        Album(
            id = AlbumId(id),
            title = title,
            artistName = artistName,
            coverArtId = coverArtId,
            recentlyAddedAtIso8601 = recentlyAddedAtIso8601,
            releaseYear = releaseYear,
        )

    companion object {
        fun fromAlbum(album: Album): AlbumDto =
            AlbumDto(
                id = album.id.value,
                title = album.title,
                artistName = album.artistName,
                coverArtId = album.coverArtId,
                recentlyAddedAtIso8601 = album.recentlyAddedAtIso8601,
                releaseYear = album.releaseYear,
            )
    }
}

@Serializable
private data class TrackDto(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val audioInfo: AudioInfoDto? = null,
    val replayGain: ReplayGainDto? = null,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
) {
    fun toTrack(): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistId = artistId?.let { ArtistId(it) },
            artistName = artistName,
            albumId = albumId?.let { AlbumId(it) },
            albumTitle = albumTitle,
            albumReleaseYear = albumReleaseYear,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            audioInfo = audioInfo?.toAudioInfo(),
            replayGain = replayGain?.toReplayGain(),
            favoritedAtIso8601 = favoritedAtIso8601,
            userRating = userRating,
        )

    companion object {
        fun fromTrack(track: Track): TrackDto =
            TrackDto(
                id = track.id.value,
                title = track.title,
                artistId = track.artistId?.value,
                artistName = track.artistName,
                albumId = track.albumId?.value,
                albumTitle = track.albumTitle,
                albumReleaseYear = track.albumReleaseYear,
                durationSeconds = track.durationSeconds,
                coverArtId = track.coverArtId,
                audioInfo = track.audioInfo?.let { AudioInfoDto.fromAudioInfo(it) },
                replayGain = track.replayGain?.let { ReplayGainDto.fromReplayGain(it) },
                favoritedAtIso8601 = track.favoritedAtIso8601,
                userRating = track.userRating,
            )
    }
}

@Serializable
private data class AudioInfoDto(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val contentType: String? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): AudioInfoDto =
            AudioInfoDto(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
            )
    }
}

@Serializable
private data class ReplayGainDto(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
) {
    fun toReplayGain(): ReplayGain =
        ReplayGain(
            trackGainDb = trackGainDb,
            albumGainDb = albumGainDb,
            trackPeak = trackPeak,
            albumPeak = albumPeak,
        )

    companion object {
        fun fromReplayGain(replayGain: ReplayGain): ReplayGainDto =
            ReplayGainDto(
                trackGainDb = replayGain.trackGainDb,
                albumGainDb = replayGain.albumGainDb,
                trackPeak = replayGain.trackPeak,
                albumPeak = replayGain.albumPeak,
            )
    }
}
