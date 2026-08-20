package app.naviamp.storage

import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.PlaybackSessionRepositoryPerformance
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.queue.PlaybackQueueGroup
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/** Shared SQLDelight persistence for Core-owned playback-session policy. */
class StoragePlaybackSessionStore(
    private val queries: NaviampStorageQueries,
    private val nowEpochMillis: () -> Long,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : PlaybackSessionRepository {
    private var performance = PlaybackSessionRepositoryPerformance()
    private val queueTrackIds = mutableMapOf<String, List<String>>()
    private val queuePayloadCharacters = mutableMapOf<String, Int>()

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? {
        val id = sourceId?.takeIf(String::isNotBlank) ?: return null
        val readMark = TimeSource.Monotonic.markNow()
        val state = queries.selectPlaybackSessionState(id).executeAsOneOrNull()
        val queueRows = state?.let { queries.selectPlaybackSessionQueue(id).executeAsList() }
        val legacyPayload = if (state == null) queries.selectPlaybackSession(id).executeAsOneOrNull() else null
        val readMillis = readMark.elapsedNow().inWholeMicroseconds / 1_000.0
        if (state == null && legacyPayload == null) {
            performance = performance.copy(readMillis = readMillis, decodeMillis = null, payloadCharacters = null)
            return null
        }
        val decodeMark = TimeSource.Monotonic.markNow()
        val decoded = if (state != null && queueRows != null) {
            runCatching {
                val tracks = queueRows.map { row -> json.decodeFromString<SavedTrack>(row.payload) }
                val radio = state.internet_radio_payload?.let {
                    json.decodeFromString<SavedInternetRadioStation>(it)
                }
                queueTrackIds[id] = queueRows.map { it.remote_track_id }
                queuePayloadCharacters[id] = queueRows.sumOf { it.payload.length }
                PlaybackSessionSettings(
                    tracks = tracks,
                    currentIndex = state.current_index.toInt(),
                    playNextCount = state.play_next_count.toInt(),
                    queueGroups = state.queue_groups_payload
                        ?.let { json.decodeFromString<List<PlaybackQueueGroup>>(it) }
                        .orEmpty(),
                    positionSeconds = state.position_seconds,
                    internetRadioStation = radio,
                    nowPlayingOpen = state.now_playing_open != 0L,
                )
            }.getOrNull()
        } else {
            runCatching { json.decodeFromString<PlaybackSessionSettings>(requireNotNull(legacyPayload)) }.getOrNull()
        }
        val payloadCharacters = legacyPayload?.length
            ?: queuePayloadCharacters[id]
            ?: queueRows?.sumOf { it.payload.length }
        performance = performance.copy(
            readMillis = readMillis,
            decodeMillis = decodeMark.elapsedNow().inWholeMicroseconds / 1_000.0,
            payloadCharacters = payloadCharacters,
        )
        return decoded
    }

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        val id = sourceId?.takeIf(String::isNotBlank) ?: return
        if (session == null) {
            val writeMark = TimeSource.Monotonic.markNow()
            queries.transaction {
                queries.deletePlaybackSessionQueue(id)
                queries.deletePlaybackSessionState(id)
                queries.deletePlaybackSession(id)
            }
            queueTrackIds.remove(id)
            queuePayloadCharacters.remove(id)
            performance = performance.copy(
                encodeMillis = null,
                writeMillis = writeMark.elapsedNow().inWholeMicroseconds / 1_000.0,
                payloadCharacters = null,
                queueRewritten = true,
            )
        } else {
            val nextTrackIds = session.tracks.map { it.id }
            val storedTrackIds = queueTrackIds[id]
                ?: queries.selectPlaybackSessionQueueTrackIds(id).executeAsList().also {
                    queueTrackIds[id] = it
                }
            val queueChanged = storedTrackIds != nextTrackIds
            val encodeMark = TimeSource.Monotonic.markNow()
            val queuePayloads = if (queueChanged) {
                session.tracks.map { json.encodeToString(it) }
            } else {
                emptyList()
            }
            val radioPayload = session.internetRadioStation?.let { json.encodeToString(it) }
            val queueGroupsPayload = session.queueGroups
                .takeIf { it.isNotEmpty() }
                ?.let { json.encodeToString(it) }
            val encodeMillis = encodeMark.elapsedNow().inWholeMicroseconds / 1_000.0
            val writeMark = TimeSource.Monotonic.markNow()
            queries.transaction {
                if (queueChanged) {
                    queries.deletePlaybackSessionQueue(id)
                    session.tracks.forEachIndexed { index, track ->
                        queries.insertPlaybackSessionQueueTrack(
                            source_id = id,
                            queue_index = index.toLong(),
                            remote_track_id = track.id,
                            payload = queuePayloads[index],
                        )
                    }
                }
                queries.upsertPlaybackSessionState(
                    source_id = id,
                    current_index = session.currentIndex.toLong(),
                    play_next_count = session.playNextCount.toLong(),
                    queue_groups_payload = queueGroupsPayload,
                    position_seconds = session.positionSeconds,
                    internet_radio_payload = radioPayload,
                    now_playing_open = if (session.nowPlayingOpen) 1L else 0L,
                    updated_at_epoch_millis = nowEpochMillis(),
                )
                queries.deletePlaybackSession(id)
            }
            if (queueChanged) {
                queueTrackIds[id] = nextTrackIds
                queuePayloadCharacters[id] = queuePayloads.sumOf { it.length }
            }
            performance = performance.copy(
                encodeMillis = encodeMillis,
                writeMillis = writeMark.elapsedNow().inWholeMicroseconds / 1_000.0,
                payloadCharacters = queuePayloadCharacters[id] ?: 0,
                queueRewritten = queueChanged,
            )
        }
    }

    override fun performanceSnapshot(): PlaybackSessionRepositoryPerformance = performance
}
