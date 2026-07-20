package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.shouldSavePlaybackPosition
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.planPlaybackSessionRestore
import app.naviamp.domain.settings.planPlaybackSessionSave
import app.naviamp.domain.settings.shouldThrottlePlaybackSessionSave

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
    private val lastSavedPositions = mutableMapOf<String?, SavedPlaybackPosition>()
    private val initializedPositionSources = mutableSetOf<String?>()
    private val lastSavedAtMillis = mutableMapOf<String?, Long>()

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
            rememberSavedPosition(request.sourceId, plan.session)
        }
        return plan
    }

    fun planAndSavePositionIfNeeded(
        request: NaviampPlaybackSessionSaveRequest,
        saveThresholdSeconds: Double,
    ): PlaybackSessionSavePlan {
        val positionSeconds = request.progressPositionSeconds ?: return PlaybackSessionSavePlan.None
        val lastSavedPosition = savedPosition(request.sourceId)
            ?.takeIf { saved -> saved.trackId == request.currentTrack?.id?.value }
            ?.positionSeconds
        if (
            !shouldSavePlaybackPosition(
                queue = request.playbackQueue,
                positionSeconds = positionSeconds,
                lastSavedPositionSeconds = lastSavedPosition,
                saveThresholdSeconds = saveThresholdSeconds,
            )
        ) {
            return PlaybackSessionSavePlan.None
        }
        return planAndSave(request)
    }

    fun planAndSaveThrottled(
        request: NaviampPlaybackSessionSaveRequest,
        force: Boolean,
        nowMillis: Long,
        saveIntervalMillis: Long,
    ): PlaybackSessionSavePlan {
        if (
            shouldThrottlePlaybackSessionSave(
                activeSourceId = request.sourceId,
                hasPlaybackTarget = request.currentTrack != null || request.station != null,
                force = force,
                nowMillis = nowMillis,
                lastSavedAtMillis = lastSavedAtMillis[request.sourceId] ?: 0L,
                saveIntervalMillis = saveIntervalMillis,
            )
        ) {
            return PlaybackSessionSavePlan.None
        }
        lastSavedAtMillis[request.sourceId] = nowMillis
        return planAndSave(request)
    }

    fun save(session: PlaybackSessionSettings?, sourceId: String? = null) {
        repository.savePlaybackSession(session, sourceId)
        rememberSavedPosition(sourceId, session)
    }

    fun clear(sourceId: String? = null) {
        save(session = null, sourceId = sourceId)
        lastSavedAtMillis.remove(sourceId)
    }

    private fun savedPosition(sourceId: String?): SavedPlaybackPosition? {
        if (initializedPositionSources.add(sourceId)) {
            repository.loadPlaybackSession(sourceId).toSavedPlaybackPosition()?.let { saved ->
                lastSavedPositions[sourceId] = saved
            }
        }
        return lastSavedPositions[sourceId]
    }

    private fun rememberSavedPosition(sourceId: String?, session: PlaybackSessionSettings?) {
        initializedPositionSources += sourceId
        session.toSavedPlaybackPosition()?.let { saved -> lastSavedPositions[sourceId] = saved }
            ?: lastSavedPositions.remove(sourceId)
    }
}

private data class SavedPlaybackPosition(
    val trackId: String,
    val positionSeconds: Double?,
)

private fun PlaybackSessionSettings?.toSavedPlaybackPosition(): SavedPlaybackPosition? {
    val session = this ?: return null
    val track = session.currentTrack() ?: return null
    return SavedPlaybackPosition(track.id.value, session.positionSeconds)
}
