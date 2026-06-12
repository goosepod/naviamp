package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

data class CacheRuntimeStats(
    val playbackSource: PlaybackSource = PlaybackSource.Unknown,
    val prefetch: AudioPrefetchStats = AudioPrefetchStats(),
)

data class AudioPrefetchStats(
    val enabled: Boolean = false,
    val configuredDepth: Int = 0,
    val running: Boolean = false,
    val queued: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val sidecarCompleted: Int = 0,
    val sidecarFailed: Int = 0,
    val lastError: String? = null,
    val lastSidecarError: String? = null,
)

data class AudioPrefetchWork<Provider>(
    val sourceId: String,
    val provider: Provider,
    val quality: StreamQuality,
    val tracks: List<Track>,
    val stats: AudioPrefetchStats,
)

fun initialAudioPrefetchStats(
    enabled: Boolean,
    configuredDepth: Int,
): AudioPrefetchStats =
    AudioPrefetchStats(
        enabled = enabled,
        configuredDepth = configuredDepth.coerceIn(0, MaxAudioPrefetchDepth),
    )

fun AudioPrefetchStats.started(queued: Int): AudioPrefetchStats =
    copy(
        running = true,
        queued = queued.coerceAtLeast(0),
        completed = 0,
        failed = 0,
        sidecarCompleted = 0,
        sidecarFailed = 0,
        lastError = null,
        lastSidecarError = null,
    )

fun AudioPrefetchStats.finished(): AudioPrefetchStats =
    copy(running = false)

fun AudioPrefetchStats.audioSuccess(sidecarResult: PlaybackSidecarPrepResult): AudioPrefetchStats =
    copy(
        completed = completed + 1,
        sidecarCompleted = sidecarCompleted + if (sidecarResult.successful) 1 else 0,
        sidecarFailed = sidecarFailed + if (sidecarResult.successful) 0 else 1,
        lastSidecarError = sidecarResult.lastError ?: lastSidecarError,
    )

fun AudioPrefetchStats.audioFailure(error: Throwable?): AudioPrefetchStats =
    copy(
        failed = failed + 1,
        lastError = error?.message,
    )

fun <Provider> planAudioPrefetchWork(
    sourceId: String?,
    provider: Provider?,
    quality: StreamQuality?,
    queue: PlaybackQueue,
    enabled: Boolean,
    configuredDepth: Int,
    includeCurrentTrack: Boolean = false,
): AudioPrefetchWork<Provider>? {
    val stats = initialAudioPrefetchStats(
        enabled = enabled,
        configuredDepth = configuredDepth,
    )
    if (!stats.enabled || stats.configuredDepth <= 0) return null
    val activeSourceId = sourceId ?: return null
    val activeProvider = provider ?: return null
    val activeQuality = quality ?: return null
    val tracks = audioPrefetchTracks(
        queue = queue,
        depth = stats.configuredDepth,
        includeCurrentTrack = includeCurrentTrack,
    )
    if (tracks.isEmpty()) return null
    return AudioPrefetchWork(
        sourceId = activeSourceId,
        provider = activeProvider,
        quality = activeQuality,
        tracks = tracks,
        stats = stats,
    )
}

suspend fun <CachedAudio> runAudioPrefetch(
    stats: AudioPrefetchStats,
    tracks: List<Track>,
    isActive: () -> Boolean,
    cacheAudio: suspend (Track) -> CachedAudio?,
    prepareSidecars: suspend (Track, CachedAudio?) -> PlaybackSidecarPrepResult = { _, _ ->
        PlaybackSidecarPrepResult()
    },
    onTrackCached: suspend (Track, CachedAudio?) -> Unit = { _, _ -> },
    onTrackFailed: suspend (Track, Throwable) -> Unit = { _, _ -> },
    onStatsChanged: (AudioPrefetchStats) -> Unit = {},
): AudioPrefetchStats {
    var currentStats = stats.started(tracks.size)
    onStatsChanged(currentStats)
    for (track in tracks) {
        if (!isActive()) break
        var sidecarResult = PlaybackSidecarPrepResult()
        val result = runCatching {
            val cachedAudio = cacheAudio(track)
            sidecarResult = prepareSidecars(track, cachedAudio)
            cachedAudio
        }
        currentStats = result.fold(
            onSuccess = { cachedAudio ->
                onTrackCached(track, cachedAudio)
                currentStats.audioSuccess(sidecarResult)
            },
            onFailure = { error ->
                onTrackFailed(track, error)
                currentStats.audioFailure(error)
            },
        )
        onStatsChanged(currentStats)
    }
    if (isActive()) {
        currentStats = currentStats.finished()
        onStatsChanged(currentStats)
    }
    return currentStats
}

const val DefaultAudioPrefetchDepth = 10
const val MaxAudioPrefetchDepth = 25
