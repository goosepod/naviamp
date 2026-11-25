package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

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
    home = naviampString(Res.string.home_music_title),
    playlists = naviampString(Res.string.playlists_title),
    radio = naviampString(Res.string.settings_experience_related_tracks_title),
    empty = naviampString(Res.string.tv_this_collection_is_empty),
    loading = naviampString(Res.string.tv_loading),
    disconnected = naviampString(Res.string.home_empty),
    failed = naviampString(Res.string.discovery_acquisition_failed),
    next = naviampString(Res.string.transport_next),
    previous = naviampString(Res.string.transport_previous),
    queue = naviampString(Res.string.player_queue),
    play = naviampString(Res.string.transport_play),
    shuffle = naviampString(Res.string.transport_shuffle),
    retry = naviampString(Res.string.library_sources_retry),
)
