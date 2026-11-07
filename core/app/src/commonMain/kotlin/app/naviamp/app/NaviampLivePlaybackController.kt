package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class NaviampLivePlaybackState(
    val currentTrack: Track? = null,
    val currentStation: InternetRadioStation? = null,
    val queue: PlaybackQueue = PlaybackQueue(),
    val progress: PlaybackProgress = PlaybackProgress.Unknown,
    val pendingSeekPositionSeconds: Double? = null,
    val pendingSeekIssuedAtMillis: Long? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffledUpNextSnapshot: List<Track>? = null,
)

/** Shared owner of live, user-visible playback state; it does not execute audio commands. */
@OptIn(ExperimentalAtomicApi::class)
class NaviampLivePlaybackController(
    initialState: NaviampLivePlaybackState = NaviampLivePlaybackState(),
) {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableProgress = MutableStateFlow(initialState.progress)
    private val observers = MutableStateFlow(emptyList<(NaviampLivePlaybackState) -> Unit>())
    private val notifying = AtomicBoolean(false)
    private val notificationPending = AtomicBoolean(false)
    private var notifiedState = initialState

    val state: StateFlow<NaviampLivePlaybackState> = mutableState.asStateFlow()
    /** High-frequency playback clock isolated from the structural playback snapshot. */
    val progress: StateFlow<PlaybackProgress> = mutableProgress.asStateFlow()

    fun replace(state: NaviampLivePlaybackState) {
        mutableState.value = state
        notifyObservers()
    }

    /** Observes canonical state mutations without requiring a host lifecycle coroutine. */
    fun observe(observer: (NaviampLivePlaybackState) -> Unit) {
        observers.update { it + observer }
    }

    fun updateCurrentTrack(track: Track?) = update { current ->
        current.copy(
            currentTrack = track,
            progress = if (current.currentTrack?.id != track?.id) PlaybackProgress.Unknown else current.progress,
            currentStation = current.currentStation?.takeIf { station ->
                track?.id == internetRadioTrackId(station.id)
            },
        )
    }

    fun updateCurrentStation(station: InternetRadioStation?) =
        update { current -> current.copy(currentStation = station) }

    fun updateQueue(queue: PlaybackQueue) = update { current -> current.copy(queue = queue) }

    fun applyQueueChange(
        queue: PlaybackQueue,
        positionSeconds: Double?,
        persist: (PlaybackQueue, Double?) -> Unit,
    ) {
        updateQueue(queue)
        persist(queue, positionSeconds)
    }

    fun updateProgress(progress: PlaybackProgress) = update { current ->
        current.copy(progress = progress.copy(seekGeneration = current.progress.seekGeneration))
    }

    fun updatePendingSeek(
        positionSeconds: Double?,
        issuedAtMillis: Long?,
    ) = update { current ->
        current.copy(
            pendingSeekPositionSeconds = positionSeconds,
            pendingSeekIssuedAtMillis = issuedAtMillis,
        )
    }

    fun applySeekPlan(
        progress: PlaybackProgress,
        pendingPositionSeconds: Double,
        issuedAtMillis: Long,
    ) = update { current ->
        current.copy(
            progress = progress.copy(seekGeneration = current.progress.seekGeneration + 1L),
            pendingSeekPositionSeconds = pendingPositionSeconds,
            pendingSeekIssuedAtMillis = issuedAtMillis,
        )
    }

    fun updatePlaybackState(playbackState: PlaybackState) =
        update { current -> current.copy(playbackState = playbackState) }

    fun applyPlaybackStateChange(
        playbackState: PlaybackState,
        progress: PlaybackProgress = state.value.progress,
        report: (PlaybackState, PlaybackProgress) -> Unit,
    ) {
        updatePlaybackState(playbackState)
        report(playbackState, progress)
    }

    fun updateRepeatMode(repeatMode: RepeatMode) =
        update { current -> current.copy(repeatMode = repeatMode) }

    fun updateShuffledUpNextSnapshot(snapshot: List<Track>?) =
        update { current -> current.copy(shuffledUpNextSnapshot = snapshot) }

    private inline fun update(transform: (NaviampLivePlaybackState) -> NaviampLivePlaybackState) {
        // Native state and clock callbacks can arrive on different threads. Retry field changes
        // against the latest snapshot so a clock cannot restore a stale Loading state.
        mutableState.update(transform)
        notifyObservers()
    }

    private fun notifyObservers() {
        notificationPending.store(true)
        do {
            if (!notifying.compareAndSet(false, true)) return
            try {
                while (notificationPending.exchange(false)) {
                    val updated = mutableState.value
                    if (updated == notifiedState) continue
                    notifiedState = updated
                    mutableProgress.value = updated.progress
                    // Drain reentrant updates after this snapshot reaches every observer. This
                    // prevents an older callback from following its newer nested notification.
                    observers.value.forEach { it(updated) }
                }
            } finally {
                notifying.store(false)
            }
        } while (notificationPending.load())
    }
}
