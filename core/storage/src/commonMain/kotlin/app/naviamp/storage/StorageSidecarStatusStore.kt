package app.naviamp.storage

import app.naviamp.domain.cache.SidecarStatusStore

class StorageSidecarStatusStore(
    private val queries: NaviampStorageQueries,
) : SidecarStatusStore {
    override fun upsertSidecarStatus(
        sourceId: String,
        trackId: String,
        qualityKey: String,
        sidecarType: String,
        status: String,
        attempts: Long,
        lastError: String?,
        updatedAtEpochMillis: Long,
    ) {
        queries.upsertCachedSidecarStatus(
            source_id = sourceId,
            remote_track_id = trackId,
            quality_key = qualityKey,
            sidecar_type = sidecarType,
            status = status,
            attempts = attempts,
            last_error = lastError,
            updated_at_epoch_millis = updatedAtEpochMillis,
        )
    }
}
