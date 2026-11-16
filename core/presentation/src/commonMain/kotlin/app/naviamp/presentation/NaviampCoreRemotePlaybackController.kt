package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueFinishedCommand
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One queue/relay owner for receiver protocols. The existing queue coordinator remains canonical. */
internal class NaviampCoreRemotePlaybackController(
    private val scope: CoroutineScope,
    private val session: NaviampCoreRemoteSession,
    private val outputs: NaviampPlaybackOutputSelectionController,
    private val endpoint: NaviampCastMediaEndpointController,
    private val providers: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val local: NaviampCorePlaybackEffectPort,
    private val publishNowPlaying: () -> Unit,
) : NaviampCoreRemotePlaybackRoute {
    override val outputKind get() = session.kind
    override val supportsVolume get() = session.supportsVolume
    override val playbackQuality get() = active?.plan?.quality
    override val suppressLocalObservations get() = active != null
    private class Snapshot(val queue: PlaybackQueue, val repeat: RepeatMode) {
        // Progress normally reuses the queue instance. Cosmetic metadata must not interrupt audio.
        override fun equals(other: Any?): Boolean {
            if (other !is Snapshot || repeat != other.repeat) return false
            if (queue === other.queue) return true
            return queue.currentIndex == other.queue.currentIndex && queue.playNextCount == other.queue.playNextCount &&
                queue.tracks.size == other.queue.tracks.size && queue.tracks.indices.all { queue.tracks[it].id == other.queue.tracks[it].id }
        }
        override fun hashCode() = 31 * (31 * (31 * repeat.hashCode() + queue.currentIndex) + queue.playNextCount) + queue.tracks.fold(1) { hash, track -> 31 * hash + track.id.hashCode() }
    }
    private data class Slot(val media: NaviampCoreRemoteMedia, val plan: NaviampCoreRemoteMediaPlan,
        val source: String, val leases: List<String>, val occurrence: NaviampRemoteQueueOccurrence, val snapshot: Snapshot)
    private val operations = Mutex()
    private val handoff = NaviampRemotePreparedHandoff()
    private var active: Slot? = null
    private var prepared: Slot? = null
    private var previousLeases = emptyList<String>()
    private var requestRevision = 0L
    private var prepareRevision = 0L
    private var queueRevision = 0L
    private var occurrenceId = 0L
    private var remoteWasPlaying = false
    private var explicitlyStopped = false
    private var finishedUrl: String? = null
    private var loadJob: Job? = null
    private var prepareJob: Job? = null
    private var outputJob: Job? = null
    private var statusJob: Job? = null
    private var queueJob: Job? = null
    private var closed = false
    private var stopOnRelease = true
    private var statusFloor = 0L
    val canSelect: Boolean get() = !closed

    private fun snapshot() = Snapshot(playback.state.value.queue, playback.state.value.repeatMode)
    private fun selected() = (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.takeIf { it.target.kind == outputKind }
    private fun current(selection: Long, revision: Long, source: String, expected: Snapshot) =
        session.connectedSelection() == selection && requestRevision == revision && providers.current()?.cacheNamespace == source && snapshot() == expected

    fun start() {
        if (closed || outputJob != null || !session.start()) return
        outputJob = scope.launch {
            outputs.state.collect { output ->
                val own = (output as? NaviampPlaybackOutputSelection.Remote)?.takeIf { it.target.kind == outputKind }
                if (own?.phase == NaviampRemoteOutputPhase.Connected && !own.playbackAuthorityActive &&
                    loadJob?.isActive != true && (active == null || !session.observedAuthority) && playback.state.value.queue.current != null) loadCurrent()
                if (own?.phase in setOf(NaviampRemoteOutputPhase.Unavailable, NaviampRemoteOutputPhase.Incompatible) && (session.observedAuthority || active != null)) {
                    playback.updatePlaybackState(PlaybackState.Paused); publishNowPlaying()
                    if (session.observedAuthority && (active != null || loadJob?.isActive == true)) release(false)
                }
                if (own == null && (active != null || loadJob?.isActive == true)) release(output == NaviampPlaybackOutputSelection.Local)
            }
        }
        statusJob = scope.launch { session.status.collect { if (it != null) operations.withLock { receiverStatus(it) } } }
        queueJob = scope.launch {
            playback.state.map { Snapshot(it.queue, it.repeatMode) }.distinctUntilChanged().collect {
                if (active != null && it != active?.snapshot && session.supportsNext) scheduleNext()
            }
        }
    }

    override fun loadCurrent(positionSeconds: Double?): Boolean {
        if (closed) return false
        val selection = session.connectedSelection() ?: return false
        val expected = snapshot()
        val track = expected.queue.current ?: return false
        val provider = providers.current() ?: return false
        val plan = session.plan(track, provider) ?: run { if (!session.returnLocalOnFailure) fail(selection, incompatible = true); return false }
        val revision = ++requestRevision
        ++prepareRevision
        loadJob?.cancel(); prepareJob?.cancel()
        val wasPlaying = playback.state.value.playbackState in setOf(PlaybackState.Playing, PlaybackState.Loading) || remoteWasPlaying
        val position = (positionSeconds ?: playback.state.value.progress.positionSeconds ?: 0.0).coerceAtLeast(0.0)
        loadJob = scope.launch {
            operations.withLock {
                val issued = mutableListOf<String>()
                var retained = false
                try {
                    if (!current(selection, revision, provider.cacheNamespace, expected)) return@withLock
                    if (!clearNext(selection)) { fail(selection); return@withLock }
                    previousLeases.forEach { endpoint.revoke(it) }; previousLeases = emptyList()
                    endpoint.start()
                    val slot = issue(track, plan, provider.cacheNamespace, expected, position, issued)
                    if (!current(selection, revision, provider.cacheNamespace, expected)) return@withLock
                    if (!withTimeoutOrNull(15_000) { session.load(selection, slot.media, plan) }.orFalse()) { fail(selection); return@withLock }
                    if (!current(selection, revision, provider.cacheNamespace, expected)) return@withLock
                    val old = active
                    active = slot; retained = true
                    statusFloor = session.observationRevision
                    handoff.reset(selection, NaviampRemoteQueuedMedia(slot.occurrence, slot.media.url))
                    if (session.observedAuthority) previousLeases = old?.leases.orEmpty() else old?.leases?.forEach { endpoint.revoke(it) }
                    if (!current(selection, revision, provider.cacheNamespace, expected) || active?.media?.url != slot.media.url) return@withLock
                    finishedUrl = null; explicitlyStopped = false; remoteWasPlaying = wasPlaying
                    if (!session.observedAuthority) outputs.activatePlaybackAuthority(selection)
                    local.stop(); local.setVisualizerFramesEnabled(false)
                    playback.replace(playback.state.value.copy(playbackState = if (wasPlaying) PlaybackState.Loading else PlaybackState.Paused,
                        progress = PlaybackProgress(position, track.durationSeconds?.toDouble())))
                    publishNowPlaying()
                    if (wasPlaying && !withTimeoutOrNull(8_000) { session.command(selection, NaviampCoreRemoteCommand.Play) }.orFalse()) {
                        playback.updatePlaybackState(PlaybackState.Paused); publishNowPlaying()
                        if (session.observedAuthority) fail(selection)
                    }
                    statusFloor = session.observationRevision
                    scheduleNext()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (revision == requestRevision) fail(selection) }
                finally { if (!retained) withContext(NonCancellable) { issued.forEach { endpoint.revoke(it) } } }
            }
        }
        return true
    }

    private suspend fun issue(track: Track, plan: NaviampCoreRemoteMediaPlan, source: String, expected: Snapshot,
        position: Double, issued: MutableList<String>): Slot {
        val url = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, source, track.id.value, plan.quality,
            plan.contentType.takeIf { session.observedAuthority }), session.retainUntilRevoked).also(issued::add)
        val artwork = track.coverArtId?.let { endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Artwork, source, it), session.retainUntilRevoked).also(issued::add) }
        return Slot(NaviampCoreRemoteMedia(url, track, artwork, (position * 1_000).toLong()), plan, source, issued.toList(),
            NaviampRemoteQueueOccurrence(queueRevision, ++occurrenceId), expected)
    }

    private fun scheduleNext() {
        if (!session.supportsNext || explicitlyStopped) return
        val revision = ++prepareRevision
        prepareJob?.cancel()
        prepareJob = scope.launch {
            operations.withLock {
                val selection = session.connectedSelection() ?: return@withLock
                val old = active ?: return@withLock
                val expected = snapshot()
                if (providers.current()?.cacheNamespace != old.source || expected.queue.current?.id != old.media.track.id) { fail(selection); return@withLock }
                if (expected != old.snapshot) {
                    ++queueRevision
                    active = old.copy(snapshot = expected, occurrence = old.occurrence.copy(queueRevision = queueRevision))
                    handoff.reset(selection, NaviampRemoteQueuedMedia(active!!.occurrence, old.media.url))
                }
                val issued = mutableListOf<String>()
                var retained = false
                var submitted = false
                try {
                    if (!clearNext(selection)) { fail(selection); return@withLock }
                    val index = expected.queue.nextIndex(expected.repeat) ?: return@withLock
                    val track = expected.queue.tracks[index]
                    val provider = providers.current()?.takeIf { it.cacheNamespace == old.source } ?: return@withLock
                    val plan = session.plan(track, provider) ?: return@withLock
                    val ticket = handoff.prepareNext(selection) ?: return@withLock
                    val next = issue(track, plan, old.source, expected, 0.0, issued)
                    if (revision != prepareRevision || snapshot() != expected || providers.current()?.cacheNamespace != old.source) return@withLock
                    submitted = true
                    if (!withTimeoutOrNull(8_000) { session.prepareNext(selection, next.media, plan) }.orFalse()) return@withLock
                    if (revision != prepareRevision || snapshot() != expected || providers.current()?.cacheNamespace != old.source || session.connectedSelection() != selection) return@withLock
                    if (handoff.acceptPreparedNext(ticket, NaviampRemoteQueuedMedia(next.occurrence, next.media.url))) { prepared = next; retained = true }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Current playback survives an unsupported/failed next resource. */ }
                finally {
                    if (!retained) withContext(NonCancellable) {
                        if (submitted && !clearRemoteNext(selection)) fail(selection)
                        issued.forEach { endpoint.revoke(it) }
                    }
                }
            }
        }
    }

    private suspend fun clearNext(selection: Long): Boolean {
        val old = prepared ?: return true
        val cleared = clearRemoteNext(selection)
        if (cleared) {
            prepared = null
            old.leases.forEach { endpoint.revoke(it) }
            active?.let { handoff.reset(selection, NaviampRemoteQueuedMedia(it.occurrence, it.media.url)) }
        }
        return cleared
    }

    private suspend fun clearRemoteNext(selection: Long): Boolean = try {
        withTimeoutOrNull(8_000) { session.prepareNext(selection, null, null) }.orFalse()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }

    private suspend fun receiverStatus(status: NaviampCoreRemoteStatus) {
        val selection = session.connectedSelection() ?: return
        val current = active ?: return
        if (selection != status.selectionId || status.revision < statusFloor) return
        if (loadJob?.isActive == true && snapshot() != current.snapshot) return
        if (providers.current()?.cacheNamespace != current.source) { fail(selection); return }
        if (status.failed) { if (status.mediaUrl == current.media.url) fail(selection); return }
        if (session.observedAuthority && !session.supportsNext && status.mediaUrl != current.media.url) { fail(selection, stopReceiver = false); return }
        if (session.supportsNext && status.mediaUrl != null) {
            val next = prepared
            if (status.mediaUrl == next?.media?.url && snapshot() != next.snapshot) { fail(selection); return }
            when (handoff.observe(selection, status.revision, status.mediaUrl, status.state == PlaybackState.Playing)) {
                is NaviampRemoteHandoffObservation.Promoted -> {
                    val promoted = next ?: return
                    val finished = queue.finishCurrentTrack()
                    check(finished.command != PlaybackQueueFinishedCommand.None && finished.queue.current?.id == promoted.media.track.id)
                    prepared = null
                    val canonicalTrack = checkNotNull(finished.queue.current)
                    active = promoted.copy(snapshot = snapshot(), media = promoted.media.copy(track = canonicalTrack))
                    playback.updateCurrentTrack(canonicalTrack)
                    current.leases.forEach { endpoint.revoke(it) }
                    finishedUrl = null
                    scheduleNext()
                }
                NaviampRemoteHandoffObservation.Ignored -> return
                NaviampRemoteHandoffObservation.UnexpectedResource -> { fail(selection, stopReceiver = false); return }
                NaviampRemoteHandoffObservation.Current -> Unit
            }
        }
        if (status.mediaUrl != active?.media?.url) return
        if (session.observedAuthority) outputs.activatePlaybackAuthority(selection)
        if (!outputs.hasRemotePlaybackAuthority()) return
        previousLeases.forEach { endpoint.revoke(it) }; previousLeases = emptyList()
        if (session.connectedSelection() != selection || active?.media?.url != status.mediaUrl || providers.current()?.cacheNamespace != current.source) return
        if (!explicitlyStopped) remoteWasPlaying = when (status.state) {
            PlaybackState.Playing, PlaybackState.Loading -> true
            PlaybackState.Finished -> remoteWasPlaying
            PlaybackState.Stopped -> session.idlePreservesPlaying && remoteWasPlaying
            else -> false
        }
        playback.replace(playback.state.value.copy(playbackState = status.state,
            progress = PlaybackProgress(status.positionMillis.coerceAtLeast(0) / 1_000.0,
                status.durationMillis?.div(1_000.0) ?: playback.state.value.queue.current?.durationSeconds?.toDouble())))
        publishNowPlaying()
        if (status.finished && !explicitlyStopped && finishedUrl != status.mediaUrl) {
            finishedUrl = status.mediaUrl
            val finished = queue.finishCurrentTrack()
            if (finished.command != PlaybackQueueFinishedCommand.None) { playback.updateCurrentTrack(finished.queue.current); loadCurrent(0.0) }
            else remoteWasPlaying = false
        }
    }

    private fun fail(selection: Long, incompatible: Boolean = false, stopReceiver: Boolean = true) {
        if (selected()?.selectionId != selection) return
        if (session.returnLocalOnFailure) { session.selectLocal(); return }
        stopOnRelease = stopReceiver
        if (incompatible) outputs.incompatible(selection) else outputs.unavailable(selection)
        remoteWasPlaying = false; local.stop(); playback.updatePlaybackState(PlaybackState.Paused); publishNowPlaying()
    }
    override fun navigate(command: PlaybackQueueNavigationCommand) { if (command != PlaybackQueueNavigationCommand.None) loadCurrent(0.0) }
    override fun pause() = send(NaviampCoreRemoteCommand.Pause)
    override fun resume() { explicitlyStopped = false; remoteWasPlaying = true; send(NaviampCoreRemoteCommand.Play); scheduleNext() }
    override fun seek(positionSeconds: Double) = send(NaviampCoreRemoteCommand.Seek((positionSeconds.coerceAtLeast(0.0) * 1_000).toLong()))
    override fun stop() { explicitlyStopped = true; remoteWasPlaying = false; ++prepareRevision; prepareJob?.cancel(); send(NaviampCoreRemoteCommand.Stop) }
    override fun setVolume(percent: Int) = send(NaviampCoreRemoteCommand.Volume(percent.coerceIn(0, 100)))
    private fun send(command: NaviampCoreRemoteCommand) {
        scope.launch { operations.withLock {
            val selection = session.connectedSelection() ?: return@withLock
            try {
                if (command == NaviampCoreRemoteCommand.Stop && !clearNext(selection)) { fail(selection); return@withLock }
                if (!withTimeoutOrNull(8_000) { session.command(selection, command) }.orFalse() && session.observedAuthority) fail(selection)
                statusFloor = maxOf(statusFloor, session.observationRevision)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (session.observedAuthority) fail(selection) }
        } }
    }

    fun selectLocal() = session.selectLocal()
    private fun release(resumeLocal: Boolean) {
        val revision = ++requestRevision
        ++prepareRevision; loadJob?.cancel(); prepareJob?.cancel()
        val identity = session.selectionIdentity
        val leases = active?.leases.orEmpty() + prepared?.leases.orEmpty() + previousLeases
        val hadActive = active != null
        val resume = remoteWasPlaying
        val stopReceiver = stopOnRelease
        stopOnRelease = true
        active = null; prepared = null; previousLeases = emptyList(); finishedUrl = null; remoteWasPlaying = false
        fun restoreLocal(canResume: Boolean) {
            if (!resumeLocal || !hadActive || playback.state.value.queue.current == null || outputs.state.value != NaviampPlaybackOutputSelection.Local || closed) return
            local.restoreQueue(playback.state.value.queue, playback.state.value.progress.positionSeconds)
            local.applyRepeatMode(playback.state.value.repeatMode)
            if (resume && canResume) local.startOrRestore() else playback.updatePlaybackState(PlaybackState.Paused)
            publishNowPlaying()
        }
        if (resumeLocal && hadActive && session.observedAuthority) playback.updatePlaybackState(PlaybackState.Paused)
        scope.launch { operations.withLock {
            var disconnected = false
            if (requestRevision == revision && active == null && session.selectionIdentity == identity) {
                try { disconnected = withTimeoutOrNull(8_000) { session.disconnect(stopReceiver); true } == true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Cleanup failure must not restart local playback. */ }
                finally { endpoint.stop() }
            } else leases.forEach { endpoint.revoke(it) }
            if (session.observedAuthority && requestRevision == revision) restoreLocal(disconnected)
        } }
        if (!session.observedAuthority) restoreLocal(true)
        publishNowPlaying()
    }

    suspend fun disconnect() {
        selected()?.let { outputs.unavailable(it.selectionId) }
        ++requestRevision; ++prepareRevision
        loadJob?.cancelAndJoin(); prepareJob?.cancelAndJoin()
        operations.withLock { try { withTimeoutOrNull(8_000) { session.disconnect() } } finally { endpoint.stop(); active = null; prepared = null; previousLeases = emptyList() } }
    }
    /** End a foreground-only relay without automatically starting the local speaker. */
    suspend fun suspendForBackground() {
        if (selected() == null) return
        val hadMedia = active != null || outputs.hasRemotePlaybackAuthority()
        ++requestRevision; ++prepareRevision
        val loading = loadJob.also { it?.cancel() }; loadJob = null
        val preparing = prepareJob.also { it?.cancel() }; prepareJob = null
        remoteWasPlaying = false; explicitlyStopped = true
        active = null; prepared = null; previousLeases = emptyList()
        session.selectLocal()
        if (hadMedia) playback.updatePlaybackState(PlaybackState.Paused)
        publishNowPlaying()
        withContext(NonCancellable) {
            loading?.join(); preparing?.join()
            operations.withLock {
                try { withTimeoutOrNull(8_000) { session.disconnect(true) } }
                finally { endpoint.stop() }
            }
        }
    }
    suspend fun shutdown() {
        if (closed) return
        closed = true
        ++requestRevision; ++prepareRevision
        withContext(NonCancellable) {
            loadJob?.cancelAndJoin(); prepareJob?.cancelAndJoin(); outputJob?.cancelAndJoin(); statusJob?.cancelAndJoin(); queueJob?.cancelAndJoin()
            outputJob = null; statusJob = null; queueJob = null
            try { session.shutdown() } finally { endpoint.stop(); active = null; prepared = null; previousLeases = emptyList() }
        }
    }
    fun close() {
        if (closed) return
        closed = true
        loadJob?.cancel(); prepareJob?.cancel(); outputJob?.cancel(); statusJob?.cancel(); queueJob?.cancel()
        outputJob = null; statusJob = null; queueJob = null
        if (selected() != null) session.selectLocal()
        session.stop()
        scope.launch(NonCancellable, start = CoroutineStart.UNDISPATCHED) {
            operations.withLock {
                try { withTimeoutOrNull(8_000) { session.shutdown() } }
                catch (_: Exception) { /* Receiver cleanup is best effort during synchronous disposal. */ }
                finally { endpoint.stop(); active = null; prepared = null; previousLeases = emptyList() }
            }
        }
    }
}

private fun Boolean?.orFalse() = this == true
