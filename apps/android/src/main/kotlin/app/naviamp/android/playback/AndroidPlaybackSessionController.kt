package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackSessionSaveRequest
import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.shouldThrottlePlaybackSessionSave

fun saveAndroidPlaybackSession(
    state: AndroidAppState,
    playbackSessions: NaviampPlaybackSessionController,
) {
    with(state) {
        val sourceId = activeSourceId ?: return
        playbackSessions.planAndSave(
            NaviampPlaybackSessionSaveRequest(
                sourceId = sourceId,
                station = nowPlayingStation,
                currentTrack = nowPlaying,
                playbackQueue = playbackQueue,
                progressPositionSeconds = playbackProgress.positionSeconds,
                platformPositionSeconds = AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 },
            ),
        )
    }
}

fun saveAndroidPlaybackSessionThrottled(
    state: AndroidAppState,
    playbackSessions: NaviampPlaybackSessionController,
    force: Boolean = false,
) {
    with(state) {
        val now = System.currentTimeMillis()
        if (
            shouldThrottlePlaybackSessionSave(
                activeSourceId = activeSourceId,
                hasPlaybackTarget = nowPlaying != null || nowPlayingStation != null,
                force = force,
                nowMillis = now,
                lastSavedAtMillis = lastPlaybackSessionSaveAtMillis,
                saveIntervalMillis = AndroidPlaybackSessionSaveIntervalMillis,
            )
        ) {
            return
        }
        lastPlaybackSessionSaveAtMillis = now
        saveAndroidPlaybackSession(state, playbackSessions)
    }
}

fun restoreAndroidPlaybackSession(
    state: AndroidAppState,
    playbackSessions: NaviampPlaybackSessionController,
    sourceId: String,
    loadRelatedTracks: (Track) -> Unit,
    synchronizePlaybackQueue: (PlaybackQueue) -> Unit,
): Boolean {
    with(state) {
        val session = playbackSessions.load(sourceId)
        val plan = playbackSessions.restorePlan(sourceId)
        when (plan) {
            PlaybackSessionRestorePlan.None -> {
                if (session == null) {
                    android.util.Log.i("NaviampSession", "No playback session for source=$sourceId")
                } else {
                    android.util.Log.i(
                        "NaviampSession",
                        "Playback session had no current track source=$sourceId tracks=${session.tracks.size} index=${session.currentIndex}",
                    )
                }
                return false
            }
            is PlaybackSessionRestorePlan.InternetRadio -> {
                nowPlaying = null
                nowPlayingStation = plan.station
                nowPlayingStreamMetadata = plan.streamMetadata
                playbackQueue = plan.playbackQueue
                playbackProgress = plan.playbackProgress
                restoredStartPositionSeconds = null
                synchronizePlaybackQueue(plan.playbackQueue)
                android.util.Log.i("NaviampSession", "Restored station source=$sourceId name=${plan.station.name}")
                status = plan.status
                return true
            }
            is PlaybackSessionRestorePlan.TrackSession -> {
                playbackQueue = plan.playbackQueue
                tracks = plan.tracks
                nowPlaying = plan.currentTrack
                nowPlayingStation = null
                nowPlayingStreamMetadata = plan.streamMetadata
                playbackProgress = plan.playbackProgress
                restoredStartPositionSeconds = plan.restoredStartPositionSeconds
                synchronizePlaybackQueue(plan.playbackQueue)
                loadRelatedTracks(plan.currentTrack)
                android.util.Log.i(
                    "NaviampSession",
                    "Restored track source=$sourceId title=${plan.currentTrack.title} queue=${plan.tracks.size} position=${plan.restoredStartPositionSeconds}",
                )
                status = plan.status
                return true
            }
        }
    }
}
