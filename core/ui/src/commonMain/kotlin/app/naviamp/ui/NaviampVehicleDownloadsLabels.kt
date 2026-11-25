package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

data class NaviampVehicleDownloadsLabels(val downloads: String, val refresh: String, val play: String, val shuffle: String, val empty: String)

suspend fun loadNaviampVehicleDownloadsLabels() = NaviampVehicleDownloadsLabels(
    naviampString(Res.string.settings_category_downloads_title), naviampString(Res.string.artist_playlists_refresh),
    naviampString(Res.string.transport_play), naviampString(Res.string.transport_shuffle), naviampString(Res.string.tv_this_collection_is_empty),
)
