package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.planPlaybackSessionRestore
import app.naviamp.domain.settings.planPlaybackSessionSave

data class NaviampPlaybackSessionSaveRequest(
    val sourceId: String?,
    val station: InternetRadioStation?,
    val currentTrack: Track?,
    val playbackQueue: PlaybackQueue,
    val progressPositionSeconds: Double?,
    val platformPositionSeconds: Double? = null,
)

/**
 * Shared playback-session decision and persistence boundary.
 *
 * This controller owns saved-session planning, not audio execution. Android's foreground service
 * and each platform playback engine remain responsible for applying a restore plan safely.
 */
class NaviampPlaybackSessionController(
    private val repository: PlaybackSessionRepository,
) {
    fun load(sourceId: String? = null): PlaybackSessionSettings? =
        repository.loadPlaybackSession(sourceId)

    fun restorePlan(sourceId: String? = null): PlaybackSessionRestorePlan =
        planPlaybackSessionRestore(load(sourceId))

    fun planAndSave(request: NaviampPlaybackSessionSaveRequest): PlaybackSessionSavePlan {
        val plan = planPlaybackSessionSave(
            activeSourceId = request.sourceId,
            station = request.station,
            currentTrack = request.currentTrack,
            playbackQueue = request.playbackQueue,
            progressPositionSeconds = request.progressPositionSeconds,
            notificationPositionSeconds = request.platformPositionSeconds,
            existingSession = load(request.sourceId),
        )
        if (plan is PlaybackSessionSavePlan.Save) {
            repository.savePlaybackSession(plan.session, request.sourceId)
        }
        return plan
    }

    fun save(session: PlaybackSessionSettings?, sourceId: String? = null) {
        repository.savePlaybackSession(session, sourceId)
    }

    fun clear(sourceId: String? = null) {
        save(session = null, sourceId = sourceId)
    }
}
