package app.naviamp.storage

import kotlinx.serialization.Serializable

/** User-authored SQL configuration, unlike reproducible indexes, history and playback queues. */
@Serializable internal data class StorageRecoveryPlaybackProfile(
    val sourceId: String, val type: String, val targetId: String, val transition: String,
    val crossfadeSeconds: Long?, val replayGain: String, val updated: Long,
) {
    fun restore(queries: NaviampStorageQueries) = queries.upsertPlaybackProfile(
        sourceId, type, targetId, transition, crossfadeSeconds, replayGain, updated,
    )
}

@Serializable internal data class StorageRecoveryLyricsOffset(
    val sourceId: String, val trackId: String, val offsetMillis: Long, val updated: Long,
) {
    fun restore(queries: NaviampStorageQueries) = queries.upsertTrackLyricsOffset(sourceId, trackId, offsetMillis, updated)
}

internal fun StorageRecoveryState.captureUserConfiguration(queries: NaviampStorageQueries) = replaceUserConfiguration(
    queries.selectRecoveryPlaybackProfiles().executeAsList().map {
        StorageRecoveryPlaybackProfile(it.source_id, it.target_type, it.target_id, it.transition_mode,
            it.crossfade_duration_seconds, it.replay_gain_mode, it.updated_at_epoch_millis)
    },
    queries.selectRecoveryLyricsOffsets().executeAsList().map {
        StorageRecoveryLyricsOffset(it.source_id, it.remote_track_id, it.offset_millis, it.updated_at_epoch_millis)
    },
)
