package app.naviamp.domain.app

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.PlaybackQueue

enum class NaviampRoute {
    Player,
    Home,
    Playlists,
    PlaylistDetail,
    AlbumDetail,
    ArtistDetail,
    Library,
    Search,
    ArtistMix,
    AlbumMix,
    Radio,
    Downloads,
    Settings,
}

data class NaviampNavigationState(
    val route: NaviampRoute = NaviampRoute.Home,
    val lastContentRoute: NaviampRoute = NaviampRoute.Home,
)

data class NaviampConnectionState(
    val serverVersion: String? = null,
    val statusMessage: String? = null,
    val connected: Boolean = false,
    val editingConnection: Boolean = false,
)

data class NaviampContentState(
    val searchQuery: String = "",
    val searchResults: MediaSearchResults = MediaSearchResults(),
    val albumDetail: AlbumDetails? = null,
    val artistDetail: ArtistDetails? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
) {
    fun clearDetails(): NaviampContentState =
        copy(
            albumDetail = null,
            artistDetail = null,
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
        )

    fun showAlbum(detail: AlbumDetails): NaviampContentState =
        clearDetails().copy(albumDetail = detail)

    fun showArtist(detail: ArtistDetails): NaviampContentState =
        clearDetails().copy(artistDetail = detail)

    fun showPlaylist(playlist: Playlist, tracks: List<Track> = emptyList()): NaviampContentState =
        clearDetails().copy(
            selectedPlaylist = playlist,
            selectedPlaylistTracks = tracks,
        )
}

data class NaviampNowPlayingState(
    val track: Track? = null,
    val station: InternetRadioStation? = null,
    val open: Boolean = false,
    val queue: PlaybackQueue = PlaybackQueue(),
    val relatedTracks: List<Track> = emptyList(),
)

data class NaviampAppState(
    val navigation: NaviampNavigationState = NaviampNavigationState(),
    val connection: NaviampConnectionState = NaviampConnectionState(),
    val content: NaviampContentState = NaviampContentState(),
    val nowPlaying: NaviampNowPlayingState = NaviampNowPlayingState(),
)
