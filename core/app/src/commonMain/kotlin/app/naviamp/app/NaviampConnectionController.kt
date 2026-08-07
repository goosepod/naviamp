package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviampConnectionPhase {
    Disconnected,
    Connecting,
    Connected,
    Offline,
    Failed,
}

enum class NaviampConnectionRestorationSource {
    SavedProviderConnection,
    SavedCredentials,
    None,
}

data class NaviampConnectionRuntimeState(
    val phase: NaviampConnectionPhase = NaviampConnectionPhase.Disconnected,
    val restoringSavedSession: Boolean = false,
    val sourceId: String? = null,
    val serverVersion: String? = null,
    val status: String? = null,
) {
    val isConnecting: Boolean get() = phase == NaviampConnectionPhase.Connecting
    val connected: Boolean
        get() = phase == NaviampConnectionPhase.Connected || phase == NaviampConnectionPhase.Offline
    val offline: Boolean get() = phase == NaviampConnectionPhase.Offline
    val restoringConnection: Boolean get() = isConnecting && restoringSavedSession
}

data class NaviampConnectionAttemptPlan(
    val restoreSavedSession: Boolean,
    val clearExistingPlayback: Boolean,
    val clearProviderData: Boolean,
    val runFullLibraryRefresh: Boolean,
)

/** Shared connection/restoration lifecycle; hosts retain provider construction and I/O. */
class NaviampConnectionController(
    initialState: NaviampConnectionRuntimeState = NaviampConnectionRuntimeState(),
    private val applicationStatus: NaviampApplicationStatusController? = null,
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<NaviampConnectionRuntimeState> = mutableState.asStateFlow()

    fun restorationSource(
        hasSavedProviderConnection: Boolean,
        serverUrl: String,
        username: String,
        password: String,
    ): NaviampConnectionRestorationSource =
        when {
            hasSavedProviderConnection -> NaviampConnectionRestorationSource.SavedProviderConnection
            serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() ->
                NaviampConnectionRestorationSource.SavedCredentials
            else -> NaviampConnectionRestorationSource.None
        }

    fun begin(restoreSavedSession: Boolean): NaviampConnectionAttemptPlan? {
        if (state.value.phase == NaviampConnectionPhase.Connecting) return null
        mutableState.value = NaviampConnectionRuntimeState(
            phase = NaviampConnectionPhase.Connecting,
            restoringSavedSession = restoreSavedSession,
            status = "Connecting...",
        )
        applicationStatus?.publish(
            area = NaviampApplicationStatusArea.Connection,
            level = NaviampApplicationStatusLevel.Information,
            message = "Connecting...",
        )
        return NaviampConnectionAttemptPlan(
            restoreSavedSession = restoreSavedSession,
            clearExistingPlayback = !restoreSavedSession,
            clearProviderData = !restoreSavedSession,
            runFullLibraryRefresh = !restoreSavedSession,
        )
    }

    fun connected(sourceId: String, serverVersion: String?, status: String) {
        mutableState.value = NaviampConnectionRuntimeState(
            phase = NaviampConnectionPhase.Connected,
            sourceId = sourceId,
            serverVersion = serverVersion,
            status = status,
        )
        applicationStatus?.publish(
            area = NaviampApplicationStatusArea.Connection,
            level = NaviampApplicationStatusLevel.Information,
            message = status,
        )
    }

    fun offline(sourceId: String, status: String) {
        mutableState.value = NaviampConnectionRuntimeState(
            phase = NaviampConnectionPhase.Offline,
            sourceId = sourceId,
            status = status,
        )
        applicationStatus?.publish(
            area = NaviampApplicationStatusArea.Connection,
            level = NaviampApplicationStatusLevel.Warning,
            message = status,
        )
    }

    fun failed(status: String) {
        mutableState.value = NaviampConnectionRuntimeState(
            phase = NaviampConnectionPhase.Failed,
            status = status,
        )
        applicationStatus?.publish(
            area = NaviampApplicationStatusArea.Connection,
            level = NaviampApplicationStatusLevel.Error,
            message = status,
        )
    }

    fun disconnected(status: String? = null) {
        mutableState.value = NaviampConnectionRuntimeState(status = status)
        if (status == null) {
            applicationStatus?.clear(NaviampApplicationStatusArea.Connection)
        } else {
            applicationStatus?.publish(
                area = NaviampApplicationStatusArea.Connection,
                level = NaviampApplicationStatusLevel.Information,
                message = status,
            )
        }
    }
}
