package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformCacheMetadata
import app.naviamp.domain.waveform.waveformCacheKey
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DesktopAudioWaveformStore(
    private val queries: NaviampStorageQueries,
    private val json: Json,
    private val nowMillis: () -> Long,
    private val maxAudioWaveformCacheBytes: Long,
) : AudioWaveformStorageRepository {
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
            trim()
            waveform
        }

    private fun trim() {
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
}
