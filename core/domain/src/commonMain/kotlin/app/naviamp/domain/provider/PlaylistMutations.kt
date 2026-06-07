package app.naviamp.domain.provider

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.appendableTracks
import app.naviamp.domain.playback.queueAppendStatus
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

data class PlaylistTrackMutationResult(
    val requestedTrackCount: Int,
    val addedTrackIds: List<TrackId>,
    val createdPlaylist: Boolean,
)

data class QueuePlaylistSaveResult(
    val playlist: Playlist,
    val trackCount: Int,
)

data class QueuePlaylistSaveRefresh(
    val result: QueuePlaylistSaveResult,
    val playlists: List<Playlist>,
)

data class AddToPlaylistMutationUpdate(
    val closeDialog: Boolean,
    val addToPlaylistStatus: String?,
    val connectionStatus: String?,
    val refreshPlaylists: Boolean,
)

data class AddToPlaylistRefresh(
    val update: AddToPlaylistMutationUpdate,
    val playlists: List<Playlist>?,
)

data class PlaylistDetailsRefresh(
    val playlists: List<Playlist>,
    val displayPlaylist: Playlist,
    val tracks: List<Track>,
)

data class PlaylistDetailsStateUpdate(
    val playlists: List<Playlist>,
    val selectedPlaylist: Playlist?,
    val selectedPlaylistTracks: List<Track>,
    val playlistTracksById: Map<String, List<Track>>,
)

data class PlaylistDetailAutoRefreshTarget<Provider : Any>(
    val provider: Provider,
    val playlist: Playlist,
)

data class PlaylistDeleteStateUpdate(
    val selectedPlaylist: Playlist?,
    val selectedPlaylistTracks: List<Track>,
    val playlistTracksById: Map<String, List<Track>>,
    val recentPlaylistIds: List<String>,
    val deletedSelectedPlaylist: Boolean,
)

const val PlaylistDetailRefreshIntervalMillis = 60_000L

data class QueueAppendPlan(
    val tracks: List<Track>,
    val status: String,
)

data class PendingPlaybackAction(
    val key: String,
    val status: String,
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

fun recentPlaylistIdsAfterPlayed(
    recentPlaylistIds: List<String>,
    playlistId: String,
    limit: Int,
): List<String> =
    (listOf(playlistId) + recentPlaylistIds.filterNot { it == playlistId }).take(limit)

fun playlistsNeedingTrackPreload(
    playlists: List<Playlist>,
    playlistTracksById: Map<String, List<Track>>,
    limit: Int = 100,
): List<Playlist> =
    playlists.take(limit).filter { playlist ->
        playlistTracksById[playlist.id].isNullOrEmpty()
    }

fun <Provider : Any> playlistDetailAutoRefreshTarget(
    provider: Provider?,
    playlist: Playlist?,
    enabled: Boolean = true,
): PlaylistDetailAutoRefreshTarget<Provider>? =
    if (enabled && provider != null && playlist != null) {
        PlaylistDetailAutoRefreshTarget(provider, playlist)
    } else {
        null
    }

suspend fun <Provider : Any> runPlaylistDetailAutoRefresh(
    target: PlaylistDetailAutoRefreshTarget<Provider>,
    waitForNextRefresh: suspend () -> Unit,
    refresh: suspend (provider: Provider, playlist: Playlist) -> Unit,
) {
    while (true) {
        waitForNextRefresh()
        runCatching {
            refresh(target.provider, target.playlist)
        }
    }
}

suspend fun MediaProvider.refreshPlaylistDetails(
    playlist: Playlist,
    playlistLimit: Int = 500,
    providerResponseService: ProviderResponseService? = null,
): PlaylistDetailsRefresh {
    val refreshedPlaylists = providerResponseService?.playlists(this, playlistLimit)
        ?: playlists(limit = playlistLimit)
    val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
    val refreshedTracks = providerResponseService?.playlistTracks(this, refreshedPlaylist.id)
        ?: playlistTracks(refreshedPlaylist.id)
    val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
    return PlaylistDetailsRefresh(
        playlists = refreshedPlaylists.map {
            if (it.id == displayPlaylist.id) displayPlaylist else it
        },
        displayPlaylist = displayPlaylist,
        tracks = refreshedTracks,
    )
}

fun playlistDetailsStateUpdate(
    currentSelectedPlaylist: Playlist?,
    currentSelectedPlaylistTracks: List<Track>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    refresh: PlaylistDetailsRefresh,
    requestedPlaylistId: String,
): PlaylistDetailsStateUpdate {
    val isCurrentSelection = currentSelectedPlaylist?.id == requestedPlaylistId
    return PlaylistDetailsStateUpdate(
        playlists = refresh.playlists,
        selectedPlaylist = if (isCurrentSelection) refresh.displayPlaylist else currentSelectedPlaylist,
        selectedPlaylistTracks = if (isCurrentSelection) refresh.tracks else currentSelectedPlaylistTracks,
        playlistTracksById = currentPlaylistTracksById + (refresh.displayPlaylist.id to refresh.tracks),
    )
}

fun playlistPlaybackTracks(
    tracks: List<Track>,
    shuffle: Boolean,
): List<Track> =
    if (shuffle) tracks.shuffled() else tracks

fun playlistPlaybackAction(
    playlist: Playlist,
    shuffle: Boolean,
): PendingPlaybackAction =
    PendingPlaybackAction(
        key = "playlist:${playlist.id}:${if (shuffle) "shuffle" else "play"}",
        status = if (shuffle) {
            "Starting ${playlist.name} in random order..."
        } else {
            "Loading ${playlist.name}..."
        },
    )

fun shouldStartPlaybackAction(pending: PendingPlaybackAction?): Boolean =
    pending == null

fun clearPendingPlaybackAction(
    pending: PendingPlaybackAction?,
    completed: PendingPlaybackAction,
): PendingPlaybackAction? =
    pending?.takeUnless { it.key == completed.key }

fun selectedPlaylistTracksForPlayback(
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    playlist: Playlist,
    loadedTracks: List<Track>,
): List<Track> =
    if (selectedPlaylist?.id == playlist.id && selectedPlaylistTracks.isNotEmpty()) {
        selectedPlaylistTracks
    } else {
        loadedTracks
    }

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

suspend fun MediaProvider.createPlaylistOrAddTracks(
    playlistId: String?,
    newPlaylistName: String?,
    tracks: List<Track>,
): PlaylistTrackMutationResult =
    createPlaylistOrAddMissingTracks(
        playlistId = playlistId,
        newPlaylistName = newPlaylistName,
        trackIds = tracks.map { it.id }.distinct(),
    )

suspend fun MediaProvider.addTracksToPlaylistAndRefresh(
    playlistId: String?,
    playlistName: String?,
    newPlaylistName: String?,
    tracks: List<Track>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): AddToPlaylistRefresh {
    val result = createPlaylistOrAddTracks(
        playlistId = playlistId,
        newPlaylistName = newPlaylistName,
        tracks = tracks.distinctBy { it.id },
    )
    val update = addToPlaylistMutationUpdate(result, playlistName)
    val playlists = if (update.refreshPlaylists) {
        if (playlistId != null) {
            providerResponseService?.invalidatePlaylistResponses(this, playlistId)
        } else {
            providerResponseService?.invalidatePlaylists(this)
        }
        providerResponseService?.playlists(this, limit = playlistLimit)
            ?: playlists(limit = playlistLimit)
    } else {
        null
    }
    return AddToPlaylistRefresh(update = update, playlists = playlists)
}

suspend fun MediaProvider.createQueuePlaylist(
    name: String,
    tracks: List<Track>,
): QueuePlaylistSaveResult {
    val playlistName = normalizedPlaylistName(name)
    require(playlistName.isNotBlank()) { "Playlist name is required." }
    require(tracks.isNotEmpty()) { "Queue is empty." }
    val playlist = createPlaylist(playlistName, tracks.map { it.id })
    return QueuePlaylistSaveResult(playlist = playlist, trackCount = tracks.size)
}

suspend fun MediaProvider.saveQueueAsPlaylistAndRefresh(
    name: String,
    tracks: List<Track>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): QueuePlaylistSaveRefresh {
    val result = createQueuePlaylist(name, tracks)
    providerResponseService?.invalidatePlaylists(this)
    val playlists = providerResponseService?.playlists(this, limit = playlistLimit)
        ?: playlists(limit = playlistLimit)
    return QueuePlaylistSaveRefresh(result = result, playlists = playlists)
}

fun queueAppendPlan(
    tracks: List<Track>,
    label: String = "tracks",
    existingTracks: List<Track> = emptyList(),
    deduplicateExisting: Boolean = false,
): QueueAppendPlan {
    val tracksToAdd = appendableTracks(
        tracksToAdd = tracks,
        existingTracks = existingTracks,
        deduplicateExisting = deduplicateExisting,
    )
    return QueueAppendPlan(
        tracks = tracksToAdd,
        status = queueAppendStatus(
            originalTracks = tracks,
            tracksToAdd = tracksToAdd,
            label = label,
            deduplicateExisting = deduplicateExisting,
        ),
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

fun playlistDeleteStateUpdate(
    currentSelectedPlaylist: Playlist?,
    currentSelectedPlaylistTracks: List<Track>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    currentRecentPlaylistIds: List<String>,
    deletedPlaylistId: String,
): PlaylistDeleteStateUpdate {
    val deletedSelectedPlaylist = currentSelectedPlaylist?.id == deletedPlaylistId
    return PlaylistDeleteStateUpdate(
        selectedPlaylist = selectedPlaylistAfterDelete(currentSelectedPlaylist, deletedPlaylistId),
        selectedPlaylistTracks = if (deletedSelectedPlaylist) emptyList() else currentSelectedPlaylistTracks,
        playlistTracksById = currentPlaylistTracksById - deletedPlaylistId,
        recentPlaylistIds = recentPlaylistIdsAfterDelete(currentRecentPlaylistIds, deletedPlaylistId),
        deletedSelectedPlaylist = deletedSelectedPlaylist,
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

fun smartPlaylistSaveErrorMessage(error: Throwable): String =
    if (error.message == "Reconnect to Navidrome with your password before saving smart playlists.") {
        "Edit this saved connection, enter your Navidrome password, then Save and connect before saving smart playlists."
    } else {
        error.message ?: "Could not save smart playlist."
    }

fun smartPlaylistUpdateErrorMessage(error: Throwable): String =
    error.message ?: "Could not update smart playlist."

fun smartPlaylistLoadErrorMessage(error: Throwable): String =
    error.message ?: "Could not load smart playlist rules."

fun smartPlaylistSavingStatus(definition: SmartPlaylistDefinition): String =
    "Saving ${definition.name}..."

fun smartPlaylistUpdatingStatus(definition: SmartPlaylistDefinition): String =
    "Updating ${definition.name}..."

fun smartPlaylistLoadingRulesStatus(playlist: Playlist): String =
    "Loading ${playlist.name} rules..."

fun smartPlaylistSavedStatus(displayPlaylist: Playlist, trackCount: Int): String =
    "Saved smart playlist ${displayPlaylist.name} with $trackCount tracks."

fun smartPlaylistUpdatedStatus(displayPlaylist: Playlist, trackCount: Int): String =
    "Updated smart playlist ${displayPlaylist.name} with $trackCount tracks."
