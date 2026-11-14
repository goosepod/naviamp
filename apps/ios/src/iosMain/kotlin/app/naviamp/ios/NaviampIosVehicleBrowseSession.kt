package app.naviamp.ios

import app.naviamp.presentation.NaviampVehicleBrowseController
import app.naviamp.presentation.NaviampVehicleBrowseDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Objective-C/Swift ABI projections. All browsing and selection behavior belongs to Core. */
class NaviampIosVehicleBrowseRow(val id: String, val title: String, val subtitle: String)
class NaviampIosVehicleBrowseTab(
    val id: String,
    val title: String,
    val rows: List<NaviampIosVehicleBrowseRow>,
    val status: String,
)

/** Binds a Swift scene callback lifetime to the application's main-dispatcher shared session. */
class NaviampIosVehicleBrowseSession internal constructor(
    scope: CoroutineScope,
    create: suspend () -> NaviampVehicleBrowseController,
    changed: (Long, List<NaviampIosVehicleBrowseTab>) -> Unit,
) {
    private var controller: NaviampVehicleBrowseController? = null
    private val observation: Job = scope.launch {
        val shared = create()
        controller = shared
        shared.state.collect { state ->
            changed(state.revision, state.tabs.map { tab ->
                NaviampIosVehicleBrowseTab(tab.destination.name, tab.title,
                    tab.rows.map { NaviampIosVehicleBrowseRow(it.id, it.title, it.subtitle) }, tab.status)
            })
        }
    }

    fun select(destination: String, id: String, revision: Long, completed: (Boolean) -> Unit) {
        val shared = controller
        val nativeDestination = NaviampVehicleBrowseDestination.entries.firstOrNull { it.name == destination }
        if (shared == null || nativeDestination == null) completed(false)
        else shared.select(nativeDestination, id, revision, completed)
    }

    fun close() {
        observation.cancel()
        controller?.close()
        controller = null
    }
}
