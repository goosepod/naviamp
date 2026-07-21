package app.naviamp.presentation

import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampSettingsSyncUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The complete host-independent state rendered by a Naviamp application. */
data class NaviampCoreState(
    val shell: NaviampAppShellUiState = NaviampAppShellUiState(),
    val settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    val overlays: NaviampCoreOverlayState = NaviampCoreOverlayState(),
    val viewport: NaviampCoreViewportState = NaviampCoreViewportState(),
)

data class NaviampCoreOverlayState(
    val statsForNerdsVisible: Boolean = false,
    val status: String? = null,
)

data class NaviampCoreViewportState(
    val libraryJump: NaviampCoreLibraryJumpRequest? = null,
)

data class NaviampCoreLibraryJumpRequest(
    val letter: Char,
    val generation: Long,
)

/**
 * Single state owner for the product graph. Feature controllers update slices through this store;
 * hosts only collect [state].
 */
class NaviampCoreStateStore(initialState: NaviampCoreState = NaviampCoreState()) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<NaviampCoreState> = mutableState.asStateFlow()

    fun update(transform: (NaviampCoreState) -> NaviampCoreState) {
        mutableState.update(transform)
    }

    fun updateShell(transform: (NaviampAppShellUiState) -> NaviampAppShellUiState) {
        update { current -> current.copy(shell = transform(current.shell)) }
    }

    fun replace(state: NaviampCoreState) {
        mutableState.value = state
    }
}
