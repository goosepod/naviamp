package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.radio.recentRadioStreamsForSource
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.radio.reconciledRecentRadioStreams
import app.naviamp.domain.radio.stationIdentity
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared application owner for durable recent-radio updates across UI and service lifetimes. */
class NaviampRecentRadioStreamController(
    private val load: () -> List<RecentRadioStream>,
    private val save: (List<RecentRadioStream>) -> Unit,
    private val onChanged: () -> Unit = {},
    private val currentSourceId: () -> String? = { null },
    private val nowEpochMillis: () -> Long = { 0L },
) {
    private val mutations = Mutex()

    suspend fun current(): List<RecentRadioStream> = mutations.withLock {
        val stored = load()
        val retained = reconciledRecentRadioStreams(stored)
        if (retained != stored) save(retained)
        recentRadioStreamsForSource(retained, currentSourceId())
    }

    suspend fun remember(stream: RecentRadioStream): List<RecentRadioStream> {
        val updated = mutations.withLock {
            currentCoroutineContext().ensureActive()
            recentRadioStreamsWith(load(), stream.copy(startedAtEpochMillis = nowEpochMillis())).also(save)
        }
        onChanged()
        return recentRadioStreamsForSource(updated, currentSourceId())
    }

    suspend fun remember(
        stream: RecentRadioStream,
        tracks: List<Track>,
        sourceId: String? = currentSourceId(),
    ): List<RecentRadioStream> {
        currentCoroutineContext().ensureActive()
        if (tracks.isEmpty()) return current()
        val updated = mutations.withLock {
            currentCoroutineContext().ensureActive()
            val stored = reconciledRecentRadioStreams(load())
            val station = stream.copy(
                sourceId = sourceId,
                startedAtEpochMillis = nowEpochMillis(),
                sessionTracks = tracks.map(SavedTrack::fromTrack),
            )
            val existing = stored.firstOrNull { it.stationIdentity() == station.stationIdentity() }
                ?: stored.firstOrNull {
                    it.generationOptions == null && it.sessionTracks.isEmpty() &&
                        it.stationIdentity() == station.copy(generationOptions = null).stationIdentity()
                }
            // Unscoped legacy entries are visible beside scoped stations, so UI IDs must not collide.
            val usedIds = stored.mapTo(mutableSetOf()) { it.id }
            var id = existing?.id ?: station.id
            var suffix = 2
            while (existing == null && id in usedIds) id = "${station.id}:station:${suffix++}"
            val retained = stored.filterNot { existing != null && it.id == existing.id && it.sourceId == existing.sourceId }
            recentRadioStreamsWith(retained, station.copy(id = id)).also(save)
        }
        onChanged()
        return recentRadioStreamsForSource(updated, currentSourceId())
    }

    suspend fun clear() {
        mutations.withLock { save(emptyList()) }
        onChanged()
    }
}
