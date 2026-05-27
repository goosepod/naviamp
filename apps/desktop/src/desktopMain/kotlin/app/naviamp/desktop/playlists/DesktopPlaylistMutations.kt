package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaylistTrackMutationResult
import app.naviamp.domain.provider.createPlaylistOrAddMissingTracks
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

data class PlaylistDetailsRefresh(
    val playlists: List<Playlist>,
    val displayPlaylist: Playlist,
    val tracks: List<Track>,
)

data class AddToPlaylistMutationUpdate(
    val closeDialog: Boolean,
    val addToPlaylistStatus: String?,
    val connectionStatus: String?,
    val refreshPlaylists: Boolean,
)

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

suspend fun saveSmartPlaylistAndRefresh(
    provider: MediaProvider,
    definition: SmartPlaylistDefinition,
    playlistLimit: Int = 500,
): PlaylistDetailsRefresh {
    val playlist = provider.createSmartPlaylist(definition)
    val refreshedPlaylists = provider.playlists(limit = playlistLimit)
    val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id }
        ?: refreshedPlaylists.firstOrNull { it.name == playlist.name }
        ?: playlist
    val refreshedTracks = provider.playlistTracks(refreshedPlaylist.id)
    val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
    return PlaylistDetailsRefresh(
        playlists = (refreshedPlaylists.filterNot { it.id == displayPlaylist.id } + displayPlaylist)
            .sortedBy { it.name.lowercase() },
        displayPlaylist = displayPlaylist,
        tracks = refreshedTracks,
    )
}

suspend fun updateSmartPlaylistAndRefresh(
    provider: MediaProvider,
    playlist: Playlist,
    definition: SmartPlaylistDefinition,
    playlistLimit: Int = 500,
): PlaylistDetailsRefresh {
    provider.updateSmartPlaylist(playlist.id, definition)
    val refreshedPlaylists = provider.playlists(limit = playlistLimit)
    val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id }
        ?: playlist.copy(name = definition.name)
    val refreshedTracks = provider.playlistTracks(refreshedPlaylist.id)
    val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
    return PlaylistDetailsRefresh(
        playlists = (refreshedPlaylists.filterNot { it.id == displayPlaylist.id } + displayPlaylist)
            .sortedBy { it.name.lowercase() },
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

fun addToPlaylistMutationUpdate(
    result: PlaylistTrackMutationResult,
    playlist: Playlist?,
): AddToPlaylistMutationUpdate =
    when {
        result.requestedTrackCount == 0 -> AddToPlaylistMutationUpdate(
            closeDialog = false,
            addToPlaylistStatus = if (playlist == null) {
                "No tracks found."
            } else {
                "Everything is already in ${playlist.name}."
            },
            connectionStatus = null,
            refreshPlaylists = false,
        )
        result.addedTrackIds.isEmpty() -> AddToPlaylistMutationUpdate(
            closeDialog = false,
            addToPlaylistStatus = "Everything is already in ${playlist?.name.orEmpty()}.",
            connectionStatus = null,
            refreshPlaylists = false,
        )
        else -> AddToPlaylistMutationUpdate(
            closeDialog = true,
            addToPlaylistStatus = null,
            connectionStatus = "Added ${result.addedTrackIds.size} track${if (result.addedTrackIds.size == 1) "" else "s"} to playlist.",
            refreshPlaylists = true,
        )
    }

fun smartPlaylistSaveErrorMessage(error: Throwable): String =
    if (error.message == "Reconnect to Navidrome with your password before saving smart playlists.") {
        "Edit this saved connection, enter your Navidrome password, then Save and connect before saving smart playlists."
    } else {
        error.message ?: "Could not save smart playlist."
    }

fun smartPlaylistSavedStatus(displayPlaylist: Playlist, trackCount: Int): String =
    "Saved smart playlist ${displayPlaylist.name} with $trackCount tracks."

fun smartPlaylistUpdatedStatus(displayPlaylist: Playlist, trackCount: Int): String =
    "Updated smart playlist ${displayPlaylist.name} with $trackCount tracks."
