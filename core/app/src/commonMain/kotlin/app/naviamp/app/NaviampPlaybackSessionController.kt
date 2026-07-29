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
import app.naviamp.domain.settings.playbackSessionFromQueue
import app.naviamp.domain.settings.shouldThrottlePlaybackSessionSave
import kotlin.time.TimeSource

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
    private val loadedSessionSources = mutableSetOf<String?>()
    private val loadedSessions = mutableMapOf<String?, PlaybackSessionSettings?>()
    private var lastLoadMillis: Double? = null
    private var lastLoadWasCached = false
    private var lastPlanMillis: Double? = null
    private var lastSaveMillis: Double? = null

    fun load(sourceId: String? = null): PlaybackSessionSettings? {
        val mark = TimeSource.Monotonic.markNow()
        val cached = sourceId in loadedSessionSources
        val session = if (cached) loadedSessions[sourceId] else repository.loadPlaybackSession(sourceId)
        if (!cached) {
            loadedSessionSources += sourceId
            loadedSessions[sourceId] = session
        }
        return session.also {
            lastLoadWasCached = cached
            lastLoadMillis = mark.elapsedNow().inWholeMicroseconds / 1_000.0
        }
    }

    fun restorePlan(sourceId: String? = null): PlaybackSessionRestorePlan =
        planPlaybackSessionRestore(load(sourceId))

    fun planAndSave(request: NaviampPlaybackSessionSaveRequest): PlaybackSessionSavePlan {
        val planningMark = TimeSource.Monotonic.markNow()
        val plan = planPlaybackSessionSave(
            activeSourceId = request.sourceId,
            station = request.station,
            currentTrack = request.currentTrack,
            playbackQueue = request.playbackQueue,
            progressPositionSeconds = request.progressPositionSeconds,
            notificationPositionSeconds = request.platformPositionSeconds,
            existingSession = load(request.sourceId),
        )
        lastPlanMillis = planningMark.elapsedNow().inWholeMicroseconds / 1_000.0
        if (plan is PlaybackSessionSavePlan.Save) {
            val saveMark = TimeSource.Monotonic.markNow()
            save(plan.session, request.sourceId)
            lastSaveMillis = saveMark.elapsedNow().inWholeMicroseconds / 1_000.0
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
        loadedSessionSources += sourceId
        loadedSessions[sourceId] = session
        rememberSavedPosition(sourceId, session)
    }

    fun saveSessionThrottled(
        session: PlaybackSessionSettings?,
        sourceId: String? = null,
        force: Boolean,
        nowMillis: Long,
        saveIntervalMillis: Long,
    ): Boolean {
        if (
            shouldThrottlePlaybackSessionSave(
                activeSourceId = sourceId,
                hasPlaybackTarget = session?.currentTrack() != null,
                force = force,
                nowMillis = nowMillis,
                lastSavedAtMillis = lastSavedAtMillis[sourceId] ?: 0L,
                saveIntervalMillis = saveIntervalMillis,
            )
        ) {
            return false
        }
        lastSavedAtMillis[sourceId] = nowMillis
        save(session, sourceId)
        return true
    }

    fun saveQueue(
        playbackQueue: PlaybackQueue,
        positionSeconds: Double?,
        sourceId: String? = null,
    ): PlaybackSessionSettings? =
        playbackSessionFromQueue(playbackQueue, positionSeconds)
            ?.copy(nowPlayingOpen = load(sourceId)?.nowPlayingOpen == true)
            .also { session ->
            save(session, sourceId)
        }

    /** Persists the shared Now Playing overlay state without disturbing the saved playback target. */
    fun updateNowPlayingOpen(open: Boolean, sourceId: String? = null): Boolean {
        val session = load(sourceId) ?: return false
        if (session.nowPlayingOpen == open) return true
        save(session.copy(nowPlayingOpen = open), sourceId)
        return true
    }

    fun clear(sourceId: String? = null) {
        save(session = null, sourceId = sourceId)
        lastSavedAtMillis.remove(sourceId)
    }

    fun performanceDiagnostics(): List<Pair<String, String>> {
        val repositoryPerformance = repository.performanceSnapshot()
        return listOfNotNull(
            lastLoadMillis?.let { "Session load total" to it.millisLabel() },
            lastLoadMillis?.let { "Session load source" to if (lastLoadWasCached) "Memory" else "Database" },
            lastPlanMillis?.let { "Session plan total" to it.millisLabel() },
            lastSaveMillis?.let { "Session save total" to it.millisLabel() },
            repositoryPerformance.readMillis?.let { "Session database read" to it.millisLabel() },
            repositoryPerformance.decodeMillis?.let { "Session JSON decode" to it.millisLabel() },
            repositoryPerformance.encodeMillis?.let { "Session JSON encode" to it.millisLabel() },
            repositoryPerformance.writeMillis?.let { "Session database write" to it.millisLabel() },
            repositoryPerformance.payloadCharacters?.let { "Session payload" to "$it characters" },
            repositoryPerformance.queueRewritten?.let { "Session queue rewritten" to it.toString() },
        )
    }

    private fun savedPosition(sourceId: String?): SavedPlaybackPosition? {
        if (initializedPositionSources.add(sourceId)) {
            load(sourceId).toSavedPlaybackPosition()?.let { saved ->
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

private fun Double.millisLabel(): String = "${(this * 100.0).toLong() / 100.0} ms"

private data class SavedPlaybackPosition(
    val trackId: String,
    val positionSeconds: Double?,
)

private fun PlaybackSessionSettings?.toSavedPlaybackPosition(): SavedPlaybackPosition? {
    val session = this ?: return null
    val track = session.currentTrack() ?: return null
    return SavedPlaybackPosition(track.id.value, session.positionSeconds)
}
