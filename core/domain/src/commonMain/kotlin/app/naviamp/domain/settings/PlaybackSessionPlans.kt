package app.naviamp.domain.settings

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue

sealed interface PlaybackSessionSavePlan {
    data object None : PlaybackSessionSavePlan

    data class Save(
        val session: PlaybackSessionSettings,
        val kind: Kind,
    ) : PlaybackSessionSavePlan

    enum class Kind {
        InternetRadio,
        Track,
    }
}

sealed interface PlaybackSessionRestorePlan {
    val status: String?

    data object None : PlaybackSessionRestorePlan {
        override val status: String? = null
    }

    data class InternetRadio(
        val station: InternetRadioStation,
        val playbackQueue: PlaybackQueue = PlaybackQueue(),
        val playbackProgress: PlaybackProgress = PlaybackProgress.Unknown,
        val streamMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
        override val status: String = "Restored ${station.name}. Press play to resume.",
    ) : PlaybackSessionRestorePlan

    data class TrackSession(
        val restoredSession: RestoredPlaybackSession,
        val restoredStartPositionSeconds: Double?,
        val streamMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
        override val status: String = "Restored ${restoredSession.currentTrack.title}. Press play to resume.",
    ) : PlaybackSessionRestorePlan {
        val tracks: List<Track> = restoredSession.tracks
        val playbackQueue: PlaybackQueue = restoredSession.playbackQueue
        val currentTrack: Track = restoredSession.currentTrack
        val playbackProgress: PlaybackProgress = restoredSession.playbackProgress
    }
}

fun shouldThrottlePlaybackSessionSave(
    activeSourceId: String?,
    hasPlaybackTarget: Boolean,
    force: Boolean,
    nowMillis: Long,
    lastSavedAtMillis: Long,
    saveIntervalMillis: Long,
): Boolean =
    activeSourceId == null ||
        !hasPlaybackTarget ||
        (!force && nowMillis - lastSavedAtMillis < saveIntervalMillis)

fun planPlaybackSessionSave(
    activeSourceId: String?,
    station: InternetRadioStation?,
    currentTrack: Track?,
    playbackQueue: PlaybackQueue,
    progressPositionSeconds: Double?,
    notificationPositionSeconds: Double?,
    existingSession: PlaybackSessionSettings?,
): PlaybackSessionSavePlan {
    if (activeSourceId == null) return PlaybackSessionSavePlan.None
    if (station != null) {
        return PlaybackSessionSavePlan.Save(
            session = PlaybackSessionSettings.fromInternetRadioStation(station),
            kind = PlaybackSessionSavePlan.Kind.InternetRadio,
        )
    }
    val track = currentTrack ?: return PlaybackSessionSavePlan.None
    val existingPositionSeconds = existingSession
        ?.takeIf { session -> session.currentTrack()?.id == track.id }
        ?.positionSeconds
    val positionSeconds = progressPositionSeconds
        ?: notificationPositionSeconds
        ?: existingPositionSeconds
    val session = playbackSessionFromCurrentTrack(
        currentTrack = track,
        queue = playbackQueue,
        positionSeconds = positionSeconds,
    ) ?: return PlaybackSessionSavePlan.None
    return PlaybackSessionSavePlan.Save(
        session = session,
        kind = PlaybackSessionSavePlan.Kind.Track,
    )
}

fun planPlaybackSessionRestore(session: PlaybackSessionSettings?): PlaybackSessionRestorePlan {
    val savedSession = session ?: return PlaybackSessionRestorePlan.None
    savedSession.internetRadioStation?.toStation()?.let { station ->
        return PlaybackSessionRestorePlan.InternetRadio(station)
    }
    val restoredSession = savedSession.restoredTrackSession() ?: return PlaybackSessionRestorePlan.None
    return PlaybackSessionRestorePlan.TrackSession(
        restoredSession = restoredSession,
        restoredStartPositionSeconds = savedSession.positionSeconds,
    )
}
