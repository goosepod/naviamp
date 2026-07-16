package app.naviamp.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NaviampHostLifecycleEvent {
    Start,
    EnterForeground,
    EnterBackground,
    Shutdown,
}

enum class NaviampRuntimePhase {
    Created,
    Restoring,
    Ready,
    Foreground,
    Background,
    Failed,
    ShuttingDown,
    Stopped,
}

data class NaviampRuntimeState(
    val phase: NaviampRuntimePhase = NaviampRuntimePhase.Created,
    val connectivity: NaviampConnectivitySnapshot? = null,
    val lastError: NaviampRuntimeError? = null,
)

/**
 * Shared lifecycle and session bootstrap used by every thin platform host.
 *
 * Events are serialized so restoration, foreground/background callbacks, and shutdown cannot race.
 * Platform-specific lifecycle objects translate their callbacks into [NaviampHostLifecycleEvent]
 * rather than owning product state themselves.
 */
class NaviampApplicationRuntime(
    val services: NaviampPlatformServices,
) {
    private val eventMutex = Mutex()
    private val mutableState = MutableStateFlow(NaviampRuntimeState())

    val state: StateFlow<NaviampRuntimeState> = mutableState.asStateFlow()

    suspend fun handle(event: NaviampHostLifecycleEvent) {
        eventMutex.withLock {
            when (event) {
                NaviampHostLifecycleEvent.Start -> start()
                NaviampHostLifecycleEvent.EnterForeground -> enterForeground()
                NaviampHostLifecycleEvent.EnterBackground -> enterBackground()
                NaviampHostLifecycleEvent.Shutdown -> shutdown()
            }
        }
    }

    private suspend fun start() {
        if (state.value.phase !in setOf(NaviampRuntimePhase.Created, NaviampRuntimePhase.Failed)) return
        mutableState.value = state.value.copy(
            phase = NaviampRuntimePhase.Restoring,
            connectivity = services.connectivity.currentSnapshot(),
            lastError = null,
        )
        runOperation(
            operation = NaviampRuntimeOperation.Restore,
            onSuccess = { current -> current.copy(phase = NaviampRuntimePhase.Ready) },
            block = services.session::restore,
        )
    }

    private suspend fun enterForeground() {
        if (state.value.phase !in setOf(NaviampRuntimePhase.Ready, NaviampRuntimePhase.Background)) return
        runOperation(
            operation = NaviampRuntimeOperation.EnterForeground,
            onSuccess = { current ->
                current.copy(
                    phase = NaviampRuntimePhase.Foreground,
                    connectivity = services.connectivity.currentSnapshot(),
                )
            },
            block = services.session::enterForeground,
        )
    }

    private suspend fun enterBackground() {
        if (state.value.phase !in setOf(NaviampRuntimePhase.Ready, NaviampRuntimePhase.Foreground)) return
        runOperation(
            operation = NaviampRuntimeOperation.EnterBackground,
            onSuccess = { current -> current.copy(phase = NaviampRuntimePhase.Background) },
            block = services.session::enterBackground,
        )
    }

    private suspend fun shutdown() {
        if (state.value.phase in setOf(NaviampRuntimePhase.ShuttingDown, NaviampRuntimePhase.Stopped)) return
        mutableState.value = state.value.copy(phase = NaviampRuntimePhase.ShuttingDown)
        runOperation(
            operation = NaviampRuntimeOperation.Shutdown,
            onSuccess = { current -> current.copy(phase = NaviampRuntimePhase.Stopped) },
            onFailure = { current, error -> current.copy(phase = NaviampRuntimePhase.Stopped, lastError = error) },
            block = services.session::shutdown,
        )
    }

    private suspend fun runOperation(
        operation: NaviampRuntimeOperation,
        onSuccess: (NaviampRuntimeState) -> NaviampRuntimeState,
        onFailure: (NaviampRuntimeState, NaviampRuntimeError) -> NaviampRuntimeState = { current, error ->
            current.copy(phase = NaviampRuntimePhase.Failed, lastError = error)
        },
        block: suspend () -> Unit,
    ) {
        try {
            block()
            mutableState.value = onSuccess(state.value)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            val error = NaviampRuntimeError(
                operation = operation,
                message = cause.message ?: "Naviamp runtime operation failed.",
            )
            mutableState.value = onFailure(state.value, error)
            services.errorReporter.report(error, cause)
        }
    }
}
