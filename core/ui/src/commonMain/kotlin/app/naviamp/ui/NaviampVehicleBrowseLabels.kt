package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

/** Shared translated vocabulary for constrained vehicle browse surfaces. */
data class NaviampVehicleBrowseLabels(
    val home: String,
    val playlists: String,
    val radio: String,
    val empty: String,
    val loading: String,
    val disconnected: String,
    val failed: String,
    val next: String,
    val previous: String,
    val queue: String,
    val play: String,
    val shuffle: String,
    val retry: String,
)

suspend fun loadNaviampVehicleBrowseLabels() = NaviampVehicleBrowseLabels(
    home = getString(Res.string.home_music_title),
    playlists = getString(Res.string.playlists_title),
    radio = getString(Res.string.settings_experience_related_tracks_title),
    empty = getString(Res.string.tv_this_collection_is_empty),
    loading = getString(Res.string.tv_loading),
    disconnected = getString(Res.string.home_empty),
    failed = getString(Res.string.discovery_acquisition_failed),
    next = getString(Res.string.transport_next),
    previous = getString(Res.string.transport_previous),
    queue = getString(Res.string.player_queue),
    play = getString(Res.string.transport_play),
    shuffle = getString(Res.string.transport_shuffle),
    retry = getString(Res.string.library_sources_retry),
)
