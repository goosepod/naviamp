package app.naviamp.presentation

import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NaviampVehiclePlaybackLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NaviampVehiclePlaybackButton(
    val action: NaviampExternalPlaybackAction, val title: String, val selected: Boolean, val enabled: Boolean,
)
data class NaviampVehiclePlaybackAlert(val id: Long, val title: String, val dismiss: String)
data class NaviampVehiclePlaybackState(
    val revision: Long = 0, val buttons: List<NaviampVehiclePlaybackButton> = emptyList(),
    val alert: NaviampVehiclePlaybackAlert? = null,
)

/** Common capability, request lifetime, acknowledgement and error policy for vehicle controls. */
class NaviampVehiclePlaybackController internal constructor(
    scope: CoroutineScope,
    private val product: StateFlow<NaviampCoreState>,
    private val bridge: NaviampCoreExternalPlaybackBridge,
    private val execute: suspend (NaviampCoreCommand) -> NaviampCoreCommandResult,
    private val labels: NaviampVehiclePlaybackLabels,
    private val timeoutMillis: Long = 30_000,
) {
    private val session = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var identity = identity()
    private var fingerprint: Any? = null
    private var revision = 0L
    private var serial = 0L
    private var request: Job? = null
    private var busy = false
    private var closed = false
    private var alert: NaviampVehiclePlaybackAlert? = null
    private val mutableState = MutableStateFlow(project())
    val state: StateFlow<NaviampVehiclePlaybackState> = mutableState.asStateFlow()

    init {
        session.launch {
            // Excludes progress, visualizer frames and volume; these do not repaint vehicle controls.
            product.map { listOf(identity(), it.shell.nowPlaying?.let { playing ->
                listOf(playing.canStartRadio, playing.canFavorite, playing.favoriteActive,
                    playing.shuffleEnabled, playing.shuffleActive, playing.canRepeat, playing.repeatMode, playing.isLive)
            }) }.distinctUntilChanged().collect { publish() }
        }
    }

    private fun identity() = listOf(product.value.vehicleSourceKey(), product.value.shell.nowPlaying?.id,
        product.value.playbackSelectionGeneration)

    private fun project(): NaviampVehiclePlaybackState {
        val current = identity()
        if (current != identity) {
            identity = current
            serial++
            request?.cancel()
            busy = false
            alert = null
        }
        val snapshot = bridge.snapshot()
        val buttons = if (closed || snapshot.current == null || snapshot.isLive) emptyList() else snapshot.customActions.map { action ->
            NaviampVehiclePlaybackButton(action, when (action) {
                NaviampExternalPlaybackAction.StartRadio -> labels.radio
                NaviampExternalPlaybackAction.Favorite -> labels.favorite
                NaviampExternalPlaybackAction.Unfavorite -> labels.unfavorite
                NaviampExternalPlaybackAction.ShuffleOff, NaviampExternalPlaybackAction.ShuffleOn -> labels.shuffle
                NaviampExternalPlaybackAction.RepeatOff -> labels.repeatOff
                NaviampExternalPlaybackAction.RepeatQueue -> labels.repeatAll
                NaviampExternalPlaybackAction.RepeatTrack -> labels.repeatOne
            }, action in setOf(NaviampExternalPlaybackAction.Unfavorite, NaviampExternalPlaybackAction.ShuffleOn,
                NaviampExternalPlaybackAction.RepeatQueue, NaviampExternalPlaybackAction.RepeatTrack), !busy)
        }
        val next = listOf(identity, buttons, alert)
        if (fingerprint != next) { fingerprint = next; revision++ }
        return NaviampVehiclePlaybackState(revision, buttons, alert)
    }

    private fun publish() { mutableState.value = project() }

    fun select(action: NaviampExternalPlaybackAction, acceptedRevision: Long) {
        publish()
        if (closed || busy || acceptedRevision != state.value.revision || state.value.buttons.none { it.action == action && it.enabled }) return
        val command = bridge.customActionCommand(action) ?: return
        val original = bridge.snapshot()
        val acceptedIdentity = identity
        val acceptedSerial = ++serial
        alert = null
        busy = true
        publish()
        request = session.launch {
            var applied = false
            try {
                applied = withTimeoutOrNull(timeoutMillis) {
                    val result = execute(command)
                    ensureActive()
                    if (result is NaviampCoreCommandResult.PlaybackActionApplied && !result.applied) false
                    else if (action == NaviampExternalPlaybackAction.StartRadio) result is NaviampCoreCommandResult.PlaybackActionApplied && result.applied
                    else {
                        product.first {
                            val current = bridge.snapshot()
                            when (action) {
                                NaviampExternalPlaybackAction.Favorite, NaviampExternalPlaybackAction.Unfavorite -> current.favorite != original.favorite
                                NaviampExternalPlaybackAction.ShuffleOff, NaviampExternalPlaybackAction.ShuffleOn -> current.shuffleActive != original.shuffleActive
                                else -> current.repeatMode == when (original.repeatMode) {
                                    NaviampRepeatMode.Off -> NaviampRepeatMode.Queue
                                    NaviampRepeatMode.Queue -> NaviampRepeatMode.Track
                                    NaviampRepeatMode.Track -> NaviampRepeatMode.Off
                                }
                            }
                        }
                        true
                    }
                } ?: false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The common failure projection deliberately excludes provider diagnostics. */ }
            finally {
                if (!closed && acceptedSerial == serial && acceptedIdentity == identity()) {
                    busy = false
                    if (!applied) alert = NaviampVehiclePlaybackAlert(acceptedSerial, labels.failed, labels.dismiss)
                    publish()
                }
            }
        }
    }

    fun dismissAlert(id: Long) {
        if (alert?.id != id) return
        alert = null
        publish()
    }

    fun close() {
        if (closed) return
        closed = true
        serial++
        alert = null
        session.cancel()
        publish()
    }
}
