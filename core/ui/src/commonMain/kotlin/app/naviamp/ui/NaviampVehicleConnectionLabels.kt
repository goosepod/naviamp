package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

data class NaviampVehicleConnectionLabels(
    val title: String, val reconnect: String, val current: String, val connecting: String,
    val failed: String, val setupOnPhone: String, val unavailable: String, val offline: String,
)

suspend fun loadNaviampVehicleConnectionLabels() = NaviampVehicleConnectionLabels(
    naviampString(Res.string.settings_source_connections_title), naviampString(Res.string.common_reconnect),
    naviampString(Res.string.common_current), naviampString(Res.string.common_connecting),
    naviampString(Res.string.tv_account_switch_failed), naviampString(Res.string.vehicle_connection_setup_on_phone),
    naviampString(Res.string.common_unavailable), naviampString(Res.string.vehicle_connection_offline),
)
