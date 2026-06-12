package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.SharedMixBuilderUi

internal class AndroidNavigationController(
    private val state: AndroidAppState,
    private val openArtistDetails: (ArtistId, String?, Boolean) -> Unit,
) {
    fun closeActiveDetail() {
        val previousArtist = state.contentState.artistDetail
            ?.let { state.artistDetailBackStack.lastOrNull() }
        if (previousArtist != null) {
            state.artistDetailBackStack = state.artistDetailBackStack.dropLast(1)
            openArtistDetails(previousArtist.id, previousArtist.name, false)
        } else {
            state.contentState = state.contentState.clearDetails()
            state.artistDetailBackStack = emptyList()
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
        }
    }
}
