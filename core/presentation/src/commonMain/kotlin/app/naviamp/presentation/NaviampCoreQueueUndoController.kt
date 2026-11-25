package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.ui.NaviampQueueUndoAction
import app.naviamp.ui.NaviampQueueUndoActionRequest
import app.naviamp.ui.NaviampQueueUndoBaseTimeoutMillis
import app.naviamp.ui.NaviampQueueUndoKind
import app.naviamp.ui.NaviampQueueUndoUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Longest window an accessibility-adjusted presentation may request. */
const val NaviampQueueUndoMaxTimeoutMillis = 300_000L

/**
 * Single-level, in-memory Undo for destructive queue edits made through this device's shared UI.
 *
 * One entry is kept: the playback state before the edit and the queue the edit produced. The entry
 * is discarded when the live queue diverges from that result (any newer edit, track transition,
 * Radio or Autoplay refill, handoff or source reset), when it expires, or when it is dismissed or
 * used. Undo is refused once the source changed or playback is no longer local. Nothing is
 * persisted, so a process restart also discards it.
 * Nothing is recorded while playback runs on a remote output or this device is a Connect target,
 * because the edit's author is not looking at this device's Undo affordance.
 */
class NaviampCoreQueueUndoController(
    private val scope: CoroutineScope,
    private val stateStore: NaviampCoreStateStore,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val playbackOutput: () -> NaviampPlaybackOutputSelection = { NaviampPlaybackOutputSelection.Local },
    private val publishNowPlaying: () -> Unit = {},
) : NaviampCoreCommandController {
    /** The pre-edit state, taken before a transaction that may record an Undo. */
    class Capture internal constructor(
        internal val state: NaviampLivePlaybackState,
        internal val sourceId: String?,
        internal val downloadedOnly: Boolean,
    )

    private class Entry(
        val id: Long,
        val kind: NaviampQueueUndoKind,
        val before: NaviampLivePlaybackState,
        var after: PlaybackQueue,
        val sourceId: String?,
    )

    private var entry: Entry? = null
    private var nextId = 0L
    private var expiry: Job? = null
    private var continuing = false

    init {
        playback.observe { live ->
            val pending = entry ?: return@observe
            if (!continuing && live.queue != pending.after) discard()
        }
    }

    fun capture(): Capture? {
        if (!localPlayback()) return null
        return Capture(
            state = playback.state.value,
            sourceId = activeSourceId(),
            downloadedOnly = effects.downloadedOnlyQueue,
        )
    }

    /** Records [kind] when [after] is the live result of an edit that started from [capture]. */
    fun record(capture: Capture?, kind: NaviampQueueUndoKind, after: PlaybackQueue, subject: String? = null) {
        val before = capture?.state ?: return
        if (after == before.queue || playback.state.value.queue != after) return
        if (capture.sourceId != activeSourceId() || !localPlayback() || before.currentStation != null) return
        val eligible = if (kind.keepsCurrent) {
            before.queue.current != null && after.current?.id == before.queue.current?.id
        } else {
            // Restoring re-prepares stream playback, which would silently drop a downloaded-only session.
            before.queue.upNext().isNotEmpty() && !capture.downloadedOnly
        }
        if (!eligible) return
        val id = ++nextId
        entry = Entry(id, kind, before, after, capture.sourceId)
        schedule(id, NaviampQueueUndoBaseTimeoutMillis)
        stateStore.update { it.copy(overlays = it.overlays.copy(queueUndo = NaviampQueueUndoUi(id, kind, subject))) }
    }

    /** Identifies the entry, if any, recorded by the transaction that just ran. */
    fun pendingId(): Long? = entry?.id

    /**
     * Runs a follow-up that belongs to transaction [pendingId], such as the Radio refill after its
     * seed, so the entry follows the refilled queue instead of being invalidated by it.
     */
    fun <T> continueTransaction(pendingId: Long?, block: () -> T): T {
        val pending = entry?.takeIf { pendingId != null && it.id == pendingId && it.after == playback.state.value.queue }
            ?: return block()
        continuing = true
        val result = try { block() } finally { continuing = false }
        if (entry === pending) pending.after = playback.state.value.queue
        return result
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val request = (command as? NaviampCoreCommand.NowPlaying.QueueUndo)?.request
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        handle(request)
        return NaviampCoreImmediateCommandResult.Handled()
    }

    private fun handle(request: NaviampQueueUndoActionRequest) {
        val pending = entry?.takeIf { it.id == request.id } ?: return
        when (request.action) {
            NaviampQueueUndoAction.Undo -> undo(pending)
            NaviampQueueUndoAction.Dismiss -> discard()
            NaviampQueueUndoAction.Presented -> schedule(pending.id, request.timeoutMillis ?: NaviampQueueUndoBaseTimeoutMillis)
            NaviampQueueUndoAction.FocusGained -> { expiry?.cancel(); expiry = null }
            NaviampQueueUndoAction.FocusLost -> schedule(pending.id, NaviampQueueUndoBaseTimeoutMillis)
        }
    }

    private fun undo(pending: Entry) {
        val live = playback.state.value
        val valid = live.queue == pending.after && pending.sourceId == activeSourceId() && localPlayback()
        discard()
        if (!valid) return
        val before = pending.before
        if (pending.kind.keepsCurrent) {
            queue.replaceQueue(before.queue)
            playback.updateShuffledUpNextSnapshot(before.shuffledUpNextSnapshot)
            effects.applyQueue(before.queue, clearPreparedNext = true)
        } else {
            val resume = before.playbackState == PlaybackState.Playing || before.playbackState == PlaybackState.Loading
            // Never stop: hosts end the media session on Stopped/Idle. A paused restore silences the new
            // stream and defers the previous track's start to its captured position until Play.
            if (!resume) {
                effects.prepareForPreview()
                effects.pause()
            }
            playback.replace(
                before.copy(
                    repeatMode = live.repeatMode,
                    pendingSeekPositionSeconds = null,
                    pendingSeekIssuedAtMillis = null,
                    playbackState = if (resume) before.playbackState else PlaybackState.Paused,
                ),
            )
            effects.restoreQueue(before.queue, before.progress.positionSeconds)
            if (resume) effects.startOrRestore()
        }
        publishNowPlaying()
    }

    private fun schedule(id: Long, timeoutMillis: Long) {
        expiry?.cancel()
        val window = timeoutMillis.coerceIn(NaviampQueueUndoBaseTimeoutMillis, NaviampQueueUndoMaxTimeoutMillis)
        expiry = scope.launch {
            delay(window)
            if (entry?.id == id) discard()
        }
    }

    private fun discard() {
        entry = null
        expiry?.cancel()
        expiry = null
        if (stateStore.state.value.overlays.queueUndo != null) {
            stateStore.update { it.copy(overlays = it.overlays.copy(queueUndo = null)) }
        }
    }

    private fun localPlayback(): Boolean {
        val connect = stateStore.state.value.shell.connect
        return playbackOutput() == NaviampPlaybackOutputSelection.Local &&
            !connect.remotePlaybackAuthorityActive && connect.connectedControllerDeviceId == null
    }

    private fun activeSourceId(): String? = stateStore.state.value.shell.connectionSettings.currentSourceId
}

/** Upcoming-queue edits keep the current occurrence playing; replacements restart the previous one. */
private val NaviampQueueUndoKind.keepsCurrent: Boolean
    get() = this != NaviampQueueUndoKind.ReplacedQueue
