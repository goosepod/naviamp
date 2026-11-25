package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

data class NaviampVehicleSearchLabels(val title: String, val hint: String, val empty: String)

suspend fun loadNaviampVehicleSearchLabels() = NaviampVehicleSearchLabels(
    naviampString(Res.string.discovery_catalog_search_action), naviampString(Res.string.search_music_label),
    naviampString(Res.string.search_no_matches),
)
