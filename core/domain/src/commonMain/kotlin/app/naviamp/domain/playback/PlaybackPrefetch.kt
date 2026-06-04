package app.naviamp.domain.playback

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

const val DefaultAudioPrefetchDepth = 10
const val MaxAudioPrefetchDepth = 25
