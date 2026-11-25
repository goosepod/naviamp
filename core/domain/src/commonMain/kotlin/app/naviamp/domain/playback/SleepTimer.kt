package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.math.pow

enum class SleepTimerTarget(
    val label: String,
) {
    Duration("duration"),
    TrackEnd("current track"),
    AlbumEnd("current album"),
    QueueEnd("queue end"),
}

data class SleepTimerState(
    val target: SleepTimerTarget,
    val startedAtEpochMillis: Long,
    val durationMillis: Long? = null,
    val trackId: String? = null,
    val albumId: String? = null,
    /** The final part of a duration timer over which the volume falls to silence. */
    val fadeMillis: Long = 0L,
    /** Playing time already spent inside the fade window. */
    val fadePlayedMillis: Long = 0L,
    /** Set while playback is running; playing time since then has not been added yet. */
    val playingSinceEpochMillis: Long? = null,
    /** A faded ending pauses, so resuming continues where the listener fell asleep. */
    val pauseAtEnd: Boolean = false,
) {
    val isDuration: Boolean
        get() = target == SleepTimerTarget.Duration

    val fadeStartEpochMillis: Long?
        get() = durationMillis?.takeIf { fadeMillis > 0L }?.let { startedAtEpochMillis + it - fadeMillis }
}

val SleepTimerFadeMinuteOptions: List<Int> = listOf(0, 1, 3, 5, 10)

fun normalizedSleepTimerFadeMinutes(minutes: Int): Int =
    SleepTimerFadeMinuteOptions.last { it <= minutes.coerceAtLeast(0) }

/** The fade advances only while playback runs, so pausing freezes the level and the deadline. */
fun SleepTimerState.withPlaybackActive(active: Boolean, nowEpochMillis: Long): SleepTimerState {
    if (fadeMillis <= 0L) return this
    if (active && playingSinceEpochMillis != null) return this
    return copy(
        fadePlayedMillis = fadePlayedMillisAt(nowEpochMillis),
        playingSinceEpochMillis = nowEpochMillis.takeIf { active },
    )
}

/** Keeps the time left but plays it at full volume, for a user who has adjusted the volume. */
fun SleepTimerState.withoutFade(nowEpochMillis: Long): SleepTimerState {
    if (fadeMillis <= 0L) return this
    return copy(
        startedAtEpochMillis = nowEpochMillis,
        durationMillis = sleepTimerRemainingMillis(this, nowEpochMillis),
        fadeMillis = 0L,
        fadePlayedMillis = 0L,
        playingSinceEpochMillis = null,
    )
}

fun sleepTimerFadeProgress(timer: SleepTimerState, nowEpochMillis: Long): Float {
    if (timer.fadeMillis <= 0L) return 0f
    return (timer.fadePlayedMillisAt(nowEpochMillis).toFloat() / timer.fadeMillis).coerceIn(0f, 1f)
}

fun sleepTimerFadeVolumeFactor(timer: SleepTimerState, nowEpochMillis: Long): Float =
    sleepTimerFadeVolumeFactor(sleepTimerFadeProgress(timer, nowEpochMillis))

/** Perceived loudness follows amplitude^0.6, so this amplitude makes loudness fall linearly. */
fun sleepTimerFadeVolumeFactor(progress: Float): Float =
    (1.0 - progress.coerceIn(0f, 1f)).pow(1.0 / 0.6).toFloat()

private fun SleepTimerState.fadePlayedMillisAt(nowEpochMillis: Long): Long {
    val fadeStart = fadeStartEpochMillis ?: return 0L
    val running = playingSinceEpochMillis
        ?.let { (nowEpochMillis - maxOf(it, fadeStart)).coerceAtLeast(0L) }
        ?: 0L
    return (fadePlayedMillis + running).coerceAtMost(fadeMillis)
}

data class SleepTimerPlaybackSnapshot(
    val currentTrackId: String?,
    val currentAlbumId: String?,
    val nextTrackAlbumId: String? = null,
    val positionSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val playbackEnded: Boolean = false,
)

sealed interface SleepTimerRequest {
    data class DurationMinutes(val minutes: Int) : SleepTimerRequest
    data object TrackEnd : SleepTimerRequest
    data object AlbumEnd : SleepTimerRequest
    data object QueueEnd : SleepTimerRequest
}

data class SleepTimerSelection(
    val timer: SleepTimerState,
    val nowEpochMillis: Long,
    val status: String,
)

fun sleepTimerState(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    snapshot: SleepTimerPlaybackSnapshot,
    queueLastTrackId: String?,
    fadeMinutes: Int = 0,
    playing: Boolean = false,
): SleepTimerState =
    when (request) {
        is SleepTimerRequest.DurationMinutes -> {
            val durationMillis = request.minutes.coerceAtLeast(1) * 60_000L
            val fadeMillis = (normalizedSleepTimerFadeMinutes(fadeMinutes) * 60_000L).coerceAtMost(durationMillis)
            SleepTimerState(
                target = SleepTimerTarget.Duration,
                startedAtEpochMillis = nowEpochMillis,
                durationMillis = durationMillis,
                trackId = snapshot.currentTrackId,
                albumId = snapshot.currentAlbumId,
                fadeMillis = fadeMillis,
                playingSinceEpochMillis = nowEpochMillis.takeIf { playing && fadeMillis > 0L },
                pauseAtEnd = fadeMillis > 0L,
            )
        }
        SleepTimerRequest.TrackEnd -> SleepTimerState(
            target = SleepTimerTarget.TrackEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
        SleepTimerRequest.AlbumEnd -> SleepTimerState(
            target = SleepTimerTarget.AlbumEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
        SleepTimerRequest.QueueEnd -> SleepTimerState(
            target = SleepTimerTarget.QueueEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = queueLastTrackId ?: snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
    }

fun sleepTimerStateForPlayback(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
    fadeMinutes: Int = 0,
): SleepTimerState =
    sleepTimerState(
        request = request,
        nowEpochMillis = nowEpochMillis,
        snapshot = sleepTimerPlaybackSnapshot(
            nowPlaying = nowPlaying,
            playbackQueue = playbackQueue,
            playbackProgress = playbackProgress,
            playbackState = playbackState,
        ),
        queueLastTrackId = playbackQueue.tracks.lastOrNull()?.id?.value,
        fadeMinutes = fadeMinutes,
        playing = playbackState == PlaybackState.Playing,
    )

fun sleepTimerSelection(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
    fadeMinutes: Int = 0,
): SleepTimerSelection {
    val timer = sleepTimerStateForPlayback(
        request = request,
        nowEpochMillis = nowEpochMillis,
        nowPlaying = nowPlaying,
        playbackQueue = playbackQueue,
        playbackProgress = playbackProgress,
        playbackState = playbackState,
        fadeMinutes = fadeMinutes,
    )
    return SleepTimerSelection(
        timer = timer,
        nowEpochMillis = nowEpochMillis,
        status = sleepTimerDisplayLabel(timer, nowEpochMillis),
    )
}

fun sleepTimerPlaybackSnapshot(
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): SleepTimerPlaybackSnapshot {
    val queueIndex = playbackQueue.currentIndex
    val nextTrack = playbackQueue.tracks.getOrNull(queueIndex + 1)
    return SleepTimerPlaybackSnapshot(
        currentTrackId = nowPlaying?.id?.value,
        currentAlbumId = nowPlaying?.albumId?.value,
        nextTrackAlbumId = nextTrack?.albumId?.value,
        positionSeconds = playbackProgress.positionSeconds,
        durationSeconds = playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble(),
        playbackEnded = playbackState == PlaybackState.Finished ||
            playbackState == PlaybackState.Stopped ||
            playbackState == PlaybackState.Idle,
    )
}

fun sleepTimerRemainingMillis(timer: SleepTimerState, nowEpochMillis: Long): Long? {
    val duration = timer.durationMillis ?: return null
    val fadeStart = timer.fadeStartEpochMillis
        ?: return (timer.startedAtEpochMillis + duration - nowEpochMillis).coerceAtLeast(0L)
    return (fadeStart - nowEpochMillis).coerceAtLeast(0L) + timer.fadeMillis - timer.fadePlayedMillisAt(nowEpochMillis)
}

fun sleepTimerDisplayLabel(timer: SleepTimerState, nowEpochMillis: Long): String =
    when (timer.target) {
        SleepTimerTarget.Duration -> sleepTimerRemainingMillis(timer, nowEpochMillis)
            ?.let { "Sleep in ${sleepTimerDurationLabel(it)}" }
            ?: "Sleep timer"
        SleepTimerTarget.TrackEnd -> "Sleep after track"
        SleepTimerTarget.AlbumEnd -> "Sleep after album"
        SleepTimerTarget.QueueEnd -> "Sleep after queue"
    }

fun sleepTimerDurationLabel(millis: Long): String {
    val totalSeconds = ((millis + 999L) / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    } else {
        "${seconds}s"
    }
}

fun shouldExpireSleepTimer(
    timer: SleepTimerState?,
    nowEpochMillis: Long,
    snapshot: SleepTimerPlaybackSnapshot,
): Boolean = timer?.isDuration == true && sleepTimerRemainingMillis(timer, nowEpochMillis) == 0L

/**
 * Whether the current queue item is the last one an end-of-track, album or queue timer plays.
 * Ending at the queue boundary, rather than near the track's reported duration, stops at the
 * track's real end and ignores the Finished state between non-gapless tracks of one album.
 */
fun sleepTimerEndsAfterCurrentTrack(
    timer: SleepTimerState?,
    queue: PlaybackQueue,
    repeatMode: RepeatMode = RepeatMode.Off,
): Boolean {
    val current = queue.current ?: return false
    return when (timer?.target) {
        null, SleepTimerTarget.Duration -> false
        SleepTimerTarget.TrackEnd -> true
        SleepTimerTarget.AlbumEnd -> {
            val next = queue.tracks.getOrNull(queue.currentIndex + 1)
            current.albumId == null || next?.albumId != current.albumId
        }
        SleepTimerTarget.QueueEnd -> queue.currentIndex >= queue.tracks.lastIndex
    }
}

class SleepTimerController(
    private val nowPlaying: () -> Track?,
    private val playbackQueue: () -> PlaybackQueue,
    private val playbackProgress: () -> PlaybackProgress,
    private val playbackState: () -> PlaybackState,
    private val setSleepTimer: (SleepTimerState?) -> Unit,
    private val setSleepTimerNowEpochMillis: (Long) -> Unit,
    private val setStatus: (String) -> Unit,
    private val stopPlayback: () -> Unit,
    private val nowEpochMillis: () -> Long,
) {
    fun snapshot(): SleepTimerPlaybackSnapshot =
        sleepTimerPlaybackSnapshot(
            nowPlaying = nowPlaying(),
            playbackQueue = playbackQueue(),
            playbackProgress = playbackProgress(),
            playbackState = playbackState(),
        )

    fun select(request: SleepTimerRequest) {
        val nowMillis = nowEpochMillis()
        val selection = sleepTimerSelection(
            request = request,
            nowEpochMillis = nowMillis,
            nowPlaying = nowPlaying(),
            playbackQueue = playbackQueue(),
            playbackProgress = playbackProgress(),
            playbackState = playbackState(),
        )
        setSleepTimer(selection.timer)
        setSleepTimerNowEpochMillis(selection.nowEpochMillis)
        setStatus(selection.status)
    }

    fun cancel() {
        setSleepTimer(null)
        setStatus("Sleep timer canceled.")
    }

    fun tick(nowMillis: Long) {
        setSleepTimerNowEpochMillis(nowMillis)
    }

    fun expire() {
        stopPlayback()
        setSleepTimer(null)
        setStatus("Sleep timer stopped playback.")
    }
}
