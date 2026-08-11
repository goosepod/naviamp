package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import kotlinx.coroutines.CancellationException

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
    val items: List<AudioPrefetchItem>,
    val stats: AudioPrefetchStats,
) {
    val tracks: List<Track>
        get() = items.map(AudioPrefetchItem::track)
}

data class AudioPrefetchKey(
    val sourceId: String,
    val trackId: TrackId,
    val quality: StreamQuality,
)

data class AudioPrefetchItem(
    val track: Track,
    val key: AudioPrefetchKey,
    val cacheAudio: Boolean,
    val prepareSidecars: Boolean,
)

class AudioPrefetchCompletionLedger(
    private val capacity: Int = DefaultAudioPrefetchCompletionCapacity,
) {
    private val orderedKeys = ArrayDeque<AudioPrefetchKey>()
    private val keys = mutableSetOf<AudioPrefetchKey>()

    fun contains(key: AudioPrefetchKey): Boolean = key in keys

    fun record(key: AudioPrefetchKey) {
        if (capacity <= 0 || !keys.add(key)) return
        orderedKeys.addLast(key)
        while (orderedKeys.size > capacity) {
            keys.remove(orderedKeys.removeFirst())
        }
    }

    fun clear() {
        orderedKeys.clear()
        keys.clear()
    }
}

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

fun AudioPrefetchStats.audioSuccessWithoutSidecar(): AudioPrefetchStats =
    copy(completed = completed + 1)

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
    completedAudio: AudioPrefetchCompletionLedger? = null,
    completedSidecars: AudioPrefetchCompletionLedger? = null,
    sidecarDepth: Int = DefaultAudioSidecarPrefetchDepth,
    includeCurrentTrack: Boolean = false,
    maximumItemsPerRun: Int = DefaultAudioPrefetchBatchSize,
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
    val normalizedSidecarDepth = sidecarDepth.coerceIn(0, stats.configuredDepth)
    val items = tracks.mapIndexedNotNull { index, track ->
        val key = AudioPrefetchKey(activeSourceId, track.id, activeQuality)
        val cacheAudio = completedAudio?.contains(key) != true
        val prepareSidecars = index < normalizedSidecarDepth && completedSidecars?.contains(key) != true
        if (!cacheAudio && !prepareSidecars) {
            null
        } else {
            AudioPrefetchItem(
                track = track,
                key = key,
                cacheAudio = cacheAudio,
                prepareSidecars = prepareSidecars,
            )
        }
    }.take(maximumItemsPerRun.coerceAtLeast(0))
    if (items.isEmpty()) return null
    return AudioPrefetchWork(
        sourceId = activeSourceId,
        provider = activeProvider,
        quality = activeQuality,
        items = items,
        stats = stats,
    )
}

suspend fun <CachedAudio> runAudioPrefetch(
    stats: AudioPrefetchStats,
    items: List<AudioPrefetchItem>,
    isActive: () -> Boolean,
    cacheAudio: suspend (Track) -> CachedAudio?,
    warmCoverArt: suspend (Track) -> Unit = {},
    prepareSidecars: suspend (Track, CachedAudio?) -> PlaybackSidecarPrepResult = { _, _ ->
        PlaybackSidecarPrepResult()
    },
    onTrackCached: suspend (Track, CachedAudio?) -> Unit = { _, _ -> },
    onTrackFailed: suspend (Track, Throwable) -> Unit = { _, _ -> },
    onAudioCached: (AudioPrefetchKey) -> Unit = {},
    onSidecarsPrepared: (AudioPrefetchKey) -> Unit = {},
    onStatsChanged: (AudioPrefetchStats) -> Unit = {},
): AudioPrefetchStats {
    var currentStats = stats.started(items.size)
    onStatsChanged(currentStats)
    for (item in items) {
        if (!isActive()) break
        var sidecarResult: PlaybackSidecarPrepResult? = null
        val result = runCatching {
            val cachedAudio = if (item.cacheAudio) {
                cacheAudio(item.track).also { onAudioCached(item.key) }
            } else {
                null
            }
            runCatching {
                warmCoverArt(item.track)
            }
            if (item.prepareSidecars) {
                val prepared = prepareSidecars(item.track, cachedAudio)
                sidecarResult = prepared
                if (prepared.successful) onSidecarsPrepared(item.key)
            }
            cachedAudio
        }
        currentStats = result.fold(
            onSuccess = { cachedAudio ->
                onTrackCached(item.track, cachedAudio)
                sidecarResult?.let(currentStats::audioSuccess) ?: currentStats.audioSuccessWithoutSidecar()
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                onTrackFailed(item.track, error)
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

const val DefaultAudioPrefetchBatchSize = 2
const val MaxAudioPrefetchDepth = 25
const val DefaultAudioSidecarPrefetchDepth = 1
const val DefaultAudioPrefetchCompletionCapacity = 64
