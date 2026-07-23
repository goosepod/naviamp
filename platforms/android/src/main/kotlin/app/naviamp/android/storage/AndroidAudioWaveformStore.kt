package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.waveformCacheKey
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidAudioWaveformStore(
    private val queries: NaviampStorageQueries,
    private val json: Json,
    private val nowMillis: () -> Long,
    private val maxAudioWaveformCacheBytes: Long,
) : AudioWaveformStorageRepository {
    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? =
        withContext(AndroidWaveformStorageDispatcher) {
            val qualityKey = quality.waveformCacheKey()
            val row = queries.selectCachedAudioWaveform(sourceId, trackId.value, qualityKey).executeAsOneOrNull()
                ?: return@withContext null
            if (row.bucket_count.toInt() != bucketCount) return@withContext null
            queries.touchCachedAudioWaveform(nowMillis(), sourceId, trackId.value, qualityKey)
            AudioWaveform(json.decodeFromString<List<Float>>(row.amplitudes_json))
        }

    override suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform =
        withContext(AndroidWaveformStorageDispatcher) {
            storeAudioWaveformRow(sourceId, trackId, quality, audioFilePath, waveform)
        }

    fun upsertAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String,
        waveform: AudioWaveform,
    ): AudioWaveform =
        storeAudioWaveformRow(sourceId, trackId, quality, audioFilePath, waveform)

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
        trim()
        return waveform
    }

    private fun trim() {
        var cacheSize = queries.audioWaveformCacheSize().executeAsOne()
        if (cacheSize <= maxAudioWaveformCacheBytes) return
        queries.oldestCachedAudioWaveforms(100).executeAsList().forEach { waveform ->
            if (cacheSize <= maxAudioWaveformCacheBytes) return
            queries.deleteCachedAudioWaveform(waveform.source_id, waveform.remote_track_id, waveform.quality_key)
            cacheSize -= waveform.size_bytes
        }
    }
}
