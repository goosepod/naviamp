package app.naviamp.storage

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.CachedAudioEvictionCandidate
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.downloadContentType
import app.naviamp.domain.cache.planAudioCacheEviction
import app.naviamp.domain.cache.toStoredAudioQuality
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class StorageCachedAudioFile(
    val filePath: String,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
) {
    val streamQuality: StreamQuality?
        get() = qualityKey.toStoredAudioQuality()
}

data class StorageCachedAudioMetadata(
    val filePath: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class StorageDownloadedAudioFile(
    val filePath: String,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
) {
    val streamQuality: StreamQuality?
        get() = qualityKey.toStoredAudioQuality()
}

data class StorageDownloadedTrack(
    val track: Track,
    val filePath: String,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
    val downloadedAtEpochMillis: Long,
)

/**
 * Portable SQL/download/cache repository shared by every host.
 *
 * Hosts supply only atomic byte storage and exact native file effects. Core owns identities,
 * download replacement, limits, metadata, LRU eviction, missing-file repair, and row lifetime.
 */
class StorageAudioStore(
    private val queries: NaviampStorageQueries,
    private val audioCacheByteStoreService: AudioByteStoreService,
    private val downloadAudioByteStoreService: AudioByteStoreService,
    private val nowEpochMillis: () -> Long,
    private val cachedAudioFileExists: (String) -> Boolean,
    private val downloadedAudioFileExists: (String) -> Boolean,
    private val deleteKnownAudioCacheFile: (String) -> Boolean,
    private val deleteKnownDownloadFile: (String) -> Boolean,
    private val workContext: CoroutineContext = EmptyCoroutineContext,
    private var maxAudioCacheBytes: Long,
    private val protectedTrackIds: () -> Set<String> = ::emptySet,
) : AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata>,
    DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack>,
    DownloadReplacementRepository<StorageDownloadedAudioFile> {

    override fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0L)
        trimAudioStore()
    }

    override fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): StorageCachedAudioMetadata? {
        val row = queries.selectCachedAudioMetadata(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = quality.cacheKey(),
        ).executeAsOneOrNull() ?: return null
        return StorageCachedAudioMetadata(
            filePath = row.file_path,
            exists = cachedAudioFileExists(row.file_path),
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
    ): StorageCachedAudioFile? = withContext(workContext) {
        val qualityKey = quality.cacheKey()
        val row = queries.selectCachedAudio(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
        ).executeAsOneOrNull() ?: return@withContext null
        if (!cachedAudioFileExists(row.file_path)) {
            queries.deleteCachedAudio(sourceId, trackId.value, qualityKey)
            return@withContext null
        }
        queries.touchCachedAudio(nowEpochMillis(), sourceId, trackId.value, qualityKey)
        row.toCachedAudioFile()
    }

    override suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): StorageCachedAudioFile? = withContext(workContext) {
        val row = queries.selectAnyCachedAudio(
            source_id = sourceId,
            remote_track_id = trackId.value,
        ).executeAsOneOrNull() ?: return@withContext null
        if (!cachedAudioFileExists(row.file_path)) {
            queries.deleteCachedAudio(sourceId, trackId.value, row.quality_key)
            return@withContext null
        }
        queries.touchCachedAudio(nowEpochMillis(), sourceId, trackId.value, row.quality_key)
        row.toCachedAudioFile()
    }

    override suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): StorageCachedAudioFile = withContext(workContext) {
        cachedAudioFile(sourceId, track.id, quality)?.let { return@withContext it }
        val qualityKey = quality.cacheKey()
        val stored = audioCacheByteStoreService.writeProviderAudio(
            sourceId = sourceId,
            trackId = track.id,
            qualityKey = qualityKey,
            contentType = track.audioInfo?.contentType,
            provider = provider,
            streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality)),
            errorMessage = "Could not cache audio track.",
        )
        val now = nowEpochMillis()
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
        StorageCachedAudioFile(stored.filePath, stored.sizeBytes, track.audioInfo?.contentType, qualityKey)
    }

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): StorageDownloadedAudioFile? = withContext(workContext) {
        val qualityKey = quality.cacheKey()
        val row = queries.selectDownloadedAudioFile(
            source_id = sourceId,
            remote_track_id = trackId.value,
            quality_key = qualityKey,
        ).executeAsOneOrNull() ?: return@withContext null
        if (!downloadedAudioFileExists(row.file_path)) {
            queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
            return@withContext null
        }
        row.toDownloadedAudioFile()
    }

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): StorageDownloadedAudioFile? = withContext(workContext) {
        val row = queries.selectDownloadedAudioFileForTrack(
            source_id = sourceId,
            remote_track_id = trackId.value,
        ).executeAsOneOrNull() ?: return@withContext null
        if (!downloadedAudioFileExists(row.file_path)) {
            queries.deleteDownloadedAudio(sourceId, trackId.value, row.quality_key)
            return@withContext null
        }
        row.toDownloadedAudioFile()
    }

    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): StorageDownloadedAudioFile = withContext(workContext) {
        downloadedAudioFile(sourceId, track.id)?.let { return@withContext it }
        val qualityKey = quality.cacheKey()
        val contentType = quality.downloadContentType(track.audioInfo?.contentType)
        val stored = downloadAudioByteStoreService.writeProviderAudio(
            sourceId = sourceId,
            trackId = track.id,
            qualityKey = qualityKey,
            contentType = contentType,
            provider = provider,
            streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality)),
            errorMessage = "Could not download audio track.",
        )
        val currentBytes = queries.downloadedAudioSize().executeAsOne()
        if (currentBytes + stored.sizeBytes > maxDownloadBytes.coerceAtLeast(0L)) {
            downloadAudioByteStoreService.deleteAudio(stored.filePath)
            throw IllegalStateException("Download storage limit exceeded.")
        }
        upsertDownloadedAudio(sourceId, track, qualityKey, stored.filePath, stored.sizeBytes, contentType, nowEpochMillis())
        StorageDownloadedAudioFile(stored.filePath, stored.sizeBytes, contentType, qualityKey)
    }

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): StorageDownloadedAudioFile = withContext(workContext) {
        val qualityKey = quality.cacheKey()
        val existingRows = queries.selectDownloadedAudio(sourceId).executeAsList()
            .filter { it.remote_track_id == track.id.value }
        val contentType = quality.downloadContentType(track.audioInfo?.contentType)
        val stored = downloadAudioByteStoreService.writeProviderAudio(
            sourceId = sourceId,
            trackId = track.id,
            qualityKey = qualityKey,
            contentType = contentType,
            provider = provider,
            streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality)),
            errorMessage = "Could not download audio track.",
        )
        val nextSize = queries.downloadedAudioSize().executeAsOne() - existingRows.sumOf { it.size_bytes } + stored.sizeBytes
        if (nextSize > maxDownloadBytes.coerceAtLeast(0L)) {
            downloadAudioByteStoreService.deleteAudio(stored.filePath)
            throw IllegalStateException("Download storage limit exceeded.")
        }
        val obsoleteRows = existingRows.filterNot { it.file_path == stored.filePath }
        if (obsoleteRows.any { row -> downloadedAudioFileExists(row.file_path) && !deleteKnownDownloadFile(row.file_path) }) {
            downloadAudioByteStoreService.deleteAudio(stored.filePath)
            throw IllegalStateException("Could not replace downloaded audio safely.")
        }
        obsoleteRows.forEach { row ->
            queries.deleteDownloadedAudio(row.source_id, row.remote_track_id, row.quality_key)
        }
        queries.deleteDownloadedAudioForTrack(sourceId, track.id.value)
        upsertDownloadedAudio(sourceId, track, qualityKey, stored.filePath, stored.sizeBytes, contentType, nowEpochMillis())
        StorageDownloadedAudioFile(stored.filePath, stored.sizeBytes, contentType, qualityKey)
    }

    override fun downloadedTracks(sourceId: String): List<StorageDownloadedTrack> =
        queries.selectDownloadedAudio(sourceId).executeAsList().map { row ->
            StorageDownloadedTrack(
                track = row.toTrack(),
                filePath = row.file_path,
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
                qualityKey = row.quality_key,
                downloadedAtEpochMillis = row.downloaded_at_epoch_millis,
            )
        }

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) {
        val qualityKey = quality.cacheKey()
        queries.selectDownloadedAudioFile(sourceId, trackId.value, qualityKey).executeAsOneOrNull()?.let { row ->
            if (!downloadedAudioFileExists(row.file_path) || deleteKnownDownloadFile(row.file_path)) {
                queries.deleteDownloadedAudio(sourceId, trackId.value, qualityKey)
            }
        }
    }

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) {
        queries.selectDownloadedAudio(sourceId).executeAsList()
            .filter { it.remote_track_id == trackId.value }
            .forEach { row ->
                if (!downloadedAudioFileExists(row.file_path) || deleteKnownDownloadFile(row.file_path)) {
                    queries.deleteDownloadedAudio(row.source_id, row.remote_track_id, row.quality_key)
                }
            }
    }

    private fun trimAudioStore() {
        var cacheSize = queries.audioCacheSize().executeAsOne()
        if (cacheSize <= maxAudioCacheBytes) return
        val oldest = queries.oldestCachedAudio(100).executeAsList()
        val paths = oldest.associate { Triple(it.source_id, it.remote_track_id, it.quality_key) to it.file_path }
        val plan = planAudioCacheEviction(
            currentSizeBytes = cacheSize,
            maxSizeBytes = maxAudioCacheBytes,
            oldestFirstCandidates = oldest.map {
                CachedAudioEvictionCandidate(it.source_id, it.remote_track_id, it.quality_key, it.size_bytes)
            },
            protectedTrackIds = protectedTrackIds(),
        )
        plan.candidatesToEvict.forEach { candidate ->
            val path = paths[Triple(candidate.sourceId, candidate.trackId, candidate.qualityKey)] ?: return@forEach
            if (!cachedAudioFileExists(path) || deleteKnownAudioCacheFile(path)) {
                queries.deleteCachedAudio(candidate.sourceId, candidate.trackId, candidate.qualityKey)
                cacheSize -= candidate.sizeBytes
            }
        }
    }

    private fun upsertDownloadedAudio(
        sourceId: String,
        track: Track,
        qualityKey: String,
        filePath: String,
        sizeBytes: Long,
        contentType: String?,
        downloadedAtEpochMillis: Long,
    ) {
        queries.upsertDownloadedAudio(
            source_id = sourceId,
            remote_track_id = track.id.value,
            quality_key = qualityKey,
            file_path = filePath,
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

private fun Cached_audio.toCachedAudioFile() =
    StorageCachedAudioFile(file_path, size_bytes, content_type, quality_key)

private fun Downloaded_audio.toDownloadedAudioFile() =
    StorageDownloadedAudioFile(file_path, size_bytes, content_type, quality_key)

private fun Downloaded_audio.toTrack(): Track = Track(
    id = TrackId(remote_track_id),
    title = title,
    artistId = artist_id?.let(::ArtistId),
    artistName = artist_name,
    albumId = album_id?.let(::AlbumId),
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
        it.codec != null || it.bitrateKbps != null || it.contentType != null ||
            it.bitDepth != null || it.samplingRateHz != null
    },
    replayGain = null,
    favoritedAtIso8601 = favorited_at_iso8601,
    userRating = user_rating?.toInt(),
)

private fun StreamQuality.cacheKey(): String = when (this) {
    StreamQuality.Original -> "original"
    is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
}
