package app.naviamp.domain.provider

import app.naviamp.domain.Playlist
import app.naviamp.domain.TrackId

data class PlaylistTrackMutationResult(
    val requestedTrackCount: Int,
    val addedTrackIds: List<TrackId>,
    val createdPlaylist: Boolean,
)

data class AddToPlaylistMutationUpdate(
    val closeDialog: Boolean,
    val addToPlaylistStatus: String?,
    val connectionStatus: String?,
    val refreshPlaylists: Boolean,
)

suspend fun MediaProvider.createPlaylistOrAddMissingTracks(
    playlistId: String?,
    newPlaylistName: String?,
    trackIds: List<TrackId>,
): PlaylistTrackMutationResult {
    val uniqueTrackIds = trackIds.distinct()
    if (uniqueTrackIds.isEmpty()) {
        return PlaylistTrackMutationResult(
            requestedTrackCount = 0,
            addedTrackIds = emptyList(),
            createdPlaylist = false,
        )
    }

    if (playlistId == null) {
        createPlaylist(newPlaylistName.orEmpty(), uniqueTrackIds)
        return PlaylistTrackMutationResult(
            requestedTrackCount = uniqueTrackIds.size,
            addedTrackIds = uniqueTrackIds,
            createdPlaylist = true,
        )
    }

    val existingIds = playlistTracks(playlistId).map { it.id }.toSet()
    val missingIds = uniqueTrackIds.filterNot { it in existingIds }
    if (missingIds.isNotEmpty()) {
        addTracksToPlaylist(playlistId, missingIds)
    }
    return PlaylistTrackMutationResult(
        requestedTrackCount = uniqueTrackIds.size,
        addedTrackIds = missingIds,
        createdPlaylist = false,
    )
}

fun addToPlaylistMutationUpdate(
    result: PlaylistTrackMutationResult,
    playlistName: String?,
): AddToPlaylistMutationUpdate =
    when {
        result.requestedTrackCount == 0 -> AddToPlaylistMutationUpdate(
            closeDialog = false,
            addToPlaylistStatus = if (playlistName == null) {
                "No tracks found."
            } else {
                "Everything is already in $playlistName."
            },
            connectionStatus = null,
            refreshPlaylists = false,
        )
        result.addedTrackIds.isEmpty() -> AddToPlaylistMutationUpdate(
            closeDialog = false,
            addToPlaylistStatus = "Everything is already in ${playlistName.orEmpty()}.",
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

fun normalizedPlaylistName(name: String): String =
    name.trim()

fun playlistRenameLoadingStatus(playlist: Playlist): String =
    "Renaming ${playlist.name}..."

fun playlistDeleteLoadingStatus(playlist: Playlist): String =
    "Deleting ${playlist.name}..."

fun playlistRenamedStatus(): String =
    "Renamed playlist."

fun playlistDeletedStatus(): String =
    "Deleted playlist."

fun playlistRenameErrorMessage(error: Throwable): String =
    error.message ?: "Could not rename playlist."

fun playlistDeleteErrorMessage(error: Throwable): String =
    error.message ?: "Could not delete playlist."

fun renamedSelectedPlaylist(
    current: Playlist?,
    playlistId: String,
    requestedName: String,
    refreshedPlaylists: List<Playlist>,
): Playlist? {
    if (current?.id != playlistId) return current
    return refreshedPlaylists.firstOrNull { it.id == playlistId }
        ?: current.copy(name = requestedName)
}

fun selectedPlaylistAfterDelete(
    current: Playlist?,
    deletedPlaylistId: String,
): Playlist? =
    current?.takeUnless { it.id == deletedPlaylistId }

fun recentPlaylistIdsAfterDelete(
    recentPlaylistIds: List<String>,
    deletedPlaylistId: String,
): List<String> =
    recentPlaylistIds.filterNot { it == deletedPlaylistId }
