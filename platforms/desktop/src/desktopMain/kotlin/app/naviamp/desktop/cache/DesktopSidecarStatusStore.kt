package app.naviamp.desktop

import app.naviamp.domain.cache.SidecarStatusStore
import app.naviamp.storage.NaviampStorageQueries

class DesktopSidecarStatusStore(
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
