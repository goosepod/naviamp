package app.naviamp.desktop

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.downloadContentType
import app.naviamp.domain.cache.CachedAudioEvictionCandidate
import app.naviamp.domain.cache.planAudioCacheEviction
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.storage.Downloaded_audio
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists

class DesktopAudioStore(
    private val queries: NaviampStorageQueries,
    private val audioCacheByteStoreService: AudioByteStoreService,
    private val downloadAudioByteStoreService: AudioByteStoreService,
    private val nowMillis: () -> Long,
    private var maxAudioCacheBytes: Long,
) {
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

    suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): CachedAudioFile? =
        withContext(Dispatchers.IO) {
            val row = queries.selectAnyCachedAudio(
                source_id = sourceId,
                remote_track_id = trackId.value,
            ).executeAsOneOrNull() ?: return@withContext null

            val path = Path.of(row.file_path)
            if (!path.exists()) {
                queries.deleteCachedAudio(sourceId, trackId.value, row.quality_key)
                return@withContext null
            }

            queries.touchCachedAudio(nowMillis(), sourceId, trackId.value, row.quality_key)
            CachedAudioFile(
                path = path,
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
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

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality))
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

    suspend fun downloadedAudioFile(
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

    suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        withContext(Dispatchers.IO) {
            downloadedAudioFile(sourceId, track.id)?.let { return@withContext it }

            val qualityKey = quality.cacheKey()
            val streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality))
            val downloadContentType = quality.downloadContentType(track.audioInfo?.contentType)
            val stored = downloadAudioByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = downloadContentType,
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
            upsertDownloadedAudio(sourceId, track, qualityKey, stored.filePath, stored.sizeBytes, downloadContentType, nowMillis())
            DownloadedAudioFile(
                path = target,
                sizeBytes = stored.sizeBytes,
                contentType = downloadContentType,
            )
        }

    suspend fun replaceDownloadedAudioTrack(
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
            val streamUrl = provider.streamUrl(StreamRequest(trackId = track.id, quality = quality))
            val downloadContentType = quality.downloadContentType(track.audioInfo?.contentType)
            val stored = downloadAudioByteStoreService.writeProviderAudio(
                sourceId = sourceId,
                trackId = track.id,
                qualityKey = qualityKey,
                contentType = downloadContentType,
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
            upsertDownloadedAudio(sourceId, track, qualityKey, stored.filePath, stored.sizeBytes, downloadContentType, nowMillis())
            DownloadedAudioFile(
                path = target,
                sizeBytes = stored.sizeBytes,
                contentType = downloadContentType,
            )
        }

    fun downloadedTracks(sourceId: String): List<DownloadedTrack> =
        queries.selectDownloadedAudio(sourceId).executeAsList().map { row ->
            DownloadedTrack(
                track = row.toTrack(),
                path = Path.of(row.file_path),
                sizeBytes = row.size_bytes,
                contentType = row.content_type,
                qualityKey = row.quality_key,
                downloadedAtEpochMillis = row.downloaded_at_epoch_millis,
            )
        }

    fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) {
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

    fun removeDownloadedAudio(sourceId: String, trackId: TrackId) {
        queries.selectDownloadedAudio(sourceId)
            .executeAsList()
            .filter { row -> row.remote_track_id == trackId.value }
            .forEach { row ->
                downloadAudioByteStoreService.deleteAudio(row.file_path)
            }
        queries.deleteDownloadedAudioForTrack(sourceId, trackId.value)
    }

    private fun trimAudioStore() {
        var cacheSize = queries.audioCacheSize().executeAsOne()
        if (cacheSize <= maxAudioCacheBytes) return

        val oldestAudio = queries.oldestCachedAudio(100).executeAsList()
        val filePathsByKey = oldestAudio.associate { audio ->
            Triple(audio.source_id, audio.remote_track_id, audio.quality_key) to audio.file_path
        }
        val plan = planAudioCacheEviction(
            currentSizeBytes = cacheSize,
            maxSizeBytes = maxAudioCacheBytes,
            oldestFirstCandidates = oldestAudio.map { audio ->
                CachedAudioEvictionCandidate(
                    sourceId = audio.source_id,
                    trackId = audio.remote_track_id,
                    qualityKey = audio.quality_key,
                    sizeBytes = audio.size_bytes,
                )
            },
        )
        plan.candidatesToEvict.forEach { audio ->
            queries.deleteCachedAudio(audio.sourceId, audio.trackId, audio.qualityKey)
            filePathsByKey[Triple(audio.sourceId, audio.trackId, audio.qualityKey)]
                ?.let { filePath -> audioCacheByteStoreService.deleteAudio(filePath) }
            cacheSize -= audio.sizeBytes
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

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }
