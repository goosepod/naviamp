package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

data class NaviampVehicleConnectionLabels(
    val title: String, val reconnect: String, val current: String, val connecting: String,
    val failed: String, val setupOnPhone: String, val unavailable: String, val offline: String,
)

suspend fun loadNaviampVehicleConnectionLabels() = NaviampVehicleConnectionLabels(
    getString(Res.string.settings_source_connections_title), getString(Res.string.common_reconnect),
    getString(Res.string.common_current), getString(Res.string.common_connecting),
    getString(Res.string.tv_account_switch_failed), getString(Res.string.vehicle_connection_setup_on_phone),
    getString(Res.string.common_unavailable), getString(Res.string.vehicle_connection_offline),
)
