package app.naviamp.domain.cache

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId

interface SidecarStatusStore {
    fun upsertSidecarStatus(
        sourceId: String,
        trackId: String,
        qualityKey: String,
        sidecarType: String,
        status: String,
        attempts: Long,
        lastError: String?,
        updatedAtEpochMillis: Long,
    )
}

class SidecarStatusService(
    private val store: SidecarStatusStore,
    private val nowMillis: () -> Long,
) : SidecarStatusRepository {
    override fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
        errorMessage: String?,
    ) {
        store.upsertSidecarStatus(
            sourceId = sourceId,
            trackId = trackId.value,
            qualityKey = quality.cacheKey(),
            sidecarType = sidecarType,
            status = if (success) SidecarStatusReady else SidecarStatusFailed,
            attempts = 1,
            lastError = errorMessage,
            updatedAtEpochMillis = nowMillis(),
        )
    }
}

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }

private const val SidecarStatusReady = "ready"
private const val SidecarStatusFailed = "failed"
