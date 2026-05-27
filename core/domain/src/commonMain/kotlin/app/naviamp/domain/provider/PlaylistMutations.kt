package app.naviamp.domain.provider

import app.naviamp.domain.TrackId

data class PlaylistTrackMutationResult(
    val requestedTrackCount: Int,
    val addedTrackIds: List<TrackId>,
    val createdPlaylist: Boolean,
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
