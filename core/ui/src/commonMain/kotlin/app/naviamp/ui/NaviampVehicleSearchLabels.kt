package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

data class NaviampVehicleSearchLabels(val title: String, val hint: String, val empty: String)

suspend fun loadNaviampVehicleSearchLabels() = NaviampVehicleSearchLabels(
    getString(Res.string.discovery_catalog_search_action), getString(Res.string.search_music_label),
    getString(Res.string.search_no_matches),
)
