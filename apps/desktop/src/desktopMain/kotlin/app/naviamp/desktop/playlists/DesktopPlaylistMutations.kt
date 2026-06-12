package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider

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
