package app.naviamp.storage

import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlinx.serialization.json.Json

/** Shared SQLDelight persistence for Core-owned playback-session policy. */
class StoragePlaybackSessionStore(
    private val queries: NaviampStorageQueries,
    private val nowEpochMillis: () -> Long,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? {
        val id = sourceId ?: return null
        val payload = queries.selectPlaybackSession(id).executeAsOneOrNull() ?: return null
        return runCatching { json.decodeFromString<PlaybackSessionSettings>(payload) }.getOrNull()
    }

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        val id = sourceId ?: return
        if (session == null) {
            queries.deletePlaybackSession(id)
        } else {
            queries.upsertPlaybackSession(
                source_id = id,
                payload = json.encodeToString(session),
                updated_at_epoch_millis = nowEpochMillis(),
            )
        }
    }
}
