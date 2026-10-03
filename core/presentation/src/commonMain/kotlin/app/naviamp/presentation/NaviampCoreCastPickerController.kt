package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.ui.NaviampCastPickerProblemUi
import app.naviamp.ui.NaviampCastPickerTargetUi
import app.naviamp.ui.NaviampCastPickerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared scan/picker lifetime. Dismissing the picker never tears down active receiver playback. */
internal class NaviampCoreCastPickerController(
    private val scope: CoroutineScope,
    private val discovery: NaviampCastDiscoveryController,
    private val sessions: NaviampCastSessionController,
    private val outputs: NaviampPlaybackOutputSelectionController,
) {
    private val mutableState = MutableStateFlow(NaviampCastPickerUi())
    val state: StateFlow<NaviampCastPickerUi> = mutableState.asStateFlow()
    private var scan: Job? = null
    private var expiry: Job? = null
    private var selection: Job? = null
    private var revision = 0L
    private val output = scope.launch {
        outputs.state.collect { value ->
            val cast = (value as? NaviampPlaybackOutputSelection.Remote)?.takeIf { it.target.kind == NaviampRemoteOutputKind.Cast }
            mutableState.value = state.value.copy(selectedTargetId = cast?.target?.id)
        }
    }

    fun show() {
        if (state.value.visible) return
        mutableState.value = state.value.copy(visible = true, problem = null)
        scan = scope.launch {
            discovery.state.collect { discovered ->
                mutableState.value = state.value.copy(
                    targets = discovered.targets.map { NaviampCastPickerTargetUi(it.target.id, it.target.displayName) },
                    problem = if (discovered.problem != null) NaviampCastPickerProblemUi.Discovery else state.value.problem,
                )
            }
        }
        discovery.start()
        expiry = scope.launch {
            var refreshTick = 0
            while (true) {
                delay(1_000)
                discovery.refreshExpiry()
                if (++refreshTick % 30 == 0) discovery.refresh()
            }
        }
    }

    fun dismiss() {
        scan?.cancel(); scan = null
        expiry?.cancel(); expiry = null
        discovery.stop()
        mutableState.value = state.value.copy(visible = false, targets = emptyList())
    }

    fun retry() {
        dismiss()
        show()
    }

    fun select(id: String) {
        if (!state.value.visible || selection?.isActive == true) return
        val target = discovery.target(id)
        if (target == null) { mutableState.value = state.value.copy(problem = NaviampCastPickerProblemUi.TargetLost); return }
        val current = ++revision
        mutableState.value = state.value.copy(connectingTargetId = id, problem = null)
        selection = scope.launch {
            try {
                val accepted = sessions.selectTarget(target)
                if (current != revision) return@launch
                mutableState.value = state.value.copy(connectingTargetId = null,
                    problem = if (accepted) null else NaviampCastPickerProblemUi.Connection)
                if (accepted) dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun selectLocal() {
        ++revision
        selection?.cancel(); selection = null
        sessions.selectLocal()
        mutableState.value = state.value.copy(connectingTargetId = null, problem = null)
        dismiss()
    }

    fun close() {
        ++revision
        selection?.cancel()
        output.cancel()
        dismiss()
    }
}
