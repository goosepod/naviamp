package app.naviamp.desktop

import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.storage.NaviampStorageQueries

class DesktopLyricsOffsetStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
) : LyricsOffsetRepository {
    override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int =
        queries.selectTrackLyricsOffset(sourceId, trackId.value)
            .executeAsOneOrNull()
            ?.toInt()
            ?: 0

    override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
        queries.upsertTrackLyricsOffset(
            source_id = sourceId,
            remote_track_id = trackId.value,
            offset_millis = offsetMillis.toLong(),
            updated_at_epoch_millis = nowMillis(),
        )
    }
}
