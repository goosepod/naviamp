package app.naviamp.android

import app.naviamp.app.NaviampDetailBackCommand
import app.naviamp.app.NaviampDetailKind
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.SharedMixBuilderUi

internal class AndroidNavigationController(
    private val state: AndroidAppState,
    private val openArtistDetails: (ArtistId, String?, Boolean) -> Unit,
) {
    fun closeActiveDetail() {
        val kind = if (state.albumDetail != null) NaviampDetailKind.Album else NaviampDetailKind.Artist
        when (val command = state.sharedNavigationController.closeActiveDetail(kind)) {
            is NaviampDetailBackCommand.OpenArtist ->
                openArtistDetails(command.artist.id, command.artist.name, false)
            is NaviampDetailBackCommand.Navigate -> {
                state.contentState = state.contentState.clearDetails()
                state.navigationState = state.navigationState.copy(route = command.route)
            }
        }
    }

    fun closeActivePlaylist() {
        state.contentState = state.contentState.copy(
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
        )
    }

    fun handleAndroidBack() {
        when {
            state.nowPlayingOpen -> state.nowPlayingOpen = false
            state.albumDetail != null || state.artistDetail != null -> closeActiveDetail()
            state.selectedPlaylist != null -> closeActivePlaylist()
            state.editingConnection && state.provider != null -> state.editingConnection = false
            state.navigationState.route != NaviampRoute.Home -> {
                state.navigationState = state.navigationState.copy(route = NaviampRoute.Home)
                state.contentState = state.contentState.clearDetails()
                state.sharedNavigationController.clearDetailHistory()
            }
        }
    }

    fun handlesAndroidBack(): Boolean =
        state.nowPlayingOpen ||
            state.albumDetail != null ||
            state.artistDetail != null ||
            state.selectedPlaylist != null ||
            (state.editingConnection && state.provider != null) ||
            state.navigationState.route != NaviampRoute.Home

    fun handleMixBuilderSelected(builder: SharedMixBuilderUi) {
        state.contentState = state.contentState.clearDetails()
        state.nowPlayingOpen = false
        when (builder.id) {
            "artist" -> state.navigationState = state.navigationState.copy(route = NaviampRoute.ArtistMix)
            "genre" -> state.navigationState = state.navigationState.copy(route = NaviampRoute.GenreMix)
            "album" -> state.navigationState = state.navigationState.copy(route = NaviampRoute.AlbumMix)
            "sonic-path" -> state.navigationState = state.navigationState.copy(route = NaviampRoute.SonicPath)
            "sonic-mix" -> state.navigationState = state.navigationState.copy(route = NaviampRoute.SonicMix)
        }
    }
}
