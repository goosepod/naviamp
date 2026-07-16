package app.naviamp.app

import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Canonical cross-platform owner of top-level route state.
 *
 * Detail-screen back stacks and operating-system back dispatch remain host responsibilities until
 * their policies are modeled explicitly. Hosts expose this state through thin observable adapters.
 */
class NaviampNavigationController(
    initialState: NaviampNavigationState = NaviampNavigationState(),
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<NaviampNavigationState> = mutableState.asStateFlow()

    fun navigate(route: NaviampRoute) {
        mutableState.update { current -> current.copy(route = route) }
    }

    fun updateLastContentRoute(route: NaviampRoute) {
        mutableState.update { current -> current.copy(lastContentRoute = route) }
    }

    fun replace(state: NaviampNavigationState) {
        mutableState.value = state
    }
}
