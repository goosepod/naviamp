package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.settings.planPlaybackSessionRestore
import app.naviamp.domain.settings.planPlaybackSessionSave
import app.naviamp.domain.settings.shouldThrottlePlaybackSessionSave

fun saveAndroidPlaybackSession(
    state: AndroidAppState,
    playbackSessionRepository: PlaybackSessionRepository,
) {
    with(state) {
        val sourceId = activeSourceId ?: return
        val plan = planPlaybackSessionSave(
            activeSourceId = sourceId,
            station = nowPlayingStation,
            currentTrack = nowPlaying,
            playbackQueue = playbackQueue,
            progressPositionSeconds = playbackProgress.positionSeconds,
            notificationPositionSeconds = AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 },
            existingSession = playbackSessionRepository.loadPlaybackSession(sourceId),
        )
        if (plan !is PlaybackSessionSavePlan.Save) return
        playbackSessionRepository.savePlaybackSession(session = plan.session, sourceId = sourceId)
        when (plan.kind) {
            PlaybackSessionSavePlan.Kind.InternetRadio -> android.util.Log.i(
                "NaviampSession",
                "Saved station source=$sourceId name=${nowPlayingStation?.name.orEmpty()}",
            )
            PlaybackSessionSavePlan.Kind.Track -> android.util.Log.i(
                "NaviampSession",
                "Saved track source=$sourceId title=${nowPlaying?.title.orEmpty()} queue=${playbackQueue.tracks.size} index=${playbackQueue.currentIndex} position=${plan.session.positionSeconds}",
            )
        }
    }
}

fun saveAndroidPlaybackSessionThrottled(
    state: AndroidAppState,
    playbackSessionRepository: PlaybackSessionRepository,
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
        saveAndroidPlaybackSession(state, playbackSessionRepository)
    }
}

fun restoreAndroidPlaybackSession(
    state: AndroidAppState,
    playbackSessionRepository: PlaybackSessionRepository,
    sourceId: String,
    loadRelatedTracks: (Track) -> Unit,
): Boolean {
    with(state) {
        val session = playbackSessionRepository.loadPlaybackSession(sourceId)
        val plan = planPlaybackSessionRestore(session)
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
                nowPlayingOpen = true
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
                nowPlayingOpen = true
                playbackProgress = plan.playbackProgress
                restoredStartPositionSeconds = plan.restoredStartPositionSeconds
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
