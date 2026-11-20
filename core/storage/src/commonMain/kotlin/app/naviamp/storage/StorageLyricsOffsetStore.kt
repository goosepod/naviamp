package app.naviamp.storage

import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LyricsOffsetRepository

class StorageLyricsOffsetStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val recovery: StorageRecoveryState?,
) : LyricsOffsetRepository {
    constructor(queries: NaviampStorageQueries, nowMillis: () -> Long) : this(queries, nowMillis, null)
    override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int? =
        queries.selectTrackLyricsOffset(sourceId, trackId.value)
            .executeAsOneOrNull()
            ?.toInt()

    override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
        fun persist() = queries.upsertTrackLyricsOffset(
            source_id = sourceId,
            remote_track_id = trackId.value,
            offset_millis = offsetMillis.toLong(),
            updated_at_epoch_millis = nowMillis(),
        )
        val state = recovery
        if (state == null) persist() else state.transaction(queries) { persist(); state.captureUserConfiguration(queries) }
    }
}
