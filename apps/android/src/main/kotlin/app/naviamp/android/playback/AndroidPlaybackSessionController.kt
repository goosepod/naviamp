package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.playbackSessionFromCurrentTrack
import app.naviamp.domain.settings.restoredTrackSession

fun saveAndroidPlaybackSession(
    state: AndroidAppState,
    storage: AndroidStorage,
) {
    with(state) {
        val sourceId = activeSourceId ?: return
        val station = nowPlayingStation
        if (station != null) {
            storage.savePlaybackSession(
                sourceId = sourceId,
                session = PlaybackSessionSettings.fromInternetRadioStation(station),
            )
            android.util.Log.i("NaviampSession", "Saved station source=$sourceId name=${station.name}")
            return
        }

        val currentTrack = nowPlaying ?: return
        val existingPositionSeconds = activeSourceId
            ?.let(storage::loadPlaybackSession)
            ?.takeIf { session -> session.currentTrack()?.id == currentTrack.id }
            ?.positionSeconds
        val positionSeconds = playbackProgress.positionSeconds
            ?: AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 }
            ?: existingPositionSeconds
        storage.savePlaybackSession(
            sourceId = sourceId,
            session = playbackSessionFromCurrentTrack(
                currentTrack = currentTrack,
                queue = playbackQueue,
                positionSeconds = positionSeconds,
            ),
        )
        android.util.Log.i(
            "NaviampSession",
            "Saved track source=$sourceId title=${currentTrack.title} queue=${playbackQueue.tracks.size} index=${playbackQueue.currentIndex} position=$positionSeconds",
        )
    }
}

fun saveAndroidPlaybackSessionThrottled(
    state: AndroidAppState,
    storage: AndroidStorage,
    force: Boolean = false,
) {
    with(state) {
        if (activeSourceId == null || (nowPlaying == null && nowPlayingStation == null)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPlaybackSessionSaveAtMillis < AndroidPlaybackSessionSaveIntervalMillis) return
        lastPlaybackSessionSaveAtMillis = now
        saveAndroidPlaybackSession(state, storage)
    }
}

fun restoreAndroidPlaybackSession(
    state: AndroidAppState,
    storage: AndroidStorage,
    sourceId: String,
    loadRelatedTracks: (Track) -> Unit,
): Boolean {
    with(state) {
        val session = storage.loadPlaybackSession(sourceId)
        if (session == null) {
            android.util.Log.i("NaviampSession", "No playback session for source=$sourceId")
            return false
        }
        session.internetRadioStation?.toStation()?.let { station ->
            nowPlaying = null
            nowPlayingStation = station
            nowPlayingStreamMetadata = PlaybackStreamMetadata()
            playbackQueue = PlaybackQueue()
            playbackProgress = PlaybackProgress.Unknown
            restoredStartPositionSeconds = null
            nowPlayingOpen = true
            android.util.Log.i("NaviampSession", "Restored station source=$sourceId name=${station.name}")
            status = "Restored ${station.name}. Press play to resume."
            return true
        }

        val restoredSession = session.restoredTrackSession() ?: run {
            android.util.Log.i(
                "NaviampSession",
                "Playback session had no current track source=$sourceId tracks=${session.tracks.size} index=${session.currentIndex}",
            )
            return false
        }
        playbackQueue = restoredSession.playbackQueue
        tracks = restoredSession.tracks
        nowPlaying = restoredSession.currentTrack
        nowPlayingStation = null
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        playbackProgress = restoredSession.playbackProgress
        restoredStartPositionSeconds = session.positionSeconds
        loadRelatedTracks(restoredSession.currentTrack)
        android.util.Log.i(
            "NaviampSession",
            "Restored track source=$sourceId title=${restoredSession.currentTrack.title} queue=${restoredSession.tracks.size} position=${session.positionSeconds}",
        )
        status = "Restored ${restoredSession.currentTrack.title}. Press play to resume."
        return true
    }
}
