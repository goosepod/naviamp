package app.naviamp.presentation

import app.naviamp.domain.network.NetworkMetering
import app.naviamp.domain.network.NetworkMeteringSource
import kotlin.concurrent.Volatile
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Network.framework path monitor translated into Core's metering fact. Expensive covers cellular
 * and personal hotspots; constrained is Low Data Mode. The host owns start and [close].
 */
class AppleNetworkMeteringSource : NetworkMeteringSource {
    @Volatile
    private var latest = NetworkMetering.Unknown
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("app.naviamp.network-metering", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            latest = when {
                path == null || nw_path_get_status(path) != nw_path_status_satisfied -> NetworkMetering.Unknown
                nw_path_is_expensive(path) || nw_path_is_constrained(path) -> NetworkMetering.Metered
                else -> NetworkMetering.Unmetered
            }
        }
        nw_path_monitor_start(monitor)
    }

    override fun current(): NetworkMetering = latest

    fun close() {
        nw_path_monitor_cancel(monitor)
    }
}
