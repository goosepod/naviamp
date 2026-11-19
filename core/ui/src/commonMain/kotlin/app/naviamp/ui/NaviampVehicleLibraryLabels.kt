package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

data class NaviampVehicleLibraryLabels(
    val library: String, val artists: String, val albums: String, val tracks: String,
    val favorites: String, val unavailable: String,
)

suspend fun loadNaviampVehicleLibraryLabels() = NaviampVehicleLibraryLabels(
    getString(Res.string.library_title), getString(Res.string.discovery_catalog_artists),
    getString(Res.string.discovery_catalog_albums), getString(Res.string.artist_appearance_tracks),
    getString(Res.string.mix_bias_favorites), getString(Res.string.common_unavailable),
)
