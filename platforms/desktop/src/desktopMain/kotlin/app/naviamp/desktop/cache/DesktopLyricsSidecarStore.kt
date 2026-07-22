package app.naviamp.desktop

import app.naviamp.domain.cache.CachedLyricsRow
import app.naviamp.domain.cache.LyricsSidecarStore
import app.naviamp.storage.NaviampStorageQueries

class DesktopLyricsSidecarStore(
    private val queries: NaviampStorageQueries,
) : LyricsSidecarStore {
    override fun cachedLyrics(sourceId: String, trackId: String): CachedLyricsRow? =
        queries.selectCachedLyrics(
            source_id = sourceId,
            remote_track_id = trackId,
        ).executeAsOneOrNull()?.let { row ->
            CachedLyricsRow(
                lyricSource = row.lyric_source,
                synced = row.synced != 0L,
                linesJson = row.lines_json,
                displayArtist = row.display_artist,
                displayTitle = row.display_title,
                language = row.language,
                offsetMillis = row.offset_millis,
            )
        }

    override fun touchCachedLyrics(sourceId: String, trackId: String, lastAccessedEpochMillis: Long) {
        queries.touchCachedLyrics(lastAccessedEpochMillis, sourceId, trackId)
    }

    override fun upsertCachedLyrics(
        sourceId: String,
        trackId: String,
        lyricSource: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    ) {
        queries.upsertCachedLyrics(
            source_id = sourceId,
            remote_track_id = trackId,
            lyric_source = lyricSource,
            synced = if (synced) 1L else 0L,
            lines_json = linesJson,
            display_artist = displayArtist,
            display_title = displayTitle,
            language = language,
            offset_millis = offsetMillis,
            size_bytes = sizeBytes,
            created_at_epoch_millis = createdAtEpochMillis,
            last_accessed_epoch_millis = lastAccessedEpochMillis,
        )
    }

    override fun cachedLrclibLyrics(sourceId: String, trackId: String): CachedLyricsRow? =
        queries.selectCachedLrclibLyrics(
            source_id = sourceId,
            remote_track_id = trackId,
        ).executeAsOneOrNull()?.let { row ->
            CachedLyricsRow(
                lyricSource = "Lrclib",
                synced = row.synced != 0L,
                linesJson = row.lines_json,
                displayArtist = row.display_artist,
                displayTitle = row.display_title,
                language = row.language,
                offsetMillis = row.offset_millis,
            )
        }

    override fun touchCachedLrclibLyrics(sourceId: String, trackId: String, lastAccessedEpochMillis: Long) {
        queries.touchCachedLrclibLyrics(lastAccessedEpochMillis, sourceId, trackId)
    }

    override fun upsertCachedLrclibLyrics(
        sourceId: String,
        trackId: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    ) {
        queries.upsertCachedLrclibLyrics(
            source_id = sourceId,
            remote_track_id = trackId,
            synced = if (synced) 1L else 0L,
            lines_json = linesJson,
            display_artist = displayArtist,
            display_title = displayTitle,
            language = language,
            offset_millis = offsetMillis,
            size_bytes = sizeBytes,
            created_at_epoch_millis = createdAtEpochMillis,
            last_accessed_epoch_millis = lastAccessedEpochMillis,
        )
    }
}
