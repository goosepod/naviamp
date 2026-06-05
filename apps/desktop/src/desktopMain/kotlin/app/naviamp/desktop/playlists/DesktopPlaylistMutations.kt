package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaylistTrackMutationResult
import app.naviamp.domain.provider.createPlaylistOrAddTracks

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
    return provider.createPlaylistOrAddTracks(
        playlistId = playlist?.id,
        newPlaylistName = newPlaylistName,
        tracks = resolveAddToPlaylistTargetTracks(provider, target),
    )
}
