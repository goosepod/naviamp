package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviampApplicationStatusArea {
    Runtime,
    Connection,
    ProviderActions,
    CacheMaintenance,
    Downloads,
    SettingsSync,
}

enum class NaviampApplicationStatusLevel {
    Information,
    Warning,
    Error,
}

data class NaviampApplicationStatus(
    val sequence: Long,
    val area: NaviampApplicationStatusArea,
    val level: NaviampApplicationStatusLevel,
    val message: String,
)

/**
 * Shared owner for application-wide messages that must be presented consistently by every host.
 *
 * Page-local loading and validation text remains with the page that owns it. This controller is
 * reserved for cross-cutting runtime, connection, and provider-action outcomes.
 */
class NaviampApplicationStatusController {
    private val mutableState = MutableStateFlow<NaviampApplicationStatus?>(null)
    private var nextSequence = 1L

    val state: StateFlow<NaviampApplicationStatus?> = mutableState.asStateFlow()

    fun publish(
        area: NaviampApplicationStatusArea,
        level: NaviampApplicationStatusLevel,
        message: String,
    ): NaviampApplicationStatus {
        val status = NaviampApplicationStatus(
            sequence = nextSequence++,
            area = area,
            level = level,
            message = message,
        )
        mutableState.value = status
        return status
    }

    fun clear(area: NaviampApplicationStatusArea? = null) {
        if (area == null || state.value?.area == area) {
            mutableState.value = null
        }
    }
}
