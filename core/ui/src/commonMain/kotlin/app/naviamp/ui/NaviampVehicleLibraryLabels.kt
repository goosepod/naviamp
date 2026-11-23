package app.naviamp.ui

import app.naviamp.ui.generated.resources.*

data class NaviampVehicleLibraryLabels(
    val library: String, val artists: String, val albums: String, val tracks: String,
    val favorites: String, val unavailable: String,
)

suspend fun loadNaviampVehicleLibraryLabels() = NaviampVehicleLibraryLabels(
    naviampString(Res.string.library_title), naviampString(Res.string.discovery_catalog_artists),
    naviampString(Res.string.discovery_catalog_albums), naviampString(Res.string.artist_appearance_tracks),
    naviampString(Res.string.mix_bias_favorites), naviampString(Res.string.common_unavailable),
)
