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
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.exists

class DesktopCache(
    private val databasePath: Path = defaultCacheDatabasePath(),
    private val maxImageCacheBytes: Long = 500L * 1024L * 1024L,
    private var maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    private val maxAudioWaveformCacheBytes: Long = 32L * 1024L * 1024L,
    private val maxHotImageBytes: Long = 32L * 1024L * 1024L,
    private val audioCacheDirectory: Path = defaultAudioCacheDirectory(),
    private val downloadDirectory: Path = defaultDownloadDirectory(),
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

    fun latestMediaSource(): SavedMediaSource? =
        queries.selectLatestMediaSource().executeAsOneOrNull()?.toSavedMediaSource()

    fun mediaSources(): List<SavedMediaSource> =
        queries.selectMediaSources().executeAsList().map { it.toSavedMediaSource() }

    fun mediaSource(sourceId: String): SavedMediaSource? =
        queries.selectMediaSourceById(sourceId).executeAsOneOrNull()?.toSavedMediaSource()

    fun deleteMediaSource(sourceId: String) {
        queries.deleteMediaSource(sourceId)
    }

    fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0)
        trimAudioStore()
    }

    fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedAudioMetadata? {
        val qualityKey = quality.cacheKey()
        val row = queries.selectCachedAudioMetadata(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
        ).executeAsOneOrNull() ?: return null

        val path = Path.of(row.file_path)
        return CachedAudioMetadata(
            path = path,
            exists = path.exists(),
            sizeBytes = row.size_bytes,
            contentType = row.content_type,
            createdAtEpochMillis = row.created_at_epoch_millis,
            lastAccessedEpochMillis = row.last_accessed_epoch_millis,
        )
    }

    suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedAudioFile? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val row = queries.selectCachedAudio(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
            ).executeAsOneOrNull() ?: return@withContext null

            val path = Path.of(row.file_path)
            if (!path.exists()) {
                queries.deleteCachedAudio(sourceId, trackId.value, qualityKey)
                return@withContext null
            }

            queries.touchCachedAudio(nowMillis(), sourceId, trackId.value, qualityKey)
            CachedAudioFile(
                path = path,
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
            )
        }

    suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): DownloadedAudioFile? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val row = queries.selectDownloadedAudioFile(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
            ).executeAsOneOrNull() ?: return@withContext null

            val path = Path.of(row.file_path)
            if (!path.exists()) {
                queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
                return@withContext null
            }

            DownloadedAudioFile(
                path = path,
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
            )
        }

    suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val row = queries.selectCachedAudioWaveform(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
            ).executeAsOneOrNull() ?: return@withContext null

            queries.touchCachedAudioWaveform(nowMillis(), sourceId, trackId.value, qualityKey)
            AudioWaveform(
                amplitudes = json.decodeFromString<List<Float>>(row.amplitudes_json),
            )
        }

    suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        analyzer: AudioWaveformAnalyzer = AudioWaveformAnalyzer(),
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            cachedAudioWaveform(sourceId, trackId, quality)?.let { return@withContext it }
            val audioPath = downloadedAudioFile(sourceId, trackId, quality)?.path
                ?: cachedAudioFile(sourceId, trackId, quality)?.path
                ?: return@withContext null
            val waveform = analyzer.analyze(audioPath) ?: return@withContext null
            val qualityKey = quality.cacheKey()
            val amplitudesJson = json.encodeToString(waveform.amplitudes)
            val now = nowMillis()
            queries.upsertCachedAudioWaveform(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
                audio_file_path = audioPath.toAbsolutePath().toString(),
                bucket_count = waveform.amplitudes.size.toLong(),
                amplitudes_json = amplitudesJson,
                size_bytes = amplitudesJson.toByteArray(Charsets.UTF_8).size.toLong(),
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            trimAudioWaveformStore()
            waveform
        }

    suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics? =
        withContext(Dispatchers.IO) {
            cachedLyrics(sourceId, trackId)?.let { return@withContext it }
            val lyrics = provider.lyrics(trackId) ?: return@withContext null
            upsertLyrics(sourceId, trackId, lyrics)
            lyrics
        }

    suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
        client: LrclibLyricsClient = LrclibLyricsClient(),
    ): Lyrics? =
        withContext(Dispatchers.IO) {
            cachedLrclibLyrics(sourceId, track.id)?.let { return@withContext it }
            val lyrics = client.lyrics(track) ?: return@withContext null
            upsertLrclibLyrics(sourceId, track.id, lyrics)
            lyrics
        }

    suspend fun cachedLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? =
        withContext(Dispatchers.IO) {
            val row = queries.selectCachedLyrics(
                source_id = sourceId,
                remote_track_id = trackId.value,
            ).executeAsOneOrNull() ?: return@withContext null

            queries.touchCachedLyrics(nowMillis(), sourceId, trackId.value)
            Lyrics(
                source = row.lyric_source.toLyricsSource(),
                synced = row.synced != 0L,
                lines = json.decodeFromString<List<LyricLineDto>>(row.lines_json).map { it.toLyricLine() },
                displayArtist = row.display_artist,
                displayTitle = row.display_title,
                language = row.language,
                offsetMillis = row.offset_millis.toInt(),
            )
        }

    private fun cachedLrclibLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? {
        val row = queries.selectCachedLrclibLyrics(
            source_id = sourceId,
            remote_track_id = trackId.value,
        ).executeAsOneOrNull() ?: return null

        queries.touchCachedLrclibLyrics(nowMillis(), sourceId, trackId.value)
        return Lyrics(
            source = LyricsSource.Lrclib,
            synced = row.synced != 0L,
            lines = json.decodeFromString<List<LyricLineDto>>(row.lines_json).map { it.toLyricLine() },
            displayArtist = row.display_artist,
            displayTitle = row.display_title,
            language = row.language,
            offsetMillis = row.offset_millis.toInt(),
        )
    }

    private fun upsertLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ) {
        val linesJson = json.encodeToString(lyrics.lines.map { LyricLineDto.fromLyricLine(it) })
        val now = nowMillis()
        queries.upsertCachedLyrics(
            source_id = sourceId,
            remote_track_id = trackId.value,
            lyric_source = lyrics.source.name,
            synced = if (lyrics.synced) 1L else 0L,
            lines_json = linesJson,
            display_artist = lyrics.displayArtist,
            display_title = lyrics.displayTitle,
            language = lyrics.language,
            offset_millis = lyrics.offsetMillis.toLong(),
            size_bytes = linesJson.toByteArray(Charsets.UTF_8).size.toLong(),
            created_at_epoch_millis = now,
            last_accessed_epoch_millis = now,
        )
    }

    private fun upsertLrclibLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ) {
        val linesJson = json.encodeToString(lyrics.lines.map { LyricLineDto.fromLyricLine(it) })
        val now = nowMillis()
        queries.upsertCachedLrclibLyrics(
            source_id = sourceId,
            remote_track_id = trackId.value,
            synced = if (lyrics.synced) 1L else 0L,
            lines_json = linesJson,
            display_artist = lyrics.displayArtist,
            display_title = lyrics.displayTitle,
            language = lyrics.language,
            offset_millis = lyrics.offsetMillis.toLong(),
            size_bytes = linesJson.toByteArray(Charsets.UTF_8).size.toLong(),
            created_at_epoch_millis = now,
            last_accessed_epoch_millis = now,
        )
    }

    suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): CachedAudioFile =
        withContext(Dispatchers.IO) {
            cachedAudioFile(sourceId, track.id, quality)?.let { return@withContext it }

            Files.createDirectories(audioCacheDirectory)
            val qualityKey = quality.cacheKey()
            val target = audioCacheDirectory.resolve(
                "${stableAudioFileName(sourceId, track.id.value, qualityKey)}${track.audioInfo?.contentType.audioExtension()}",
            )
            val temp = audioCacheDirectory.resolve("${target.fileName}.tmp")
            val streamUrl = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
            URI.create(streamUrl).toURL().openStream().use { input ->
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
            }
            moveDownloadedAudio(temp, target)

            val now = nowMillis()
            val size = Files.size(target)
            queries.upsertCachedAudio(
                source_id = sourceId,
                remote_track_id = track.id.value,
                quality_key = qualityKey,
                file_path = target.toAbsolutePath().toString(),
                size_bytes = size,
                content_type = track.audioInfo?.contentType,
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            trimAudioStore()
            CachedAudioFile(
                path = target,
                sizeBytes = size,
                contentType = track.audioInfo?.contentType,
            )
        }

    suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        withContext(Dispatchers.IO) {
            downloadedAudioFile(sourceId, track.id, quality)?.let { return@withContext it }

            Files.createDirectories(downloadDirectory)
            val qualityKey = quality.cacheKey()
            val target = downloadDirectory.resolve(
                "${stableAudioFileName(sourceId, track.id.value, qualityKey)}${track.audioInfo?.contentType.audioExtension()}",
            )
            val temp = downloadDirectory.resolve("${target.fileName}.tmp")
            val streamUrl = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
            try {
                URI.create(streamUrl).toURL().openStream().use { input ->
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
                }
                val size = Files.size(temp)
                val currentDownloadBytes = queries.downloadedAudioSize().executeAsOne()
                if (currentDownloadBytes + size > maxDownloadBytes.coerceAtLeast(0)) {
                    Files.deleteIfExists(temp)
                    throw IllegalStateException("Download storage limit exceeded.")
                }
                moveDownloadedAudio(temp, target)

                val now = nowMillis()
                queries.upsertDownloadedAudio(
                    source_id = sourceId,
                    remote_track_id = track.id.value,
                    quality_key = qualityKey,
                    file_path = target.toAbsolutePath().toString(),
                    size_bytes = size,
                    content_type = track.audioInfo?.contentType,
                    title = track.title,
                    artist_id = track.artistId?.value,
                    artist_name = track.artistName,
                    album_id = track.albumId?.value,
                    album_title = track.albumTitle,
                    album_release_year = track.albumReleaseYear?.toLong(),
                    duration_seconds = track.durationSeconds?.toLong(),
                    cover_art_id = track.coverArtId,
                    audio_codec = track.audioInfo?.codec,
                    audio_bitrate_kbps = track.audioInfo?.bitrateKbps?.toLong(),
                    audio_content_type = track.audioInfo?.contentType,
                    audio_bit_depth = track.audioInfo?.bitDepth?.toLong(),
                    audio_sampling_rate_hz = track.audioInfo?.samplingRateHz?.toLong(),
                    favorited_at_iso8601 = track.favoritedAtIso8601,
                    user_rating = track.userRating?.toLong(),
                    downloaded_at_epoch_millis = now,
                )
                DownloadedAudioFile(
                    path = target,
                    sizeBytes = size,
                    contentType = track.audioInfo?.contentType,
                )
            } catch (exception: Exception) {
                Files.deleteIfExists(temp)
                throw exception
            }
        }

    fun downloadedTracks(sourceId: String): List<DownloadedTrack> =
        queries.selectDownloadedAudio(sourceId).executeAsList().map { row ->
            DownloadedTrack(
                track = row.toTrack(),
                path = Path.of(row.file_path),
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
                downloadedAtEpochMillis = row.downloaded_at_epoch_millis,
            )
        }

    fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ) {
        val qualityKey = quality.cacheKey()
        queries.selectDownloadedAudioFile(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
        ).executeAsOneOrNull()?.let { row ->
            Files.deleteIfExists(Path.of(row.file_path))
        }
        queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
    }

    fun upsertNavidromeSource(
        connection: NavidromeConnection,
        provider: MediaProvider,
    ): SavedMediaSource {
        val now = nowMillis()
        val existing = queries.selectMediaSourceByCacheNamespace(provider.cacheNamespace).executeAsOneOrNull()
        val id = existing?.id ?: stableSourceId(provider.cacheNamespace)
        queries.upsertMediaSource(
            id = id,
            provider_id = provider.id.value,
            cache_namespace = provider.cacheNamespace,
            display_name = connection.resolvedDisplayName(),
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            created_at_epoch_millis = existing?.created_at_epoch_millis ?: now,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
        )
        return SavedMediaSource(
            id = id,
            providerId = provider.id.value,
            cacheNamespace = provider.cacheNamespace,
            displayName = connection.resolvedDisplayName(),
            baseUrl = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification,
            customCertificatePath = connection.tlsSettings.customCertificatePath,
            clientCertificateKeyStorePath = connection.tlsSettings.clientCertificateKeyStorePath,
            clientCertificateKeyStorePassword = connection.tlsSettings.clientCertificateKeyStorePassword,
            createdAtEpochMillis = existing?.created_at_epoch_millis ?: now,
            lastConnectedAtEpochMillis = now,
            lastSyncStartedAtEpochMillis = existing?.last_sync_started_at_epoch_millis,
            lastSyncCompletedAtEpochMillis = existing?.last_sync_completed_at_epoch_millis,
        )
    }

    fun markLibrarySyncStarted(sourceId: String) {
        queries.markMediaSourceSyncStarted(nowMillis(), sourceId)
    }

    fun markLibrarySyncCompleted(sourceId: String) {
        queries.markMediaSourceSyncCompleted(nowMillis(), sourceId)
    }

    fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
        val now = nowMillis()
        queries.transaction {
            artists.forEach { artist ->
                queries.upsertLibraryArtist(
                    source_id = sourceId,
                    remote_artist_id = artist.id.value,
                    name = artist.name,
                    search_name = artist.name.searchText(),
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
        val now = nowMillis()
        queries.transaction {
            albums.forEach { album ->
                queries.upsertLibraryAlbum(
                    source_id = sourceId,
                    remote_album_id = album.id.value,
                    remote_artist_id = null,
                    title = album.title,
                    artist_name = album.artistName,
                    search_title = album.title.searchText(),
                    search_artist_name = album.artistName.searchText(),
                    cover_art_id = album.coverArtId,
                    release_year = album.releaseYear?.toLong(),
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
        val now = nowMillis()
        queries.transaction {
            tracks.forEach { track ->
                queries.upsertLibraryTrack(
                    source_id = sourceId,
                    remote_track_id = track.id.value,
                    remote_album_id = track.albumId?.value,
                    remote_artist_id = track.artistId?.value,
                    title = track.title,
                    artist_name = track.artistName,
                    album_title = track.albumTitle,
                    search_title = track.title.searchText(),
                    search_artist_name = track.artistName.searchText(),
                    search_album_title = track.albumTitle?.searchText(),
                    duration_seconds = track.durationSeconds?.toLong(),
                    cover_art_id = track.coverArtId,
                    audio_codec = track.audioInfo?.codec,
                    audio_bitrate_kbps = track.audioInfo?.bitrateKbps?.toLong(),
                    audio_content_type = track.audioInfo?.contentType,
                    audio_bit_depth = track.audioInfo?.bitDepth?.toLong(),
                    audio_sampling_rate_hz = track.audioInfo?.samplingRateHz?.toLong(),
                    favorited_at_iso8601 = track.favoritedAtIso8601,
                    user_rating = track.userRating?.toLong(),
                    updated_at_epoch_millis = now,
                )
            }
        }
    }

    fun librarySnapshot(sourceId: String, limit: Long = 50, offset: Long = 0): LibrarySnapshot =
        LibrarySnapshot(
            artists = queries.selectLibraryArtists(sourceId, limit, offset).executeAsList().map {
                Artist(
                    id = ArtistId(it.remote_artist_id),
                    name = it.name,
                )
            },
            albums = queries.selectLibraryAlbums(sourceId, limit, offset).executeAsList().map {
                Album(
                    id = AlbumId(it.remote_album_id),
                    title = it.title,
                    artistName = it.artist_name,
                    coverArtId = it.cover_art_id,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = it.release_year?.toInt(),
                )
            },
            tracks = queries.selectLibraryTracks(sourceId, limit, offset).executeAsList().map {
                it.toTrack()
            },
        )

    fun searchLibrary(sourceId: String, query: String, limit: Long = 50, offset: Long = 0): LibrarySnapshot {
        val pattern = "%${query.searchText()}%"
        return LibrarySnapshot(
            artists = queries.searchLibraryArtists(sourceId, pattern, limit, offset).executeAsList().map {
                Artist(
                    id = ArtistId(it.remote_artist_id),
                    name = it.name,
                )
            },
            albums = queries.searchLibraryAlbums(sourceId, pattern, pattern, limit, offset).executeAsList().map {
                Album(
                    id = AlbumId(it.remote_album_id),
                    title = it.title,
                    artistName = it.artist_name,
                    coverArtId = it.cover_art_id,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = it.release_year?.toInt(),
                )
            },
            tracks = queries.searchLibraryTracks(sourceId, pattern, pattern, pattern, limit, offset).executeAsList().map {
                it.toTrack()
            },
        )
    }

    fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
        queries.selectRandomLibraryTrackForAlbum(sourceId, albumId.value)
            .executeAsOneOrNull()
            ?.toTrack()

    fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long = 50): List<Track> =
        queries.selectLibraryTracksForAlbum(sourceId, albumId.value, limit)
            .executeAsList()
            .map { it.toTrack() }

    fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        queries.selectRandomLibraryTrackForArtist(sourceId, artistId.value)
            .executeAsOneOrNull()
            ?.toTrack()

    fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long = 50): List<Track> =
        queries.selectLibraryTracksForArtist(sourceId, artistId.value, limit)
            .executeAsList()
            .map { it.toTrack() }

    fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long = 40): List<Track> {
        val albumTracks = track.albumId
            ?.let { libraryTracksForAlbum(sourceId, it, limit) }
            .orEmpty()
        val artistLimit = (limit - albumTracks.size).coerceAtLeast(12)
        val artistTracks = track.artistId
            ?.let { libraryTracksForArtist(sourceId, it, artistLimit) }
            .orEmpty()
        return (albumTracks + artistTracks)
            .asSequence()
            .filterNot { it.id == track.id }
            .distinctBy { it.id }
            .take(limit.toInt())
            .toList()
    }

    fun libraryIndexStats(sourceId: String): LibraryIndexStats =
        LibraryIndexStats(
            artistCount = queries.libraryArtistCountForSource(sourceId).executeAsOne(),
            albumCount = queries.libraryAlbumCountForSource(sourceId).executeAsOne(),
            trackCount = queries.libraryTrackCountForSource(sourceId).executeAsOne(),
        )

    fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
        queries.selectLibraryAlbumYears(sourceId)
            .executeAsList()
            .mapNotNull { row ->
                LibraryAlbumYear(
                    year = row.release_year.toInt(),
                    albumCount = row.album_count,
                )
            }

    fun libraryOffsetForLetter(sourceId: String, tab: LibraryTab, letter: Char): Long {
        val boundary = letter.librarySearchBoundary()
        return when (tab) {
            LibraryTab.Artists -> queries.libraryArtistOffsetForLetter(sourceId, boundary).executeAsOne()
            LibraryTab.Albums -> queries.libraryAlbumOffsetForLetter(sourceId, boundary).executeAsOne()
        }
    }

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

    fun clearCacheData() {
        synchronized(hotImages) {
            hotImages.clear()
            hotImageBytes = 0
        }
        queries.transaction {
            queries.clearResponses()
            queries.clearImages()
            queries.clearAudioWaveforms()
            queries.clearAudio()
            queries.clearLyrics()
            queries.clearLrclibLyrics()
        }
        clearAudioFiles()
    }

    fun clearDownloadData() {
        queries.clearDownloads()
        clearDownloadFiles()
    }

    fun clearLibraryData(sourceId: String? = null) {
        queries.transaction {
            if (sourceId == null) {
                queries.clearLibraryTracks()
                queries.clearLibraryAlbums()
                queries.clearLibraryArtists()
            } else {
                queries.clearLibraryForSource(sourceId)
                queries.clearLibraryAlbumsForSource(sourceId)
                queries.clearLibraryArtistsForSource(sourceId)
            }
        }
    }

    fun clearAll() {
        clearCacheData()
        clearDownloadData()
        clearLibraryData()
        queries.clearMediaSources()
    }

    fun stats(): CacheStats =
        CacheStats(
            databasePath = databasePath.toAbsolutePath().toString(),
            databaseBytes = databasePath.sizeOrZero(),
            imageCount = queries.imageCacheCount().executeAsOne(),
            imageBytes = queries.imageCacheSize().executeAsOne(),
            responseCount = queries.responseCacheCount().executeAsOne(),
            audioCount = queries.audioCacheCount().executeAsOne(),
            audioBytes = queries.audioCacheSize().executeAsOne(),
            downloadCount = queries.downloadedAudioCount().executeAsOne(),
            downloadBytes = queries.downloadedAudioSize().executeAsOne(),
            audioWaveformCount = queries.audioWaveformCacheCount().executeAsOne(),
            audioWaveformBytes = queries.audioWaveformCacheSize().executeAsOne(),
            lyricsCount = queries.lyricsCacheCount().executeAsOne() + queries.lrclibLyricsCacheCount().executeAsOne(),
            lyricsBytes = queries.lyricsCacheSize().executeAsOne() + queries.lrclibLyricsCacheSize().executeAsOne(),
            mediaSourceCount = queries.mediaSourceCount().executeAsOne(),
            libraryArtistCount = queries.libraryArtistCount().executeAsOne(),
            libraryAlbumCount = queries.libraryAlbumCount().executeAsOne(),
            libraryTrackCount = queries.libraryTrackCount().executeAsOne(),
            hotImageCount = synchronized(hotImages) { hotImages.size },
            hotImageBytes = synchronized(hotImages) { hotImageBytes },
            maxImageBytes = maxImageCacheBytes,
            maxAudioBytes = maxAudioCacheBytes,
            maxAudioWaveformBytes = maxAudioWaveformCacheBytes,
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

    private fun trimAudioStore() {
        var cacheSize = queries.audioCacheSize().executeAsOne()
        if (cacheSize <= maxAudioCacheBytes) return

        queries.oldestCachedAudio(100).executeAsList().forEach { audio ->
            if (cacheSize <= maxAudioCacheBytes) return
            queries.deleteCachedAudio(audio.source_id, audio.remote_track_id, audio.quality_key)
            Files.deleteIfExists(Path.of(audio.file_path))
            cacheSize -= audio.size_bytes
        }
    }

    private fun trimAudioWaveformStore() {
        var cacheSize = queries.audioWaveformCacheSize().executeAsOne()
        if (cacheSize <= maxAudioWaveformCacheBytes) return

        queries.oldestCachedAudioWaveforms(100).executeAsList().forEach { waveform ->
            if (cacheSize <= maxAudioWaveformCacheBytes) return
            queries.deleteCachedAudioWaveform(
                waveform.source_id,
                waveform.remote_track_id,
                waveform.quality_key,
            )
            cacheSize -= waveform.size_bytes
        }
    }

    private fun clearAudioFiles() {
        runCatching {
            if (!audioCacheDirectory.exists()) return@runCatching
            Files.walk(audioCacheDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    if (path != audioCacheDirectory) {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }
    }

    private fun clearDownloadFiles() {
        runCatching {
            if (!downloadDirectory.exists()) return@runCatching
            Files.walk(downloadDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    if (path != downloadDirectory) {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }
    }
}

object DesktopCaches {
    val session = DesktopCache()
}

data class CacheStats(
    val databasePath: String,
    val databaseBytes: Long,
    val imageCount: Long,
    val imageBytes: Long,
    val responseCount: Long,
    val audioCount: Long,
    val audioBytes: Long,
    val downloadCount: Long,
    val downloadBytes: Long,
    val audioWaveformCount: Long,
    val audioWaveformBytes: Long,
    val lyricsCount: Long,
    val lyricsBytes: Long,
    val mediaSourceCount: Long,
    val libraryArtistCount: Long,
    val libraryAlbumCount: Long,
    val libraryTrackCount: Long,
    val hotImageCount: Int,
    val hotImageBytes: Long,
    val maxImageBytes: Long,
    val maxAudioBytes: Long,
    val maxAudioWaveformBytes: Long,
    val maxHotImageBytes: Long,
)

data class CachedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class CachedAudioMetadata(
    val path: Path,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class DownloadedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class DownloadedTrack(
    val track: Track,
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
    val downloadedAtEpochMillis: Long,
)

data class AudioWaveform(
    val amplitudes: List<Float>,
)

data class SavedMediaSource(
    val id: String,
    val providerId: String,
    val cacheNamespace: String,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
    val clientCertificateKeyStorePassword: String? = null,
    val createdAtEpochMillis: Long,
    val lastConnectedAtEpochMillis: Long?,
    val lastSyncStartedAtEpochMillis: Long?,
    val lastSyncCompletedAtEpochMillis: Long?,
) {
    fun toNavidromeConnection(): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = username,
            token = token,
            salt = salt,
            displayName = displayName,
            tlsSettings = NavidromeTlsSettings(
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
            ),
        )
}

private fun app.naviamp.desktop.cache.Media_source.toSavedMediaSource(): SavedMediaSource =
    SavedMediaSource(
        id = id,
        providerId = provider_id,
        cacheNamespace = cache_namespace,
        displayName = display_name.takeUnless { it == "Navidrome" } ?: base_url,
        baseUrl = base_url,
        username = username,
        token = token,
        salt = salt,
        insecureSkipTlsVerification = insecure_skip_tls_verification != 0L,
        customCertificatePath = custom_certificate_path,
        clientCertificateKeyStorePath = client_certificate_keystore_path,
        clientCertificateKeyStorePassword = client_certificate_keystore_password,
        createdAtEpochMillis = created_at_epoch_millis,
        lastConnectedAtEpochMillis = last_connected_at_epoch_millis,
        lastSyncStartedAtEpochMillis = last_sync_started_at_epoch_millis,
        lastSyncCompletedAtEpochMillis = last_sync_completed_at_epoch_millis,
    )

private fun NavidromeConnection.resolvedDisplayName(): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedBaseUrl

data class LibrarySnapshot(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class LibraryIndexStats(
    val artistCount: Long,
    val albumCount: Long,
    val trackCount: Long,
) {
    val hasUsableIndex: Boolean
        get() = artistCount > 0L || albumCount > 0L || trackCount > 0L
}

data class LibraryAlbumYear(
    val year: Int,
    val albumCount: Long,
)

private fun app.naviamp.desktop.cache.Library_track.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = remote_artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = remote_album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        albumReleaseYear = null,
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = AudioInfo(
            codec = audio_codec,
            bitrateKbps = audio_bitrate_kbps?.toInt(),
            contentType = audio_content_type,
            bitDepth = audio_bit_depth?.toInt(),
            samplingRateHz = audio_sampling_rate_hz?.toInt(),
        ).takeIf {
            it.codec != null ||
                it.bitrateKbps != null ||
                it.contentType != null ||
                it.bitDepth != null ||
                it.samplingRateHz != null
        },
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun app.naviamp.desktop.cache.Downloaded_audio.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        albumReleaseYear = album_release_year?.toInt(),
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = AudioInfo(
            codec = audio_codec,
            bitrateKbps = audio_bitrate_kbps?.toInt(),
            contentType = audio_content_type ?: content_type,
            bitDepth = audio_bit_depth?.toInt(),
            samplingRateHz = audio_sampling_rate_hz?.toInt(),
        ).takeIf {
            it.codec != null ||
                it.bitrateKbps != null ||
                it.contentType != null ||
                it.bitDepth != null ||
                it.samplingRateHz != null
        },
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun createDatabase(path: Path): NaviampCacheDatabase {
    Files.createDirectories(path.parent)
    val exists = path.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
    if (!exists) {
        NaviampCacheDatabase.Schema.create(driver)
    }
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    ensureCurrentTables(driver)
    return NaviampCacheDatabase(driver)
}

private fun ensureCurrentTables(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS media_source (
          id TEXT NOT NULL PRIMARY KEY,
          provider_id TEXT NOT NULL,
          cache_namespace TEXT NOT NULL UNIQUE,
          display_name TEXT NOT NULL,
          base_url TEXT NOT NULL,
          username TEXT NOT NULL,
          token TEXT NOT NULL,
          salt TEXT NOT NULL,
          insecure_skip_tls_verification INTEGER NOT NULL DEFAULT 0,
          custom_certificate_path TEXT,
          client_certificate_keystore_path TEXT,
          client_certificate_keystore_password TEXT,
          created_at_epoch_millis INTEGER NOT NULL,
          last_connected_at_epoch_millis INTEGER,
          last_sync_started_at_epoch_millis INTEGER,
          last_sync_completed_at_epoch_millis INTEGER
        )
        """.trimIndent(),
        0,
    )
    runCatching {
        driver.execute(
            null,
            "ALTER TABLE media_source ADD COLUMN insecure_skip_tls_verification INTEGER NOT NULL DEFAULT 0",
            0,
        )
    }
    runCatching {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN custom_certificate_path TEXT", 0)
    }
    runCatching {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN client_certificate_keystore_path TEXT", 0)
    }
    runCatching {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN client_certificate_keystore_password TEXT", 0)
    }
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_audio_waveform (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          quality_key TEXT NOT NULL,
          audio_file_path TEXT NOT NULL,
          bucket_count INTEGER NOT NULL,
          amplitudes_json TEXT NOT NULL,
          size_bytes INTEGER NOT NULL DEFAULT 0,
          created_at_epoch_millis INTEGER NOT NULL,
          last_accessed_epoch_millis INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(source_id, remote_track_id, quality_key)
        )
        """.trimIndent(),
        0,
    )
    runCatching {
        driver.execute(
            null,
            "ALTER TABLE cached_audio_waveform ADD COLUMN size_bytes INTEGER NOT NULL DEFAULT 0",
            0,
        )
    }
    runCatching {
        driver.execute(
            null,
            "ALTER TABLE cached_audio_waveform ADD COLUMN last_accessed_epoch_millis INTEGER NOT NULL DEFAULT 0",
            0,
        )
    }
    driver.execute(
        null,
        "UPDATE cached_audio_waveform SET size_bytes = LENGTH(amplitudes_json) WHERE size_bytes = 0",
        0,
    )
    driver.execute(
        null,
        """
        UPDATE cached_audio_waveform
        SET last_accessed_epoch_millis = created_at_epoch_millis
        WHERE last_accessed_epoch_millis = 0
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS library_artist (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_artist_id TEXT NOT NULL,
          name TEXT NOT NULL,
          search_name TEXT NOT NULL,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_artist_id)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS library_album (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_album_id TEXT NOT NULL,
          remote_artist_id TEXT,
          title TEXT NOT NULL,
          artist_name TEXT NOT NULL,
          search_title TEXT NOT NULL,
          search_artist_name TEXT NOT NULL,
          cover_art_id TEXT,
          release_year INTEGER,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_album_id)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS library_track (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          remote_album_id TEXT,
          remote_artist_id TEXT,
          title TEXT NOT NULL,
          artist_name TEXT NOT NULL,
          album_title TEXT,
          search_title TEXT NOT NULL,
          search_artist_name TEXT NOT NULL,
          search_album_title TEXT,
          duration_seconds INTEGER,
          cover_art_id TEXT,
          audio_codec TEXT,
          audio_bitrate_kbps INTEGER,
          audio_content_type TEXT,
          audio_bit_depth INTEGER,
          audio_sampling_rate_hz INTEGER,
          favorited_at_iso8601 TEXT,
          user_rating INTEGER,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id)
        )
        """.trimIndent(),
        0,
    )
    listOf(
        "ALTER TABLE library_track ADD COLUMN audio_codec TEXT",
        "ALTER TABLE library_track ADD COLUMN audio_bitrate_kbps INTEGER",
        "ALTER TABLE library_track ADD COLUMN audio_content_type TEXT",
        "ALTER TABLE library_track ADD COLUMN audio_bit_depth INTEGER",
        "ALTER TABLE library_track ADD COLUMN audio_sampling_rate_hz INTEGER",
    ).forEach { sql ->
        runCatching { driver.execute(null, sql, 0) }
    }
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_audio (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          quality_key TEXT NOT NULL,
          file_path TEXT NOT NULL,
          size_bytes INTEGER NOT NULL,
          content_type TEXT,
          created_at_epoch_millis INTEGER NOT NULL,
          last_accessed_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id, quality_key)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS downloaded_audio (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          quality_key TEXT NOT NULL,
          file_path TEXT NOT NULL,
          size_bytes INTEGER NOT NULL,
          content_type TEXT,
          title TEXT NOT NULL,
          artist_id TEXT,
          artist_name TEXT NOT NULL,
          album_id TEXT,
          album_title TEXT,
          album_release_year INTEGER,
          duration_seconds INTEGER,
          cover_art_id TEXT,
          audio_codec TEXT,
          audio_bitrate_kbps INTEGER,
          audio_content_type TEXT,
          audio_bit_depth INTEGER,
          audio_sampling_rate_hz INTEGER,
          favorited_at_iso8601 TEXT,
          user_rating INTEGER,
          downloaded_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id, quality_key)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_lyrics (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          lyric_source TEXT NOT NULL,
          synced INTEGER NOT NULL,
          lines_json TEXT NOT NULL,
          display_artist TEXT,
          display_title TEXT,
          language TEXT,
          offset_millis INTEGER NOT NULL,
          size_bytes INTEGER NOT NULL,
          created_at_epoch_millis INTEGER NOT NULL,
          last_accessed_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_lrclib_lyrics (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          synced INTEGER NOT NULL,
          lines_json TEXT NOT NULL,
          display_artist TEXT,
          display_title TEXT,
          language TEXT,
          offset_millis INTEGER NOT NULL,
          size_bytes INTEGER NOT NULL,
          created_at_epoch_millis INTEGER NOT NULL,
          last_accessed_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id)
        )
        """.trimIndent(),
        0,
    )
    listOf(
        "CREATE INDEX IF NOT EXISTS library_artist_source_name ON library_artist(source_id, search_name)",
        "CREATE INDEX IF NOT EXISTS library_album_source_title ON library_album(source_id, search_title)",
        "CREATE INDEX IF NOT EXISTS library_album_source_artist ON library_album(source_id, search_artist_name)",
        "CREATE INDEX IF NOT EXISTS library_track_source_title ON library_track(source_id, search_title)",
        "CREATE INDEX IF NOT EXISTS library_track_source_artist ON library_track(source_id, search_artist_name)",
        "CREATE INDEX IF NOT EXISTS cached_audio_source_access ON cached_audio(source_id, last_accessed_epoch_millis)",
        "CREATE INDEX IF NOT EXISTS downloaded_audio_source_downloaded_at ON downloaded_audio(source_id, downloaded_at_epoch_millis)",
        "CREATE INDEX IF NOT EXISTS cached_audio_waveform_access ON cached_audio_waveform(last_accessed_epoch_millis)",
        "CREATE INDEX IF NOT EXISTS cached_lyrics_access ON cached_lyrics(last_accessed_epoch_millis)",
        "CREATE INDEX IF NOT EXISTS cached_lrclib_lyrics_access ON cached_lrclib_lyrics(last_accessed_epoch_millis)",
    ).forEach { sql ->
        driver.execute(null, sql, 0)
    }
}

private fun defaultCacheDatabasePath(): Path =
    defaultAppDataDirectory().resolve("cache.db")

private fun defaultAudioCacheDirectory(): Path =
    defaultAppDataDirectory().resolve("audio-cache")

private fun defaultDownloadDirectory(): Path =
    defaultAppDataDirectory().resolve("downloads")

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

private fun String.searchText(): String =
    trim().lowercase()

private fun Char.librarySearchBoundary(): String =
    if (this == '#') "" else lowercaseChar().toString()

private fun stableSourceId(cacheNamespace: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(cacheNamespace.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "source_${digest.take(24)}"
}

private fun stableAudioFileName(sourceId: String, trackId: String, qualityKey: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$sourceId:$trackId:$qualityKey".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return digest.take(32)
}

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }

private fun String?.audioExtension(): String =
    when (this?.lowercase()?.substringBefore(";")?.trim()) {
        "audio/mpeg", "audio/mp3" -> ".mp3"
        "audio/aac", "audio/aacp" -> ".aac"
        "audio/flac", "audio/x-flac" -> ".flac"
        "audio/ogg", "application/ogg" -> ".ogg"
        "audio/opus" -> ".opus"
        "audio/mp4", "audio/m4a" -> ".m4a"
        "audio/wav", "audio/wave", "audio/x-wav" -> ".wav"
        else -> ".audio"
    }

private fun moveDownloadedAudio(temp: Path, target: Path) {
    runCatching {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.getOrElse {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun Path.sizeOrZero(): Long =
    runCatching {
        if (exists()) Files.size(this) else 0L
    }.getOrDefault(0L)

private fun String.toLyricsSource(): LyricsSource =
    runCatching { LyricsSource.valueOf(this) }.getOrDefault(LyricsSource.Provider)

@Serializable
private data class LyricLineDto(
    val startMillis: Long? = null,
    val text: String,
) {
    fun toLyricLine(): LyricLine =
        LyricLine(
            startMillis = startMillis,
            text = text,
        )

    companion object {
        fun fromLyricLine(line: LyricLine): LyricLineDto =
            LyricLineDto(
                startMillis = line.startMillis,
                text = line.text,
            )
    }
}

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
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
            bitDepth = bitDepth,
            samplingRateHz = samplingRateHz,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): AudioInfoDto =
            AudioInfoDto(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
                bitDepth = audioInfo.bitDepth,
                samplingRateHz = audioInfo.samplingRateHz,
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
