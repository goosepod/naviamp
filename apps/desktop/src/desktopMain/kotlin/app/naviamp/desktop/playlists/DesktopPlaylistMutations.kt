package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaylistDetailsRefresh
import app.naviamp.domain.provider.PlaylistTrackMutationResult
import app.naviamp.domain.provider.createPlaylistOrAddMissingTracks

fun homePlaylists(
    playlists: List<Playlist>,
    recentPlaylistIds: List<String>,
    limit: Int = 6,
): List<Playlist> =
    playlists.sortedWith(
        compareBy<Playlist> {
            val index = recentPlaylistIds.indexOf(it.id)
            if (index == -1) Int.MAX_VALUE else index
        }.thenBy { it.name.lowercase() },
    ).take(limit)

suspend fun refreshPlaylistDetails(
    provider: MediaProvider,
    playlist: Playlist,
    playlistLimit: Int = 500,
): PlaylistDetailsRefresh {
    val refreshedPlaylists = provider.playlists(limit = playlistLimit)
    val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
    val refreshedTracks = provider.playlistTracks(refreshedPlaylist.id)
    val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
    return PlaylistDetailsRefresh(
        playlists = refreshedPlaylists.map {
            if (it.id == displayPlaylist.id) displayPlaylist else it
        },
        displayPlaylist = displayPlaylist,
        tracks = refreshedTracks,
    )
}

suspend fun resolveAddToPlaylistTargetTracks(
    provider: MediaProvider,
    target: AddToPlaylistTarget,
): List<Track> =
    when (target) {
        is AddToPlaylistTarget.TrackTarget -> listOf(target.track)
        is AddToPlaylistTarget.AlbumTarget -> provider.album(target.album.id).tracks
        is AddToPlaylistTarget.ArtistTarget -> provider.artist(target.artist.id).albums.flatMap { album ->
            provider.album(album.id).tracks
        }
        is AddToPlaylistTarget.PlaylistTarget -> provider.playlistTracks(target.playlist.id)
    }

suspend fun addTargetTracksToPlaylist(
    provider: MediaProvider,
    target: AddToPlaylistTarget,
    playlist: Playlist?,
    newPlaylistName: String?,
): PlaylistTrackMutationResult {
    val targetIds = resolveAddToPlaylistTargetTracks(provider, target).map { it.id }.distinct()
    return provider.createPlaylistOrAddMissingTracks(
        playlistId = playlist?.id,
        newPlaylistName = newPlaylistName,
        trackIds = targetIds,
    )
}
