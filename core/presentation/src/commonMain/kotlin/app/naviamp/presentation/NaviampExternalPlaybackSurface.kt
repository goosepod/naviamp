package app.naviamp.presentation

import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NaviampExternalPlaybackLabels
import app.naviamp.ui.loadNaviampExternalPlaybackLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

enum class NaviampExternalPlaybackSurfaceAction { Play, Pause, Stop, Previous, Next, Seek, Shuffle, Repeat, Favorite }

sealed interface NaviampExternalPlaybackSurfaceCommand {
    data object Play : NaviampExternalPlaybackSurfaceCommand
    data object Pause : NaviampExternalPlaybackSurfaceCommand
    data object Toggle : NaviampExternalPlaybackSurfaceCommand
    data object Stop : NaviampExternalPlaybackSurfaceCommand
    data object Previous : NaviampExternalPlaybackSurfaceCommand
    data object Next : NaviampExternalPlaybackSurfaceCommand
    data object Favorite : NaviampExternalPlaybackSurfaceCommand
    data class Seek(val seconds: Double) : NaviampExternalPlaybackSurfaceCommand
    data class Shuffle(val active: Boolean) : NaviampExternalPlaybackSurfaceCommand
    data class Repeat(val mode: NaviampRepeatMode) : NaviampExternalPlaybackSurfaceCommand
}

enum class NaviampAudioSessionDeactivationSource { Application, System, Unknown }
enum class NaviampAudioSessionInterruptionReason { OutputDisconnected, Other }
enum class NaviampAudioSessionResumptionRecommendation { Resume, StayPaused, Unknown }

sealed interface NaviampExternalPlaybackAudioEvent {
    data object InterruptionBegan : NaviampExternalPlaybackAudioEvent
    data class InterruptionEnded(val shouldResume: Boolean) : NaviampExternalPlaybackAudioEvent
    data object OutputDisconnected : NaviampExternalPlaybackAudioEvent
    data object SessionBecameActive : NaviampExternalPlaybackAudioEvent
    data class SessionBecameInactive(val source: NaviampAudioSessionDeactivationSource,
        val reason: NaviampAudioSessionInterruptionReason? = null) : NaviampExternalPlaybackAudioEvent
    data class ResumptionRecommended(val recommendation: NaviampAudioSessionResumptionRecommendation) : NaviampExternalPlaybackAudioEvent
}

/** Native resources and type translation only. Core owns requests, publication and lifetime policy. */
interface NaviampExternalPlaybackSurface<Artwork> {
    fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean, audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit)
    fun decodeArtwork(bytes: ByteArray): Artwork?
    fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>, artwork: Artwork?, labels: NaviampExternalPlaybackLabels)
    fun close()
}

fun NaviampExternalPlaybackSnapshot.surfaceActions(): Set<NaviampExternalPlaybackSurfaceAction> = buildSet {
    if (current == null) return@buildSet
    if (canPlayPause) { add(NaviampExternalPlaybackSurfaceAction.Play); add(NaviampExternalPlaybackSurfaceAction.Pause) }
    if (canControl) add(NaviampExternalPlaybackSurfaceAction.Stop)
    if (hasPrevious) add(NaviampExternalPlaybackSurfaceAction.Previous)
    if (hasNext) add(NaviampExternalPlaybackSurfaceAction.Next)
    if (canSeek) add(NaviampExternalPlaybackSurfaceAction.Seek)
    if (canShuffle) add(NaviampExternalPlaybackSurfaceAction.Shuffle)
    if (canRepeat) add(NaviampExternalPlaybackSurfaceAction.Repeat)
    if (canFavorite) add(NaviampExternalPlaybackSurfaceAction.Favorite)
}

/**
 * One session per Core lifetime, shared by native media hosts. Construct/close on the owning scope's
 * dispatcher. OS callbacks may arrive on other threads: intents are validated, queued on that scope
 * and validated again before execution. Closing invalidates all retained callbacks immediately.
 */
class NaviampExternalPlaybackSurfaceSession<Artwork>(
    parentScope: CoroutineScope,
    private val control: NaviampExternalPlaybackControl,
    snapshots: Flow<NaviampExternalPlaybackSnapshot>,
    private val toggleFavorite: () -> Unit,
    private val loadArtwork: suspend (String) -> ByteArray?,
    private val surface: NaviampExternalPlaybackSurface<Artwork>,
    private val onFailure: (Throwable) -> Unit,
    registerClose: (() -> Unit) -> Unit = {},
    private val loadLabels: suspend () -> NaviampExternalPlaybackLabels = ::loadNaviampExternalPlaybackLabels,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val active = MutableStateFlow(true)
    private val planner = NaviampExternalPlaybackPublicationPlanner()
    private val lifecycle = NaviampExternalPlaybackLifecycleCoordinator(
        control::snapshot, control::play, control::pause, control::stop,
    )
    private var artwork: Artwork? = null
    private val artworkController = NaviampExternalPlaybackArtworkController(scope, control::snapshot, loadArtwork, ::updateArtwork)
    private lateinit var labels: NaviampExternalPlaybackLabels

    init {
        try {
            registerClose(::close)
            check(job.isActive) { "Cannot attach a playback surface to a cancelled scope" }
            // Install cancellation cleanup before native registration; even cancellation during
            // attach or before the collector's first dispatch must release native resources.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { close() }
            }
            surface.attach(::command, ::audioEvent)
            scope.launch {
                try { labels = loadLabels(); snapshots.collect(::update) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { fail(failure) }
                finally { close() }
            }
        } catch (failure: Throwable) {
            try { close() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    fun close() {
        if (!active.compareAndSet(true, false)) return
        lifecycle.close()
        artworkController.close()
        artwork = null
        job.cancel()
        planner.reset()
        surface.close()
    }

    private fun accepts(command: NaviampExternalPlaybackSurfaceCommand): Boolean {
        if (!active.value || !job.isActive) return false
        val snapshot = control.snapshot()
        val action = when (command) {
            NaviampExternalPlaybackSurfaceCommand.Play, NaviampExternalPlaybackSurfaceCommand.Toggle -> NaviampExternalPlaybackSurfaceAction.Play
            NaviampExternalPlaybackSurfaceCommand.Pause -> NaviampExternalPlaybackSurfaceAction.Pause
            NaviampExternalPlaybackSurfaceCommand.Stop -> NaviampExternalPlaybackSurfaceAction.Stop
            NaviampExternalPlaybackSurfaceCommand.Previous -> NaviampExternalPlaybackSurfaceAction.Previous
            NaviampExternalPlaybackSurfaceCommand.Next -> NaviampExternalPlaybackSurfaceAction.Next
            NaviampExternalPlaybackSurfaceCommand.Favorite -> NaviampExternalPlaybackSurfaceAction.Favorite
            is NaviampExternalPlaybackSurfaceCommand.Shuffle -> NaviampExternalPlaybackSurfaceAction.Shuffle
            is NaviampExternalPlaybackSurfaceCommand.Repeat -> NaviampExternalPlaybackSurfaceAction.Repeat
            is NaviampExternalPlaybackSurfaceCommand.Seek -> {
                if (!command.seconds.isFinite() || command.seconds < 0 || command.seconds * 1_000.0 >= Long.MAX_VALUE.toDouble() ||
                    snapshot.durationMillis?.let { command.seconds * 1_000.0 > it } == true) return false
                NaviampExternalPlaybackSurfaceAction.Seek
            }
        }
        return action in snapshot.surfaceActions()
    }

    private fun command(command: NaviampExternalPlaybackSurfaceCommand): Boolean {
        if (!accepts(command)) return false
        scope.launch {
            if (!accepts(command)) return@launch
            try {
                when (command) {
                    NaviampExternalPlaybackSurfaceCommand.Play, NaviampExternalPlaybackSurfaceCommand.Pause,
                    NaviampExternalPlaybackSurfaceCommand.Toggle, NaviampExternalPlaybackSurfaceCommand.Stop,
                    NaviampExternalPlaybackSurfaceCommand.Previous, NaviampExternalPlaybackSurfaceCommand.Next -> lifecycle.cancelPendingResume()
                    else -> Unit
                }
                when (command) {
                    NaviampExternalPlaybackSurfaceCommand.Play -> control.play()
                    NaviampExternalPlaybackSurfaceCommand.Pause -> control.pause()
                    NaviampExternalPlaybackSurfaceCommand.Toggle -> control.playPause()
                    NaviampExternalPlaybackSurfaceCommand.Stop -> control.stop()
                    NaviampExternalPlaybackSurfaceCommand.Previous -> control.previous()
                    NaviampExternalPlaybackSurfaceCommand.Next -> control.next()
                    NaviampExternalPlaybackSurfaceCommand.Favorite -> toggleFavorite()
                    is NaviampExternalPlaybackSurfaceCommand.Seek -> control.seekTo((command.seconds * 1_000.0).toLong())
                    is NaviampExternalPlaybackSurfaceCommand.Shuffle -> control.setShuffleActive(command.active)
                    is NaviampExternalPlaybackSurfaceCommand.Repeat -> control.setRepeatMode(command.mode)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(failure) }
        }
        return true
    }

    private fun audioEvent(event: NaviampExternalPlaybackAudioEvent) {
        if (!active.value || !job.isActive) return
        scope.launch {
            if (!active.value) return@launch
            try {
                when (event) {
                    NaviampExternalPlaybackAudioEvent.InterruptionBegan -> lifecycle.interruptionBegan()
                    is NaviampExternalPlaybackAudioEvent.InterruptionEnded -> lifecycle.interruptionEnded(event.shouldResume)
                    NaviampExternalPlaybackAudioEvent.OutputDisconnected -> lifecycle.outputDisconnected()
                    NaviampExternalPlaybackAudioEvent.SessionBecameActive -> Unit // Activation is not permission to autoplay.
                    is NaviampExternalPlaybackAudioEvent.SessionBecameInactive -> lifecycle.sessionBecameInactive(event.source, event.reason)
                    is NaviampExternalPlaybackAudioEvent.ResumptionRecommended -> lifecycle.interruptionEnded(
                        event.recommendation == NaviampAudioSessionResumptionRecommendation.Resume)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(failure) }
        }
    }

    private fun update(snapshot: NaviampExternalPlaybackSnapshot) {
        if (!active.value) return
        lifecycle.onSnapshot(snapshot)
        artworkController.update(snapshot)
        val publication = planner.plan(snapshot)
        if (publication.sessionContent || publication.playbackState) publish(snapshot)
    }

    private fun updateArtwork(bytes: ByteArray?) {
        if (!active.value) return
        if (bytes == null) {
            artwork = null
            return
        }
        val decoded = try { surface.decodeArtwork(bytes) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } ?: return
        if (!active.value) return
        artwork = decoded
        try { publish(control.snapshot()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { fail(failure) }
    }

    private fun publish(snapshot: NaviampExternalPlaybackSnapshot) = surface.publish(snapshot, snapshot.surfaceActions(), artwork, labels)

    private fun fail(failure: Exception) {
        try { close() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
        onFailure(failure)
    }
}

fun <Artwork> NaviampCoreExternalPlaybackBridge.attachSurface(
    scope: CoroutineScope,
    surface: NaviampExternalPlaybackSurface<Artwork>,
    loadArtwork: suspend (String) -> ByteArray?,
    onFailure: (Throwable) -> Unit,
    registerClose: (() -> Unit) -> Unit = {},
): NaviampExternalPlaybackSurfaceSession<Artwork> = NaviampExternalPlaybackSurfaceSession(
    scope, this, snapshots, ::toggleFavorite, loadArtwork, surface, onFailure, registerClose,
)
