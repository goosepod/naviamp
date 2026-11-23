package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

/** Translated playback vocabulary shared by constrained vehicle surfaces. */
data class NaviampVehiclePlaybackLabels(
    val radio: String, val favorite: String, val unfavorite: String, val shuffle: String,
    val repeatOff: String, val repeatAll: String, val repeatOne: String,
    val failed: String, val dismiss: String,
)

suspend fun loadNaviampVehiclePlaybackLabels() = NaviampVehiclePlaybackLabels(
    naviampString(Res.string.tv_start_radio), naviampString(Res.string.tv_favorite),
    naviampString(Res.string.playlist_item_unfavorite), naviampString(Res.string.transport_shuffle),
    naviampString(Res.string.tv_repeat_off), naviampString(Res.string.tv_repeat_all), naviampString(Res.string.tv_repeat_one),
    naviampString(Res.string.discovery_acquisition_failed), naviampString(Res.string.library_sources_cancel),
)
