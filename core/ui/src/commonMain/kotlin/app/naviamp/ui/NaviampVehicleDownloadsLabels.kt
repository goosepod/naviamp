package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

data class NaviampVehicleDownloadsLabels(val downloads: String, val refresh: String, val play: String, val shuffle: String, val empty: String)

suspend fun loadNaviampVehicleDownloadsLabels() = NaviampVehicleDownloadsLabels(
    getString(Res.string.settings_category_downloads_title), getString(Res.string.artist_playlists_refresh),
    getString(Res.string.transport_play), getString(Res.string.transport_shuffle), getString(Res.string.tv_this_collection_is_empty),
)
