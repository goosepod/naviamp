package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.QueryResult
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
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformRepository
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ObjectByteStoreService
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import app.naviamp.domain.waveform.AudioWaveformCacheMetadata
import app.naviamp.domain.waveform.waveformCacheKey
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.storage.NaviampStorageDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class DesktopCache(
    private val databasePath: Path = defaultCacheDatabasePath(),
    private val maxImageCacheBytes: Long = 500L * 1024L * 1024L,
    private var maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    private val maxAudioWaveformCacheBytes: Long = 32L * 1024L * 1024L,
    private val maxHotImageBytes: Long = 32L * 1024L * 1024L,
    private val audioCacheDirectory: Path = defaultAudioCacheDirectory(),
    private val downloadDirectory: Path = defaultDownloadDirectory(),
) : ImageCacheRepository,
    ProviderResponseCacheRepository,
    AudioCacheRepository<CachedAudioFile, CachedAudioMetadata>,
    AudioWaveformRepository,
    AudioWaveformStorageRepository,
    LyricsSidecarRepository,
    SidecarStatusRepository,
    DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    DownloadReplacementRepository<DownloadedAudioFile>,
    MediaSourceRepository,
    ProviderMediaSourceRepository,
    LocalLibraryIndexRepository,
    CacheMaintenanceRepository<StorageCacheStats>,
    TrackMetadataRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val database = createDatabase(databasePath)
    private val queries = database.naviampStorageQueries
    private val providerResponseCache = ProviderResponseCacheService(
        store = DesktopProviderResponseStore(queries),
        nowMillis = ::nowMillis,
    )
    private val sidecarStatus = SidecarStatusService(
        store = DesktopSidecarStatusStore(queries),
        nowMillis = ::nowMillis,
    )
    private val mediaSources = DesktopMediaSourceStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val hotImages = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var hotImageBytes: Long = 0
    private val httpClient = KtorSharedHttpClient()
    private val imageByteStoreService = ObjectByteStoreService(
        store = DesktopObjectByteStore(
            queries = queries,
            nowMillis = ::nowMillis,
            afterWrite = ::trimImageStore,
        ),
        httpClient = httpClient,
    )
    private val audioCacheByteStoreService = AudioByteStoreService(
        store = DesktopAudioByteStore(audioCacheDirectory),
        httpClient = httpClient,
    )
    private val downloadAudioByteStoreService = AudioByteStoreService(
        store = DesktopAudioByteStore(downloadDirectory),
        httpClient = httpClient,
    )

    override suspend fun imageBytes(url: String): ByteArray {
        hotImage(url)?.let { return it }

        return withContext(Dispatchers.IO + NonCancellable) {
            val bytes = imageByteStoreService.remoteBytes(url)
            putHotImage(url, bytes)
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

    override fun latestMediaSource(): SavedMediaSource? =
        mediaSources.latestMediaSource()

    override fun mediaSources(): List<SavedMediaSource> =
        mediaSources.mediaSources()

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        mediaSources.mediaSource(sourceId)

    override fun deleteMediaSource(sourceId: String) {
        mediaSources.deleteMediaSource(sourceId)
    }

    override fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0)
        trimAudioStore()
    }

    override fun cachedAudioMetadata(
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

    override suspend fun cachedAudioFile(
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

    override suspend fun downloadedAudioFile(
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

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): DownloadedAudioFile? =
        withContext(Dispatchers.IO) {
            val row = queries.selectDownloadedAudioFileForTrack(
                source_id = sourceId,
                remote_track_id = trackId.value,
            ).executeAsOneOrNull() ?: return@withContext null

            val path = Path.of(row.file_path)
            if (!path.exists()) {
                queries.deleteDownloadedAudio(sourceId, trackId.value, row.quality_key)
                return@withContext null
            }
            DownloadedAudioFile(
                path = path,
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
            )
        }

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.waveformCacheKey()
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

    override suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        ensureAudioWaveform(sourceId, trackId, quality, DesktopAudioWaveformAnalyzer())

    suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        analyzer: DomainAudioWaveformAnalyzer = DesktopAudioWaveformAnalyzer(),
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            cachedAudioWaveform(sourceId, trackId, quality)?.let { return@withContext it }
            val audioPath = downloadedAudioFile(sourceId, trackId, quality)?.path
                ?: cachedAudioFile(sourceId, trackId, quality)?.path
                ?: return@withContext null
            val waveform = analyzer.analyze(
                AudioWaveformAnalysisSource(
                    cacheKey = trackId.value,
                    streamUrl = audioPath.toUri().toString(),
                ),
            ) ?: return@withContext null
            storeAudioWaveform(
                sourceId = sourceId,
                trackId = trackId,
                quality = quality,
                audioFilePath = audioPath.toAbsolutePath().toString(),
                waveform = waveform,
            )
        }

    override suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.waveformCacheKey()
            val amplitudesJson = json.encodeToString(waveform.amplitudes)
            val now = nowMillis()
            val metadata = AudioWaveformCacheMetadata(
                sourceId = sourceId,
                remoteTrackId = trackId.value,
                qualityKey = qualityKey,
                bucketCount = waveform.amplitudes.size,
                sizeBytes = amplitudesJson.toByteArray(Charsets.UTF_8).size.toLong(),
                createdAtEpochMillis = now,
                lastAccessedEpochMillis = now,
            )
            queries.upsertCachedAudioWaveform(
                source_id = metadata.sourceId,
                remote_track_id = metadata.remoteTrackId,
                quality_key = metadata.qualityKey,
                audio_file_path = audioFilePath.orEmpty(),
                bucket_count = metadata.bucketCount.toLong(),
                amplitudes_json = amplitudesJson,
                size_bytes = metadata.sizeBytes,
                created_at_epoch_millis = metadata.createdAtEpochMillis,
                last_accessed_epoch_millis = metadata.lastAccessedEpochMillis,
            )
            trimAudioWaveformStore()
            waveform
        }

    override suspend fun providerLyrics(
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

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics =
        withContext(Dispatchers.IO) {
            upsertLyrics(sourceId, trackId, lyrics)
            lyrics
        }

    override suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
    ): Lyrics? =
        lrclibLyrics(sourceId, track, LrclibLyricsClient())

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

    override fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
        errorMessage: String?,
    ) {
        sidecarStatus.recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, errorMessage)
    }

    fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
    ) {
        recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, null)
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

    override suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): CachedAudioFile =
        withContext(Dispatchers.IO) {
            cachedAudioFile(sourceId, track.id, quality)?.let { return@withContext it }

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
            val stored = audioCacheByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = track.audioInfo?.contentType,
                provider = provider,
                streamUrl = streamUrl,
                errorMessage = "Could not cache audio track.",
            )

            val now = nowMillis()
            queries.upsertCachedAudio(
                source_id = sourceId,
                remote_track_id = track.id.value,
                quality_key = qualityKey,
                file_path = stored.filePath,
                size_bytes = stored.sizeBytes,
                content_type = track.audioInfo?.contentType,
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            trimAudioStore()
            CachedAudioFile(
                path = Path.of(stored.filePath),
                sizeBytes = stored.sizeBytes,
                contentType = track.audioInfo?.contentType,
            )
        }

    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        withContext(Dispatchers.IO) {
            downloadedAudioFile(sourceId, track.id)?.let { return@withContext it }

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
            val stored = downloadAudioByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = track.audioInfo?.contentType,
                provider = provider,
                streamUrl = streamUrl,
                errorMessage = "Could not download audio track.",
            )
            val currentDownloadBytes = queries.downloadedAudioSize().executeAsOne()
            if (currentDownloadBytes + stored.sizeBytes > maxDownloadBytes.coerceAtLeast(0)) {
                downloadAudioByteStoreService.deleteAudio(stored.filePath)
                throw IllegalStateException("Download storage limit exceeded.")
            }
            val target = Path.of(stored.filePath)
            val now = nowMillis()
            queries.upsertDownloadedAudio(
                source_id = sourceId,
                remote_track_id = track.id.value,
                quality_key = qualityKey,
                file_path = stored.filePath,
                size_bytes = stored.sizeBytes,
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
                sizeBytes = stored.sizeBytes,
                contentType = track.audioInfo?.contentType,
            )
        }

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val existingRows = queries.selectDownloadedAudio(sourceId)
                .executeAsList()
                .filter { row -> row.remote_track_id == track.id.value }
            val streamUrl = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
            val stored = downloadAudioByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = track.audioInfo?.contentType,
                provider = provider,
                streamUrl = streamUrl,
                errorMessage = "Could not download audio track.",
            )
            val currentDownloadBytes = queries.downloadedAudioSize().executeAsOne()
            val replacedBytes = existingRows.sumOf { row -> row.size_bytes }
            if (currentDownloadBytes - replacedBytes + stored.sizeBytes > maxDownloadBytes.coerceAtLeast(0)) {
                downloadAudioByteStoreService.deleteAudio(stored.filePath)
                throw IllegalStateException("Download storage limit exceeded.")
            }
            existingRows.forEach { row ->
                if (row.file_path != stored.filePath) {
                    downloadAudioByteStoreService.deleteAudio(row.file_path)
                }
            }
            queries.deleteDownloadedAudioForTrack(sourceId, track.id.value)
            val target = Path.of(stored.filePath)
            val now = nowMillis()
            queries.upsertDownloadedAudio(
                source_id = sourceId,
                remote_track_id = track.id.value,
                quality_key = qualityKey,
                file_path = stored.filePath,
                size_bytes = stored.sizeBytes,
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
                sizeBytes = stored.sizeBytes,
                contentType = track.audioInfo?.contentType,
            )
        }

    override fun downloadedTracks(sourceId: String): List<DownloadedTrack> =
        queries.selectDownloadedAudio(sourceId).executeAsList().map { row ->
            DownloadedTrack(
                track = row.toTrack(),
                path = Path.of(row.file_path),
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
                downloadedAtEpochMillis = row.downloaded_at_epoch_millis,
            )
        }

    override fun removeDownloadedAudio(
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
            downloadAudioByteStoreService.deleteAudio(row.file_path)
        }
        queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
    }

    override fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ) {
        queries.selectDownloadedAudio(sourceId)
            .executeAsList()
            .filter { row -> row.remote_track_id == trackId.value }
            .forEach { row ->
                downloadAudioByteStoreService.deleteAudio(row.file_path)
            }
        queries.deleteDownloadedAudioForTrack(sourceId, trackId.value)
    }

    fun upsertNavidromeSource(
        connection: NavidromeConnection,
        provider: MediaProvider,
    ): SavedMediaSource {
        val identity = upsertProviderMediaSource(
            connection = ProviderMediaSourceConnection(
                displayName = connection.resolvedDisplayName(),
                baseUrl = connection.baseUrl,
                username = connection.username,
                token = connection.token,
                salt = connection.salt,
                nativeToken = connection.nativeToken,
                tlsSettings = connection.tlsSettings,
            ),
            cacheNamespace = provider.cacheNamespace,
            providerId = provider.id.value,
        )
        return mediaSource(identity.id)
            ?: throw IllegalStateException("Media source ${identity.id} was not persisted.")
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity =
        mediaSources.upsertProviderMediaSource(connection, cacheNamespace, providerId)

    override fun markLibrarySyncStarted(sourceId: String) {
        mediaSources.markLibrarySyncStarted(sourceId)
    }

    override fun markLibrarySyncCompleted(sourceId: String) {
        mediaSources.markLibrarySyncCompleted(sourceId)
    }

    override fun markLibraryScanChecked(sourceId: String, signature: String) {
        mediaSources.markLibraryScanChecked(sourceId, signature)
    }

    override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
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

    override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
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

    override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
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

    override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot =
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

    override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot {
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

    override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
        queries.selectRandomLibraryTrackForAlbum(sourceId, albumId.value)
            .executeAsOneOrNull()
            ?.toTrack()

    override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
        queries.selectLibraryTracksForAlbum(sourceId, albumId.value, limit)
            .executeAsList()
            .map { it.toTrack() }

    override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        queries.selectRandomLibraryTrackForArtist(sourceId, artistId.value)
            .executeAsOneOrNull()
            ?.toTrack()

    override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtist(sourceId, artistId.value, limit)
            .executeAsList()
            .map { it.toTrack() }

    override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtistName(sourceId, artistName.searchText(), limit)
            .executeAsList()
            .map { it.toTrack() }

    override fun artistPopularTracks(sourceId: String, artistId: ArtistId, source: String): List<ArtistPopularTrackMatch> =
        queries.selectArtistPopularTracks(sourceId, artistId.value, source)
            .executeAsList()
            .map { it.toPopularTrackMatch() }

    override fun replaceArtistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String,
        candidates: List<ArtistPopularTrackCandidate>,
        matchedTracksBySourceTrackId: Map<String, Track>,
        fetchedAtEpochMillis: Long,
    ) {
        queries.transaction {
            queries.deleteArtistPopularTracks(sourceId, artistId.value, source)
            candidates.forEach { candidate ->
                queries.upsertArtistPopularTrack(
                    source_id = sourceId,
                    remote_artist_id = artistId.value,
                    popular_source = source,
                    source_track_id = candidate.sourceTrackId,
                    rank = candidate.rank.toLong(),
                    title = candidate.title,
                    album_title = candidate.albumTitle,
                    duration_seconds = candidate.durationSeconds?.toLong(),
                    matched_remote_track_id = matchedTracksBySourceTrackId[candidate.sourceTrackId]?.id?.value,
                    fetched_at_epoch_millis = fetchedAtEpochMillis,
                )
            }
        }
    }

    override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> {
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

    override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
        LibraryIndexStats(
            artistCount = queries.libraryArtistCountForSource(sourceId).executeAsOne(),
            albumCount = queries.libraryAlbumCountForSource(sourceId).executeAsOne(),
            trackCount = queries.libraryTrackCountForSource(sourceId).executeAsOne(),
        )

    override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
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

    override fun updateTrack(updatedTrack: Track) {
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

    override fun clearProviderData() {
        queries.clearResponses()
    }

    override fun clearCacheData() {
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
            queries.clearSidecarStatuses()
        }
        clearAudioFiles()
    }

    override fun clearDownloadData() {
        queries.clearDownloads()
        clearDownloadFiles()
    }

    override fun clearLibraryData(sourceId: String?) {
        queries.transaction {
            if (sourceId == null) {
                queries.clearArtistPopularTracks()
                queries.clearLibraryTracks()
                queries.clearLibraryAlbums()
                queries.clearLibraryArtists()
            } else {
                queries.clearArtistPopularTracksForSource(sourceId)
                queries.clearLibraryForSource(sourceId)
                queries.clearLibraryAlbumsForSource(sourceId)
                queries.clearLibraryArtistsForSource(sourceId)
            }
        }
    }

    override fun clearAll() {
        clearCacheData()
        clearDownloadData()
        clearLibraryData(null)
        queries.clearMediaSources()
    }

    override fun stats(): StorageCacheStats =
        StorageCacheStats(
            databaseLabel = databasePath.toAbsolutePath().toString(),
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

    override suspend fun <T> cachedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T =
        providerResponseCache.cachedProviderResponse(provider, resourceType, resourceId, decode, encode, fetch)

    override fun invalidateProviderResponses(
        provider: MediaProvider,
        resourceType: String,
    ) {
        providerResponseCache.invalidateProviderResponses(provider, resourceType)
    }

    override fun invalidateProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
    ) {
        providerResponseCache.invalidateProviderResponse(provider, resourceType, resourceId)
    }

    private suspend fun <T> cached(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T =
        cachedProviderResponse(provider, resourceType, resourceId, decode, encode, fetch)

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
            audioCacheByteStoreService.deleteAudio(audio.file_path)
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

private fun app.naviamp.storage.Library_track.toTrack(): Track =
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

private fun app.naviamp.storage.SelectArtistPopularTracks.toPopularTrackMatch(): ArtistPopularTrackMatch =
    ArtistPopularTrackMatch(
        candidate = ArtistPopularTrackCandidate(
            source = popular_source,
            sourceTrackId = source_track_id,
            rank = rank.toInt(),
            title = popular_title,
            albumTitle = popular_album_title,
            durationSeconds = popular_duration_seconds?.toInt(),
        ),
        matchedTrack = Track(
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
        ),
        fetchedAtEpochMillis = fetched_at_epoch_millis,
    )

private fun app.naviamp.storage.Downloaded_audio.toTrack(): Track =
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

private fun createDatabase(path: Path): NaviampStorageDatabase {
    Files.createDirectories(path.parent)
    val exists = path.exists()
    registerSqliteDriver()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
    if (!exists) {
        NaviampStorageDatabase.Schema.create(driver)
    } else {
        val oldVersion = driver.databaseVersion()
        val newVersion = NaviampStorageDatabase.Schema.version
        if (oldVersion in 1 until newVersion) {
            NaviampStorageDatabase.Schema.migrate(driver, oldVersion, newVersion)
        }
    }
    ensureMediaSourceLibraryScanSchema(driver)
    ensureArtistPopularTracksSchema(driver)
    ensureCachedSidecarStatusSchema(driver)
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return NaviampStorageDatabase(driver)
}

private fun registerSqliteDriver() {
    Class.forName("org.sqlite.JDBC")
}

private fun JdbcSqliteDriver.databaseVersion(): Long =
    executeQuery(null, "PRAGMA user_version", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    }, 0).value

private fun ensureMediaSourceLibraryScanSchema(driver: JdbcSqliteDriver) {
    if (!driver.tableHasColumn("media_source", "native_token")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN native_token TEXT", 0)
    }
    if (!driver.tableHasColumn("media_source", "last_library_scan_signature")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN last_library_scan_signature TEXT", 0)
    }
    if (!driver.tableHasColumn("media_source", "last_library_scan_checked_at_epoch_millis")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN last_library_scan_checked_at_epoch_millis INTEGER", 0)
    }
}

private fun JdbcSqliteDriver.tableHasColumn(tableName: String, columnName: String): Boolean =
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        var found = false
        while (cursor.next().value) {
            if (cursor.getString(1) == columnName) {
                found = true
                break
            }
        }
        QueryResult.Value(found)
    }, 0).value

private fun ensureArtistPopularTracksSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS artist_popular_track (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_artist_id TEXT NOT NULL,
          popular_source TEXT NOT NULL,
          source_track_id TEXT NOT NULL,
          rank INTEGER NOT NULL,
          title TEXT NOT NULL,
          album_title TEXT,
          duration_seconds INTEGER,
          matched_remote_track_id TEXT,
          fetched_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_artist_id, popular_source, source_track_id)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS artist_popular_track_artist
        ON artist_popular_track(source_id, remote_artist_id, popular_source, rank)
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS artist_popular_track_match
        ON artist_popular_track(source_id, matched_remote_track_id)
        """.trimIndent(),
        0,
    )
}

private fun ensureCachedSidecarStatusSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_sidecar_status (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          quality_key TEXT NOT NULL,
          sidecar_type TEXT NOT NULL,
          status TEXT NOT NULL,
          attempts INTEGER NOT NULL,
          last_error TEXT,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id, quality_key, sidecar_type)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS cached_sidecar_status_track
        ON cached_sidecar_status(source_id, remote_track_id, quality_key)
        """.trimIndent(),
        0,
    )
}

private fun defaultCacheDatabasePath(): Path =
    defaultAppDataDirectory().resolve("storage.db")

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

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }

private fun moveDownloadedAudio(temp: Path, target: Path) {
    runCatching {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.getOrElse {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private class DesktopAudioByteStore(
    private val directory: Path,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        Files.createDirectories(directory)
        val target = directory.resolve(fileName)
        val temp = directory.resolve("${target.fileName}.tmp")
        return try {
            Files.newOutputStream(temp).use { output ->
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            }
            moveDownloadedAudio(temp, target)
            StoredAudioBytes(
                filePath = target.toAbsolutePath().toString(),
                sizeBytes = Files.size(target),
            )
        } catch (exception: Exception) {
            Files.deleteIfExists(temp)
            throw exception
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        Files.deleteIfExists(Path.of(filePath))
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
