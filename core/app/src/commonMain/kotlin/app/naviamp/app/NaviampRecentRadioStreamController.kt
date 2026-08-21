package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.radio.recentRadioStreamsForSource
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedTrack

/** Shared application owner for durable recent-radio updates across UI and service lifetimes. */
class NaviampRecentRadioStreamController(
    private val load: () -> List<RecentRadioStream>,
    private val save: (List<RecentRadioStream>) -> Unit,
    private val onChanged: () -> Unit = {},
    private val currentSourceId: () -> String? = { null },
    private val nowEpochMillis: () -> Long = { 0L },
) {
    fun current(): List<RecentRadioStream> {
        val stored = load()
        val retained = stored.take(app.naviamp.domain.radio.MaxRecentRadioStreams)
        if (retained.size != stored.size) save(retained)
        return recentRadioStreamsForSource(retained, currentSourceId())
    }

    fun remember(stream: RecentRadioStream): List<RecentRadioStream> {
        val updated = recentRadioStreamsWith(load(), stream)
        save(updated)
        onChanged()
        return recentRadioStreamsForSource(updated, currentSourceId())
    }

    fun remember(stream: RecentRadioStream, tracks: List<Track>): List<RecentRadioStream> {
        val stored = load()
        val startedAt = nowEpochMillis()
        val baseId = "${stream.id}:session:$startedAt"
        val ids = stored.mapTo(mutableSetOf()) { it.id }
        var sessionId = baseId
        var suffix = 2
        while (sessionId in ids) {
            sessionId = "$baseId:$suffix"
            suffix += 1
        }
        val session = stream.copy(
            id = sessionId,
            sourceId = currentSourceId(),
            startedAtEpochMillis = startedAt,
            sessionTracks = tracks.map(SavedTrack::fromTrack),
        )
        val updated = recentRadioStreamsWith(stored, session)
        save(updated)
        onChanged()
        return recentRadioStreamsForSource(updated, currentSourceId())
    }

    fun clear() {
        save(emptyList())
        onChanged()
    }
}
