package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

/** Translated playback vocabulary shared by constrained vehicle surfaces. */
data class NaviampVehiclePlaybackLabels(
    val radio: String, val favorite: String, val unfavorite: String, val shuffle: String,
    val repeatOff: String, val repeatAll: String, val repeatOne: String,
    val failed: String, val dismiss: String,
)

suspend fun loadNaviampVehiclePlaybackLabels() = NaviampVehiclePlaybackLabels(
    getString(Res.string.tv_start_radio), getString(Res.string.tv_favorite),
    getString(Res.string.playlist_item_unfavorite), getString(Res.string.transport_shuffle),
    getString(Res.string.tv_repeat_off), getString(Res.string.tv_repeat_all), getString(Res.string.tv_repeat_one),
    getString(Res.string.discovery_acquisition_failed), getString(Res.string.library_sources_cancel),
)
