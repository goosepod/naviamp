package app.naviamp.android

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.AudioWaveformCacheRepository
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
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.stableMediaSourceId
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.waveformCacheKey
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.storage.Downloaded_audio
import app.naviamp.storage.Library_track
import app.naviamp.storage.Media_source
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.Playback_history
import app.naviamp.storage.SelectArtistPopularTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class AndroidStorage(
    context: Context,
) : ImageCacheRepository,
    ProviderResponseCacheRepository,
    AudioCacheRepository<AndroidCachedAudioFile, AndroidCachedAudioMetadata>,
    AudioWaveformCacheRepository,
    AudioWaveformStorageRepository,
    LyricsSidecarRepository,
    DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    PlaybackHistoryRepository<AndroidPlaybackHistoryItem>,
    MediaSourceRepository,
    ProviderMediaSourceRepository,
    PlaybackSessionRepository,
    LocalLibraryIndexRepository,
    CacheMaintenanceRepository<StorageCacheStats>,
    SidecarStatusRepository,
    AutoCloseable {
    private val appContext = context.applicationContext
    private val driver = AndroidSqliteDriver(
        schema = NaviampStorageDatabase.Schema,
        context = appContext,
        name = DatabaseName,
    ).also {
        it.execute(null, "PRAGMA foreign_keys=ON", 0)
    }
    private val database = NaviampStorageDatabase(driver)
    private val queries = database.naviampStorageQueries
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val providerResponseCache = ProviderResponseCacheService(
        store = AndroidProviderResponseStore(queries),
        nowMillis = ::nowMillis,
    )
    private val sidecarStatus = SidecarStatusService(
        store = AndroidSidecarStatusStore(queries),
        nowMillis = ::nowMillis,
    )
    private val httpClient = KtorSharedHttpClient()
    private val imageByteStoreService = ObjectByteStoreService(
        store = AndroidObjectByteStore(
            queries = queries,
            nowMillis = ::nowMillis,
        ),
        httpClient = httpClient,
    )

    val audioCacheDirectory: File = File(appContext.cacheDir, "audio-cache")
    val downloadDirectory: File = File(appContext.filesDir, "downloads")
    private var maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L
    private val audioCacheByteStoreService = AudioByteStoreService(
        store = AndroidAudioByteStore(audioCacheDirectory),
        httpClient = httpClient,
    )
    private val downloadAudioByteStoreService = AudioByteStoreService(
        store = AndroidAudioByteStore(downloadDirectory),
        httpClient = httpClient,
    )

    override fun close() {
        driver.close()
    }

    override fun latestMediaSource(): SavedMediaSource? =
        queries.selectLatestMediaSource()
            .executeAsOneOrNull()
            ?.toSavedMediaSource()

    fun latestNavidromeSource(): SavedMediaSource? =
        latestMediaSource()

    override fun mediaSources(): List<SavedMediaSource> =
        queries.selectMediaSources()
            .executeAsList()
            .map { it.toSavedMediaSource() }

    fun latestNavidromeConnection(): NavidromeConnection? =
        latestNavidromeSource()?.toNavidromeConnection()

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        queries.selectMediaSourceById(sourceId)
            .executeAsOneOrNull()
            ?.toSavedMediaSource()

    override fun deleteMediaSource(sourceId: String) {
        queries.deleteMediaSource(sourceId)
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity {
        val now = System.currentTimeMillis()
        val existing = queries.selectMediaSourceByCacheNamespace(cacheNamespace).executeAsOneOrNull()
        val id = existing?.id ?: stableMediaSourceId(cacheNamespace)
        val displayName = connection.displayName
        queries.upsertMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            native_token = connection.nativeToken,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            created_at_epoch_millis = existing?.created_at_epoch_millis ?: now,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
            last_library_scan_signature = existing?.last_library_scan_signature,
            last_library_scan_checked_at_epoch_millis = existing?.last_library_scan_checked_at_epoch_millis,
        )
        queries.updateMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            native_token = connection.nativeToken,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
            last_library_scan_signature = existing?.last_library_scan_signature,
            last_library_scan_checked_at_epoch_millis = existing?.last_library_scan_checked_at_epoch_millis,
        )
        return MediaSourceIdentity(
            id = id,
            cacheNamespace = cacheNamespace,
            displayName = displayName,
        )
    }

    fun upsertNavidromeSource(connection: NavidromeConnection, cacheNamespace: String, providerId: String): MediaSourceIdentity =
        upsertProviderMediaSource(
            connection = ProviderMediaSourceConnection(
                displayName = connection.resolvedDisplayName(),
                baseUrl = connection.baseUrl,
                username = connection.username,
                token = connection.token,
                salt = connection.salt,
                nativeToken = connection.nativeToken,
                tlsSettings = connection.tlsSettings,
            ),
            cacheNamespace = cacheNamespace,
            providerId = providerId,
        )

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? =
        queries.selectPlaybackSession(requirePlaybackSessionSourceId(sourceId))
            .executeAsOneOrNull()
            ?.let { payload ->
                runCatching { json.decodeFromString<PlaybackSessionSettings>(payload) }.getOrNull()
            }

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        val requiredSourceId = requirePlaybackSessionSourceId(sourceId)
        if (session == null) {
            queries.deletePlaybackSession(requiredSourceId)
            return
        }
        queries.upsertPlaybackSession(
            source_id = requiredSourceId,
            payload = json.encodeToString(session),
            updated_at_epoch_millis = System.currentTimeMillis(),
        )
    }

    fun savePlaybackSession(sourceId: String, session: PlaybackSessionSettings?) {
        savePlaybackSession(session = session, sourceId = sourceId)
    }

    private fun requirePlaybackSessionSourceId(sourceId: String?): String =
        requireNotNull(sourceId?.takeIf { it.isNotBlank() }) {
            "Android playback sessions require a sourceId."
        }

    override suspend fun imageBytes(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            imageByteStoreService.remoteBytes(url)
        }

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

    override fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0)
        trimAudioStore()
    }

    override fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AndroidCachedAudioMetadata? {
        val qualityKey = quality.cacheKey()
        val row = queries.selectCachedAudioMetadata(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
        ).executeAsOneOrNull() ?: return null
        val file = File(row.file_path)
        return AndroidCachedAudioMetadata(
            file = file,
            exists = file.exists(),
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
    ): AndroidCachedAudioFile? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val row = queries.selectCachedAudio(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
            ).executeAsOneOrNull() ?: return@withContext null
            val file = File(row.file_path)
            if (!file.exists()) {
                queries.deleteCachedAudio(sourceId, trackId.value, qualityKey)
                return@withContext null
            }
            queries.touchCachedAudio(nowMillis(), sourceId, trackId.value, qualityKey)
            AndroidCachedAudioFile(file, row.size_bytes, row.content_type)
        }

    override suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): AndroidCachedAudioFile =
        withContext(Dispatchers.IO) {
            cachedAudioFile(sourceId, track.id, quality)?.let { return@withContext it }

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(StreamRequest(track.id, quality))
            val stored = audioCacheByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = track.audioInfo?.contentType,
                provider = provider,
                streamUrl = streamUrl,
                errorMessage = "Could not cache audio track.",
            )
            val target = File(stored.filePath)
            val now = nowMillis()
            queries.upsertCachedAudio(
                source_id = sourceId,
                remote_track_id = track.id.value,
                quality_key = qualityKey,
                file_path = target.absolutePath,
                size_bytes = stored.sizeBytes,
                content_type = track.audioInfo?.contentType,
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            trimAudioStore()
            AndroidCachedAudioFile(target, stored.sizeBytes, track.audioInfo?.contentType)
        }

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AndroidDownloadedAudioFile? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val row = queries.selectDownloadedAudioFile(
                source_id = sourceId,
                remote_track_id = trackId.value,
                quality_key = qualityKey,
            ).executeAsOneOrNull() ?: return@withContext null
            val file = File(row.file_path)
            if (!file.exists()) {
                queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
                return@withContext null
            }
            AndroidDownloadedAudioFile(file, row.size_bytes, row.content_type)
        }

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): AndroidDownloadedAudioFile? =
        withContext(Dispatchers.IO) {
            val row = queries.selectDownloadedAudioFileForTrack(
                source_id = sourceId,
                remote_track_id = trackId.value,
            ).executeAsOneOrNull() ?: return@withContext null
            val file = File(row.file_path)
            if (!file.exists()) {
                queries.deleteDownloadedAudio(sourceId, trackId.value, row.quality_key)
                return@withContext null
            }
            AndroidDownloadedAudioFile(file, row.size_bytes, row.content_type)
        }

    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): AndroidDownloadedAudioFile =
        withContext(Dispatchers.IO) {
            downloadedAudioFile(sourceId, track.id)?.let { return@withContext it }

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(StreamRequest(track.id, quality))
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
            val target = File(stored.filePath)
            upsertDownloadedAudio(sourceId, track, qualityKey, target, stored.sizeBytes, track.audioInfo?.contentType, nowMillis())
            AndroidDownloadedAudioFile(target, stored.sizeBytes, track.audioInfo?.contentType)
        }

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): AndroidDownloadedAudioFile =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.cacheKey()
            val existingRows = queries.selectDownloadedAudio(sourceId)
                .executeAsList()
                .filter { row -> row.remote_track_id == track.id.value }
            val streamUrl = provider.streamUrl(StreamRequest(track.id, quality))
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
            val target = File(stored.filePath)
            upsertDownloadedAudio(sourceId, track, qualityKey, target, stored.sizeBytes, track.audioInfo?.contentType, nowMillis())
            AndroidDownloadedAudioFile(target, stored.sizeBytes, track.audioInfo?.contentType)
        }

    override fun downloadedTracks(sourceId: String): List<AndroidDownloadedTrack> =
        queries.selectDownloadedAudio(sourceId).executeAsList().map { row ->
            AndroidDownloadedTrack(
                track = row.toTrack(),
                file = File(row.file_path),
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
                downloadedAtEpochMillis = row.downloaded_at_epoch_millis,
            )
        }

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) {
        val qualityKey = quality.cacheKey()
        queries.selectDownloadedAudioFile(sourceId, trackId.value, qualityKey).executeAsOneOrNull()?.let { row ->
            downloadAudioByteStoreService.deleteAudio(row.file_path)
        }
        queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
    }

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) {
        queries.selectDownloadedAudio(sourceId)
            .executeAsList()
            .filter { row -> row.remote_track_id == trackId.value }
            .forEach { row ->
                downloadAudioByteStoreService.deleteAudio(row.file_path)
            }
        queries.deleteDownloadedAudioForTrack(sourceId, trackId.value)
    }

    fun removeDownloadedAudioForTrack(sourceId: String, trackId: TrackId) {
        removeDownloadedAudio(sourceId, trackId)
    }

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val qualityKey = quality.waveformCacheKey()
            val row = queries.selectCachedAudioWaveform(sourceId, trackId.value, qualityKey).executeAsOneOrNull()
                ?: return@withContext null
            queries.touchCachedAudioWaveform(nowMillis(), sourceId, trackId.value, qualityKey)
            AudioWaveform(json.decodeFromString<List<Float>>(row.amplitudes_json))
        }

    fun upsertAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFile: File,
        waveform: AudioWaveform,
    ) {
        storeAudioWaveformRow(
            sourceId = sourceId,
            trackId = trackId,
            quality = quality,
            audioFilePath = audioFile.absolutePath,
            waveform = waveform,
        )
    }

    override suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform = withContext(Dispatchers.IO) {
        storeAudioWaveformRow(sourceId, trackId, quality, audioFilePath, waveform)
    }

    private fun storeAudioWaveformRow(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform {
        val qualityKey = quality.waveformCacheKey()
        val amplitudesJson = json.encodeToString(waveform.amplitudes)
        val now = nowMillis()
        queries.upsertCachedAudioWaveform(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
            audio_file_path = audioFilePath.orEmpty(),
            bucket_count = waveform.amplitudes.size.toLong(),
            amplitudes_json = amplitudesJson,
            size_bytes = amplitudesJson.toByteArray(Charsets.UTF_8).size.toLong(),
            created_at_epoch_millis = now,
            last_accessed_epoch_millis = now,
        )
        trimAudioWaveformStore()
        return waveform
    }

    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics? = withContext(Dispatchers.IO) {
        cachedLyrics(sourceId, trackId)?.let { return@withContext it }
        val lyrics = provider.lyrics(trackId) ?: return@withContext null
        upsertLyrics(sourceId, trackId, lyrics)
        lyrics
    }

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics = withContext(Dispatchers.IO) {
        upsertLyrics(sourceId, trackId, lyrics)
        lyrics
    }

    override suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
    ): Lyrics? = withContext(Dispatchers.IO) {
        cachedLrclibLyrics(sourceId, track.id)?.let { return@withContext it }
        val lyrics = AndroidLrclibLyricsClient().lyrics(track) ?: return@withContext null
        upsertLrclibLyrics(sourceId, track.id, lyrics)
        lyrics
    }

    private fun cachedLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? {
        val row = queries.selectCachedLyrics(
            source_id = sourceId,
            remote_track_id = trackId.value,
        ).executeAsOneOrNull() ?: return null

        queries.touchCachedLyrics(nowMillis(), sourceId, trackId.value)
        return Lyrics(
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

    override fun playbackHistory(sourceId: String, limit: Int): List<AndroidPlaybackHistoryItem> =
        queries.selectPlaybackHistory(sourceId, limit.toLong()).executeAsList().map { row ->
            AndroidPlaybackHistoryItem(row.toTrack(), row.played_at_epoch_millis)
        }

    override fun recordPlaybackHistory(sourceId: String, track: Track, playedAtEpochMillis: Long) {
        queries.upsertPlaybackHistory(
            source_id = sourceId,
            remote_track_id = track.id.value,
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
            played_at_epoch_millis = playedAtEpochMillis,
        )
    }

    override fun markLibrarySyncStarted(sourceId: String) {
        queries.markMediaSourceSyncStarted(nowMillis(), sourceId)
    }

    override fun markLibrarySyncCompleted(sourceId: String) {
        queries.markMediaSourceSyncCompleted(nowMillis(), sourceId)
    }

    override fun markLibraryScanChecked(sourceId: String, signature: String) {
        queries.markMediaSourceLibraryScanChecked(signature, nowMillis(), sourceId)
    }

    override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
        val now = nowMillis()
        queries.transaction {
            artists.forEach { artist ->
                queries.upsertLibraryArtist(sourceId, artist.id.value, artist.name, artist.name.searchText(), now)
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
                Artist(ArtistId(it.remote_artist_id), it.name)
            },
            albums = queries.selectLibraryAlbums(sourceId, limit, offset).executeAsList().map {
                Album(AlbumId(it.remote_album_id), it.title, it.artist_name, it.cover_art_id, null, it.release_year?.toInt())
            },
            tracks = queries.selectLibraryTracks(sourceId, limit, offset).executeAsList().map { it.toTrack() },
        )

    override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot {
        val pattern = "%${query.searchText()}%"
        return LibrarySnapshot(
            artists = queries.searchLibraryArtists(sourceId, pattern, limit, offset).executeAsList().map {
                Artist(ArtistId(it.remote_artist_id), it.name)
            },
            albums = queries.searchLibraryAlbums(sourceId, pattern, pattern, limit, offset).executeAsList().map {
                Album(AlbumId(it.remote_album_id), it.title, it.artist_name, it.cover_art_id, null, it.release_year?.toInt())
            },
            tracks = queries.searchLibraryTracks(sourceId, pattern, pattern, pattern, limit, offset).executeAsList()
                .map { it.toTrack() },
        )
    }

    override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
        queries.selectRandomLibraryTrackForAlbum(sourceId, albumId.value).executeAsOneOrNull()?.toTrack()

    override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
        queries.selectLibraryTracksForAlbum(sourceId, albumId.value, limit).executeAsList().map { it.toTrack() }

    fun libraryTrack(sourceId: String, trackId: TrackId): Track? =
        queries.selectLibraryTrackById(sourceId, trackId.value).executeAsOneOrNull()?.toTrack()

    override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        queries.selectRandomLibraryTrackForArtist(sourceId, artistId.value).executeAsOneOrNull()?.toTrack()

    override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtist(sourceId, artistId.value, limit).executeAsList().map { it.toTrack() }

    override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
        queries.selectLibraryTracksForArtistName(sourceId, artistName.searchText(), limit).executeAsList().map { it.toTrack() }

    override fun libraryTracksForAlbumTitle(
        sourceId: String,
        albumTitle: String,
        artistName: String?,
        limit: Long,
    ): List<Track> {
        val searchArtistName = artistName?.searchText()
        val searchAlbumTitle = albumTitle.searchText()
        return if (searchArtistName.isNullOrBlank()) {
            queries.selectLibraryTracksForAlbumTitle(sourceId, searchAlbumTitle, limit)
        } else {
            queries.selectLibraryTracksForAlbumTitleAndArtist(sourceId, searchAlbumTitle, searchArtistName, limit)
        }.executeAsList().map { it.toTrack() }
    }

    override fun artistPopularTracks(sourceId: String, artistId: ArtistId, source: String): List<ArtistPopularTrackMatch> =
        queries.selectArtistPopularTracks(sourceId, artistId.value, source).executeAsList().map { it.toPopularTrackMatch() }

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
        val albumTracks = track.albumId?.let { libraryTracksForAlbum(sourceId, it, limit) }.orEmpty()
        val artistLimit = (limit - albumTracks.size).coerceAtLeast(12)
        val artistTracks = track.artistId?.let { libraryTracksForArtist(sourceId, it, artistLimit) }.orEmpty()
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
        queries.selectLibraryAlbumYears(sourceId).executeAsList().map { row ->
            LibraryAlbumYear(row.release_year.toInt(), row.album_count)
        }

    override fun clearProviderData() {
        queries.clearResponses()
    }

    override fun clearCacheData() {
        queries.transaction {
            queries.clearResponses()
            queries.clearImages()
            queries.clearAudioWaveforms()
            queries.clearAudio()
            queries.clearLyrics()
            queries.clearLrclibLyrics()
            queries.clearSidecarStatuses()
        }
        clearFiles(audioCacheDirectory)
    }

    override fun clearDownloadData() {
        queries.clearDownloads()
        clearFiles(downloadDirectory)
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
        queries.clearPlaybackHistory()
        queries.clearMediaSources()
    }

    override fun stats(): StorageCacheStats =
        StorageCacheStats(
            databaseLabel = DatabaseName,
            mediaSourceCount = queries.mediaSourceCount().executeAsOne(),
            playbackSessionCount = queries.playbackSessionCount().executeAsOne(),
            imageCount = queries.imageCacheCount().executeAsOne(),
            imageBytes = queries.imageCacheSize().executeAsOne(),
            responseCount = queries.responseCacheCount().executeAsOne(),
            audioCount = queries.audioCacheCount().executeAsOne(),
            audioBytes = queries.audioCacheSize().executeAsOne(),
            downloadCount = queries.downloadedAudioCount().executeAsOne(),
            downloadBytes = queries.downloadedAudioSize().executeAsOne(),
            audioWaveformCount = queries.audioWaveformCacheCount().executeAsOne(),
            audioWaveformBytes = queries.audioWaveformCacheSize().executeAsOne(),
            lyricsBytes = queries.lyricsCacheSize().executeAsOne() + queries.lrclibLyricsCacheSize().executeAsOne(),
            libraryArtistCount = queries.libraryArtistCount().executeAsOne(),
            libraryAlbumCount = queries.libraryAlbumCount().executeAsOne(),
            libraryTrackCount = queries.libraryTrackCount().executeAsOne(),
            audioCacheDirectory = audioCacheDirectory.absolutePath,
            downloadDirectory = downloadDirectory.absolutePath,
        )

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
        val maxAudioWaveformCacheBytes = 32L * 1024L * 1024L
        var cacheSize = queries.audioWaveformCacheSize().executeAsOne()
        if (cacheSize <= maxAudioWaveformCacheBytes) return
        queries.oldestCachedAudioWaveforms(100).executeAsList().forEach { waveform ->
            if (cacheSize <= maxAudioWaveformCacheBytes) return
            queries.deleteCachedAudioWaveform(waveform.source_id, waveform.remote_track_id, waveform.quality_key)
            cacheSize -= waveform.size_bytes
        }
    }

    private fun upsertDownloadedAudio(
        sourceId: String,
        track: Track,
        qualityKey: String,
        file: File,
        sizeBytes: Long,
        contentType: String?,
        downloadedAtEpochMillis: Long,
    ) {
        queries.upsertDownloadedAudio(
            source_id = sourceId,
            remote_track_id = track.id.value,
            quality_key = qualityKey,
            file_path = file.absolutePath,
            size_bytes = sizeBytes,
            content_type = contentType,
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
            downloaded_at_epoch_millis = downloadedAtEpochMillis,
        )
    }
}

data class AndroidCachedAudioFile(
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
)

data class AndroidCachedAudioMetadata(
    val file: File,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class AndroidDownloadedAudioFile(
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
)

data class AndroidDownloadedTrack(
    val track: Track,
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
    val downloadedAtEpochMillis: Long,
)

data class AndroidPlaybackHistoryItem(
    val track: Track,
    val playedAtEpochMillis: Long,
)

private fun Media_source.toSavedMediaSource(): SavedMediaSource =
    SavedMediaSource(
        id = id,
        providerId = provider_id,
        cacheNamespace = cache_namespace,
        displayName = display_name.takeUnless { it == "Navidrome" } ?: base_url,
        baseUrl = base_url,
        username = username,
        token = token,
        salt = salt,
        nativeToken = native_token,
        tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = insecure_skip_tls_verification != 0L,
            customCertificatePath = custom_certificate_path,
            clientCertificateKeyStorePath = client_certificate_keystore_path,
            clientCertificateKeyStorePassword = client_certificate_keystore_password,
        ),
        createdAtEpochMillis = created_at_epoch_millis,
        lastConnectedAtEpochMillis = last_connected_at_epoch_millis,
        lastSyncStartedAtEpochMillis = last_sync_started_at_epoch_millis,
        lastSyncCompletedAtEpochMillis = last_sync_completed_at_epoch_millis,
    )

private fun Library_track.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = remote_artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = remote_album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = audioInfo(audio_codec, audio_bitrate_kbps, audio_content_type, audio_bit_depth, audio_sampling_rate_hz),
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun SelectArtistPopularTracks.toPopularTrackMatch(): ArtistPopularTrackMatch =
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
            durationSeconds = duration_seconds?.toInt(),
            coverArtId = cover_art_id,
            audioInfo = audioInfo(audio_codec, audio_bitrate_kbps, audio_content_type, audio_bit_depth, audio_sampling_rate_hz),
            replayGain = null,
            favoritedAtIso8601 = favorited_at_iso8601,
            userRating = user_rating?.toInt(),
        ),
        fetchedAtEpochMillis = fetched_at_epoch_millis,
    )

private fun Downloaded_audio.toTrack(): Track =
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
        audioInfo = audioInfo(audio_codec, audio_bitrate_kbps, audio_content_type, audio_bit_depth, audio_sampling_rate_hz),
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun Playback_history.toTrack(): Track =
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
        audioInfo = audioInfo(audio_codec, audio_bitrate_kbps, audio_content_type, audio_bit_depth, audio_sampling_rate_hz),
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun audioInfo(
    codec: String?,
    bitrateKbps: Long?,
    contentType: String?,
    bitDepth: Long?,
    samplingRateHz: Long?,
): AudioInfo? =
    AudioInfo(
        codec = codec,
        bitrateKbps = bitrateKbps?.toInt(),
        contentType = contentType,
        bitDepth = bitDepth?.toInt(),
        samplingRateHz = samplingRateHz?.toInt(),
    ).takeIf {
        it.codec != null ||
            it.bitrateKbps != null ||
            it.contentType != null ||
            it.bitDepth != null ||
            it.samplingRateHz != null
    }

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }

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

private fun String.searchText(): String =
    lowercase().trim()

private fun moveDownloadedAudio(temp: File, target: File) {
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
}

private class AndroidAudioByteStore(
    private val directory: File,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        directory.mkdirs()
        val target = File(directory, fileName)
        val temp = File(directory, "${target.name}.tmp")
        return try {
            temp.outputStream().use { output ->
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            }
            moveDownloadedAudio(temp, target)
            StoredAudioBytes(
                filePath = target.absolutePath,
                sizeBytes = target.length(),
            )
        } catch (exception: Exception) {
            temp.delete()
            throw exception
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        File(filePath).delete()
    }
}

private fun clearFiles(directory: File) {
    if (!directory.exists()) return
    directory.walkBottomUp()
        .filter { it != directory }
        .forEach { it.delete() }
}

private fun nowMillis(): Long = System.currentTimeMillis()

private const val DatabaseName = "naviamp-storage.db"
