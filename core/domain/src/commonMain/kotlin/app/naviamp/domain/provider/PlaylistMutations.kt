package app.naviamp.domain.provider

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
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

data class QueuePlaylistSaveStateUpdate(
    val playlists: List<Playlist>,
    val status: String,
)

data class PlaylistRenameRefresh(
    val requestedName: String,
    val playlists: List<Playlist>,
)

data class PlaylistRenameSelectionApplication(
    val selectedPlaylist: Playlist?,
)

data class PlaylistRenameStateUpdate(
    val playlists: List<Playlist>,
    val selectionApplication: PlaylistRenameSelectionApplication?,
    val status: String,
)

data class PlaylistDeleteRefresh(
    val playlists: List<Playlist>,
)

data class PlaylistDeleteSelectionApplication(
    val selectedPlaylist: Playlist?,
    val selectedPlaylistTracks: List<Track>,
)

data class PlaylistDeleteApplicationUpdate(
    val playlists: List<Playlist>,
    val playlistTracksById: Map<String, List<Track>>,
    val recentPlaylistIds: List<String>,
    val selectionApplication: PlaylistDeleteSelectionApplication?,
    val status: String,
)

data class SmartPlaylistMutationStateUpdate(
    val playlists: List<Playlist>,
    val displayPlaylist: Playlist,
    val tracks: List<Track>,
    val playlistTracksById: Map<String, List<Track>>,
    val selectionApplication: PlaylistDetailsSelectionApplication?,
    val status: String,
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

data class AddToPlaylistStateUpdate(
    val playlists: List<Playlist>?,
    val closeDialog: Boolean,
    val addToPlaylistStatus: String?,
    val connectionStatus: String?,
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

data class PlaylistDetailsOpenPlan(
    val recentPlaylistIds: List<String>,
    val loadingStatus: String,
)

data class PlaylistDetailsSelectionApplication(
    val playlist: Playlist,
    val tracks: List<Track>,
    val status: String?,
)

data class PlaylistDetailsApplicationUpdate(
    val playlists: List<Playlist>,
    val playlistTracksById: Map<String, List<Track>>,
    val selectionApplication: PlaylistDetailsSelectionApplication?,
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

data class PlaylistListRefresh(
    val playlists: List<Playlist>,
    val playlistsToPreload: List<Playlist>,
)

data class PlaylistListStateUpdate(
    val playlists: List<Playlist>,
    val playlistsToPreload: List<Playlist>,
    val status: String?,
)

enum class PlaylistHomeProjection {
    All,
    RecentLimited,
}

data class PlaylistListApplication(
    val playlists: List<Playlist>,
    val homeContent: HomeContent,
)

data class PlaylistTrackPreloadStateUpdate(
    val playlistTracksById: Map<String, List<Track>>,
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

data class PlaylistPlaybackStartPlan(
    val action: PendingPlaybackAction,
    val shouldStart: Boolean,
    val status: String,
)

data class PlaylistPlaybackTrackLoadPlan(
    val shouldLoadTracks: Boolean,
)

data class PlaylistPlaybackReadyPlan(
    val tracks: List<Track>,
    val firstTrack: Track?,
    val recentPlaylistIds: List<String>,
    val emptyStatus: String,
)

data class PlaylistPlaybackPreparation(
    val loadedTracks: List<Track>,
    val shouldStoreLoadedTracks: Boolean,
    val readyPlan: PlaylistPlaybackReadyPlan,
)

data class PlaylistPlaybackApplicationUpdate(
    val playlistTracksById: Map<String, List<Track>>,
    val loadedTracksToStore: List<Track>?,
    val firstTrack: Track?,
    val playbackTracks: List<Track>,
    val recentPlaylistIds: List<String>,
    val status: String?,
)

data class PlaylistDetailPlaybackApplicationUpdate(
    val playlistTracksById: Map<String, List<Track>>,
    val loadedTracksToStore: List<Track>?,
    val firstTrack: Track?,
    val playbackTracks: List<Track>,
    val playbackIndex: Int,
    val recentPlaylistIds: List<String>,
    val status: String?,
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

fun HomeContent.withPlaylists(
    playlists: List<Playlist>,
    recentPlaylistIds: List<String>,
): HomeContent =
    copy(playlists = homePlaylists(playlists, recentPlaylistIds))

fun HomeContent.withAllPlaylists(playlists: List<Playlist>): HomeContent =
    copy(playlists = playlists)

fun playlistListApplication(
    playlists: List<Playlist>,
    currentHomeContent: HomeContent,
    recentPlaylistIds: List<String>,
    projection: PlaylistHomeProjection,
): PlaylistListApplication =
    PlaylistListApplication(
        playlists = playlists,
        homeContent = when (projection) {
            PlaylistHomeProjection.All -> currentHomeContent.withAllPlaylists(playlists)
            PlaylistHomeProjection.RecentLimited -> currentHomeContent.withPlaylists(playlists, recentPlaylistIds)
        },
    )

fun recentPlaylistIdsAfterPlayed(
    recentPlaylistIds: List<String>,
    playlistId: String,
    limit: Int,
): List<String> =
    (listOf(playlistId) + recentPlaylistIds.filterNot { it == playlistId }).take(limit)

fun playlistDetailsLoadingStatus(playlist: Playlist): String =
    "Loading ${playlist.name}..."

fun playlistDetailsLoadedStatus(): String =
    "Connected."

fun playlistDetailsErrorMessage(error: Throwable): String =
    error.message ?: "Playlist failed to load."

fun playlistDetailsOpenPlan(
    playlist: Playlist,
    recentPlaylistIds: List<String>,
    recentPlaylistLimit: Int,
): PlaylistDetailsOpenPlan =
    PlaylistDetailsOpenPlan(
        recentPlaylistIds = recentPlaylistIdsAfterPlayed(recentPlaylistIds, playlist.id, recentPlaylistLimit),
        loadingStatus = playlistDetailsLoadingStatus(playlist),
    )

fun playlistsNeedingTrackPreload(
    playlists: List<Playlist>,
    playlistTracksById: Map<String, List<Track>>,
    limit: Int = 100,
): List<Playlist> =
    playlists.take(limit).filter { playlist ->
        playlistTracksById[playlist.id].isNullOrEmpty()
    }

fun playlistListRefresh(
    playlists: List<Playlist>,
    playlistTracksById: Map<String, List<Track>>,
    preloadLimit: Int = 100,
): PlaylistListRefresh =
    PlaylistListRefresh(
        playlists = playlists,
        playlistsToPreload = playlistsNeedingTrackPreload(
            playlists = playlists,
            playlistTracksById = playlistTracksById,
            limit = preloadLimit,
        ),
    )

fun playlistListLoadingStatus(): String =
    "Loading playlists..."

fun playlistListErrorMessage(error: Throwable): String =
    error.message ?: "Could not load playlists."

fun playlistListStateUpdate(refresh: PlaylistListRefresh): PlaylistListStateUpdate =
    PlaylistListStateUpdate(
        playlists = refresh.playlists,
        playlistsToPreload = refresh.playlistsToPreload,
        status = null,
    )

fun playlistPreloadTargets(
    playlists: List<Playlist>,
    playlistTracksById: Map<String, List<Track>>,
    preloadLimit: Int = 100,
): List<Playlist> =
    playlistListRefresh(
        playlists = playlists,
        playlistTracksById = playlistTracksById,
        preloadLimit = preloadLimit,
    ).playlistsToPreload

fun playlistTrackPreloadStateUpdate(
    currentPlaylistTracksById: Map<String, List<Track>>,
    playlist: Playlist,
    tracks: List<Track>,
): PlaylistTrackPreloadStateUpdate =
    PlaylistTrackPreloadStateUpdate(
        playlistTracksById = currentPlaylistTracksById + (playlist.id to tracks),
    )

suspend fun MediaProvider.refreshPlaylistsAndPlanPreload(
    providerResponseService: ProviderResponseService? = null,
    useCache: Boolean = true,
    playlistLimit: Int = 500,
    preloadLimit: Int = 100,
    playlistTracksById: Map<String, List<Track>> = emptyMap(),
): PlaylistListRefresh {
    val refreshedPlaylists = if (useCache) {
        providerResponseService?.playlists(this, limit = playlistLimit)
            ?: playlists(limit = playlistLimit)
    } else {
        playlists(limit = playlistLimit)
    }
    return playlistListRefresh(
        playlists = refreshedPlaylists,
        playlistTracksById = playlistTracksById,
        preloadLimit = preloadLimit,
    )
}

suspend fun MediaProvider.refreshPlaylistListState(
    providerResponseService: ProviderResponseService? = null,
    useCache: Boolean = true,
    playlistLimit: Int = 500,
    preloadLimit: Int = 100,
    playlistTracksById: Map<String, List<Track>> = emptyMap(),
): PlaylistListStateUpdate =
    playlistListStateUpdate(
        refreshPlaylistsAndPlanPreload(
            providerResponseService = providerResponseService,
            useCache = useCache,
            playlistLimit = playlistLimit,
            preloadLimit = preloadLimit,
            playlistTracksById = playlistTracksById,
        ),
    )

suspend fun MediaProvider.loadPlaylistTracksForPreload(
    playlist: Playlist,
    providerResponseService: ProviderResponseService? = null,
    useCache: Boolean = true,
): List<Track> =
    if (useCache) {
        providerResponseService?.playlistTracks(this, playlist.id)
            ?: playlistTracks(playlist.id)
    } else {
        playlistTracks(playlist.id)
    }

suspend fun MediaProvider.loadPlaylistTrackPreloadState(
    playlist: Playlist,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    useCache: Boolean = true,
): PlaylistTrackPreloadStateUpdate =
    playlistTrackPreloadStateUpdate(
        currentPlaylistTracksById = currentPlaylistTracksById,
        playlist = playlist,
        tracks = loadPlaylistTracksForPreload(
            playlist = playlist,
            providerResponseService = providerResponseService,
            useCache = useCache,
        ),
    )

suspend fun MediaProvider.preloadPlaylistTracksStateUpdate(
    playlists: List<Playlist>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    useCache: Boolean = true,
    preloadLimit: Int = 100,
): PlaylistTrackPreloadStateUpdate {
    var playlistTracksById = currentPlaylistTracksById
    playlistPreloadTargets(
        playlists = playlists,
        playlistTracksById = playlistTracksById,
        preloadLimit = preloadLimit,
    ).forEach { playlist ->
        runCatching {
            loadPlaylistTracksForPreload(
                playlist = playlist,
                providerResponseService = providerResponseService,
                useCache = useCache,
            )
        }.onSuccess { tracks ->
            playlistTracksById = playlistTrackPreloadStateUpdate(
                currentPlaylistTracksById = playlistTracksById,
                playlist = playlist,
                tracks = tracks,
            ).playlistTracksById
        }
    }
    return PlaylistTrackPreloadStateUpdate(playlistTracksById)
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

fun playlistDetailsApplicationUpdate(
    currentSelectedPlaylist: Playlist?,
    currentSelectedPlaylistTracks: List<Track>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    refresh: PlaylistDetailsRefresh,
    requestedPlaylistId: String,
    status: String? = null,
): PlaylistDetailsApplicationUpdate {
    val selectedPlaylistChanged = currentSelectedPlaylist?.id == requestedPlaylistId
    val stateUpdate = playlistDetailsStateUpdate(
        currentSelectedPlaylist = currentSelectedPlaylist,
        currentSelectedPlaylistTracks = currentSelectedPlaylistTracks,
        currentPlaylistTracksById = currentPlaylistTracksById,
        refresh = refresh,
        requestedPlaylistId = requestedPlaylistId,
    )
    return PlaylistDetailsApplicationUpdate(
        playlists = stateUpdate.playlists,
        playlistTracksById = stateUpdate.playlistTracksById,
        selectionApplication = if (selectedPlaylistChanged) {
            PlaylistDetailsSelectionApplication(
                playlist = requireNotNull(stateUpdate.selectedPlaylist),
                tracks = stateUpdate.selectedPlaylistTracks,
                status = status,
            )
        } else {
            null
        },
    )
}

suspend fun MediaProvider.refreshPlaylistDetailsApplication(
    playlist: Playlist,
    currentSelectedPlaylist: Playlist?,
    currentSelectedPlaylistTracks: List<Track>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
    status: String? = null,
): PlaylistDetailsApplicationUpdate {
    val refresh = refreshPlaylistDetails(
        playlist = playlist,
        playlistLimit = playlistLimit,
        providerResponseService = providerResponseService,
    )
    return playlistDetailsApplicationUpdate(
        currentSelectedPlaylist = currentSelectedPlaylist,
        currentSelectedPlaylistTracks = currentSelectedPlaylistTracks,
        currentPlaylistTracksById = currentPlaylistTracksById,
        refresh = refresh,
        requestedPlaylistId = playlist.id,
        status = status,
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

fun playlistPlaybackStartPlan(
    playlist: Playlist,
    shuffle: Boolean,
    pending: PendingPlaybackAction?,
): PlaylistPlaybackStartPlan {
    val action = playlistPlaybackAction(playlist, shuffle)
    return PlaylistPlaybackStartPlan(
        action = action,
        shouldStart = shouldStartPlaybackAction(pending),
        status = pending?.status ?: action.status,
    )
}

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

fun playlistPlaybackTrackLoadPlan(
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    playlist: Playlist,
): PlaylistPlaybackTrackLoadPlan =
    PlaylistPlaybackTrackLoadPlan(
        shouldLoadTracks = selectedPlaylist?.id != playlist.id || selectedPlaylistTracks.isEmpty(),
    )

suspend fun MediaProvider.loadPlaylistTracksForPlayback(
    playlist: Playlist,
    providerResponseService: ProviderResponseService? = null,
): List<Track> =
    providerResponseService?.playlistTracks(this, playlist.id)
        ?: playlistTracks(playlist.id)

suspend fun MediaProvider.preparePlaylistPlayback(
    playlist: Playlist,
    shuffle: Boolean,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    recentPlaylistIds: List<String>,
    recentPlaylistLimit: Int,
    providerResponseService: ProviderResponseService? = null,
    emptyStatus: String = "Playlist is empty.",
): PlaylistPlaybackPreparation {
    val loadPlan = playlistPlaybackTrackLoadPlan(selectedPlaylist, selectedPlaylistTracks, playlist)
    val loadedTracks = if (loadPlan.shouldLoadTracks) {
        loadPlaylistTracksForPlayback(playlist, providerResponseService)
    } else {
        emptyList()
    }
    return PlaylistPlaybackPreparation(
        loadedTracks = loadedTracks,
        shouldStoreLoadedTracks = loadPlan.shouldLoadTracks,
        readyPlan = playlistPlaybackReadyPlan(
            playlist = playlist,
            shuffle = shuffle,
            selectedPlaylist = selectedPlaylist,
            selectedPlaylistTracks = selectedPlaylistTracks,
            loadedTracks = loadedTracks,
            recentPlaylistIds = recentPlaylistIds,
            recentPlaylistLimit = recentPlaylistLimit,
            emptyStatus = emptyStatus,
        ),
    )
}

fun playlistPlaybackApplicationUpdate(
    playlist: Playlist,
    preparation: PlaylistPlaybackPreparation,
    currentPlaylistTracksById: Map<String, List<Track>>,
): PlaylistPlaybackApplicationUpdate {
    val playlistTracksById = if (preparation.shouldStoreLoadedTracks) {
        currentPlaylistTracksById + (playlist.id to preparation.loadedTracks)
    } else {
        currentPlaylistTracksById
    }
    val readyPlan = preparation.readyPlan
    return PlaylistPlaybackApplicationUpdate(
        playlistTracksById = playlistTracksById,
        loadedTracksToStore = preparation.loadedTracks.takeIf { preparation.shouldStoreLoadedTracks },
        firstTrack = readyPlan.firstTrack,
        playbackTracks = readyPlan.tracks,
        recentPlaylistIds = readyPlan.recentPlaylistIds,
        status = if (readyPlan.firstTrack == null) readyPlan.emptyStatus else null,
    )
}

fun playlistPlaybackErrorMessage(error: Throwable, playlist: Playlist): String =
    error.message ?: "Could not play ${playlist.name}."

suspend fun MediaProvider.preparePlaylistPlaybackApplication(
    playlist: Playlist,
    shuffle: Boolean,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    recentPlaylistIds: List<String>,
    recentPlaylistLimit: Int,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    emptyStatus: String = "Playlist is empty.",
): PlaylistPlaybackApplicationUpdate =
    playlistPlaybackApplicationUpdate(
        playlist = playlist,
        preparation = preparePlaylistPlayback(
            playlist = playlist,
            shuffle = shuffle,
            selectedPlaylist = selectedPlaylist,
            selectedPlaylistTracks = selectedPlaylistTracks,
            recentPlaylistIds = recentPlaylistIds,
            recentPlaylistLimit = recentPlaylistLimit,
            providerResponseService = providerResponseService,
            emptyStatus = emptyStatus,
        ),
        currentPlaylistTracksById = currentPlaylistTracksById,
    )

fun playlistDetailPlaybackApplicationUpdate(
    playlist: Playlist,
    preparation: PlaylistPlaybackPreparation,
    currentPlaylistTracksById: Map<String, List<Track>>,
    requestedIndex: Int = 0,
): PlaylistDetailPlaybackApplicationUpdate {
    val update = playlistPlaybackApplicationUpdate(
        playlist = playlist,
        preparation = preparation,
        currentPlaylistTracksById = currentPlaylistTracksById,
    )
    return PlaylistDetailPlaybackApplicationUpdate(
        playlistTracksById = update.playlistTracksById,
        loadedTracksToStore = update.loadedTracksToStore,
        firstTrack = update.firstTrack,
        playbackTracks = update.playbackTracks,
        playbackIndex = if (update.playbackTracks.isEmpty()) 0 else requestedIndex.coerceIn(update.playbackTracks.indices),
        recentPlaylistIds = update.recentPlaylistIds,
        status = update.status,
    )
}

suspend fun MediaProvider.preparePlaylistDetailPlaybackApplication(
    playlist: Playlist,
    shuffle: Boolean,
    selectedPlaylistTracks: List<Track>,
    recentPlaylistIds: List<String>,
    recentPlaylistLimit: Int,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    requestedIndex: Int = 0,
    emptyStatus: String = "Playlist is empty.",
): PlaylistDetailPlaybackApplicationUpdate =
    playlistDetailPlaybackApplicationUpdate(
        playlist = playlist,
        preparation = preparePlaylistPlayback(
            playlist = playlist,
            shuffle = shuffle,
            selectedPlaylist = playlist,
            selectedPlaylistTracks = selectedPlaylistTracks,
            recentPlaylistIds = recentPlaylistIds,
            recentPlaylistLimit = recentPlaylistLimit,
            providerResponseService = providerResponseService,
            emptyStatus = emptyStatus,
        ),
        currentPlaylistTracksById = currentPlaylistTracksById,
        requestedIndex = requestedIndex,
    )

fun playlistPlaybackReadyPlan(
    playlist: Playlist,
    shuffle: Boolean,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    loadedTracks: List<Track>,
    recentPlaylistIds: List<String>,
    recentPlaylistLimit: Int,
    emptyStatus: String = "Playlist is empty.",
): PlaylistPlaybackReadyPlan {
    val playbackTracks = selectedPlaylistTracksForPlayback(
        selectedPlaylist = selectedPlaylist,
        selectedPlaylistTracks = selectedPlaylistTracks,
        playlist = playlist,
        loadedTracks = loadedTracks,
    )
    val tracks = playlistPlaybackTracks(playbackTracks, shuffle)
    return PlaylistPlaybackReadyPlan(
        tracks = tracks,
        firstTrack = tracks.firstOrNull(),
        recentPlaylistIds = recentPlaylistIdsAfterPlayed(
            recentPlaylistIds = recentPlaylistIds,
            playlistId = playlist.id,
            limit = recentPlaylistLimit,
        ),
        emptyStatus = emptyStatus,
    )
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

suspend fun MediaProvider.addTracksToPlaylistStateUpdate(
    playlistId: String?,
    playlistName: String?,
    newPlaylistName: String?,
    tracks: List<Track>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): AddToPlaylistStateUpdate {
    val uniqueTracks = tracks.distinctBy { it.id }
    if (uniqueTracks.isEmpty()) {
        return AddToPlaylistStateUpdate(
            playlists = null,
            closeDialog = false,
            addToPlaylistStatus = "No tracks found.",
            connectionStatus = null,
        )
    }
    val refresh = addTracksToPlaylistAndRefresh(
        playlistId = playlistId,
        playlistName = playlistName,
        newPlaylistName = newPlaylistName,
        tracks = uniqueTracks,
        providerResponseService = providerResponseService,
        playlistLimit = playlistLimit,
    )
    return addToPlaylistStateUpdate(refresh)
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

suspend fun MediaProvider.saveQueueAsPlaylistStateUpdate(
    name: String,
    tracks: List<Track>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): QueuePlaylistSaveStateUpdate =
    queuePlaylistSaveStateUpdate(
        saveQueueAsPlaylistAndRefresh(
            name = name,
            tracks = tracks,
            providerResponseService = providerResponseService,
            playlistLimit = playlistLimit,
        ),
    )

fun queuePlaylistSaveLoadingStatus(): String =
    "Saving queue as playlist..."

fun queuePlaylistSaveErrorMessage(error: Throwable): String =
    error.message ?: "Could not save queue as playlist."

fun queuePlaylistSaveStateUpdate(refresh: QueuePlaylistSaveRefresh): QueuePlaylistSaveStateUpdate =
    QueuePlaylistSaveStateUpdate(
        playlists = refresh.playlists,
        status = "Saved ${refresh.result.playlist.name} with ${refresh.result.trackCount} tracks.",
    )

suspend fun MediaProvider.renamePlaylistAndRefresh(
    playlist: Playlist,
    name: String,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): PlaylistRenameRefresh {
    val requestedName = normalizedPlaylistName(name)
    renamePlaylist(playlist.id, requestedName)
    providerResponseService?.invalidatePlaylistResponses(this, playlist.id)
    val playlists = providerResponseService?.playlists(this, limit = playlistLimit)
        ?: playlists(limit = playlistLimit)
    return PlaylistRenameRefresh(requestedName = requestedName, playlists = playlists)
}

suspend fun MediaProvider.deletePlaylistAndRefresh(
    playlist: Playlist,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): PlaylistDeleteRefresh {
    deletePlaylist(playlist.id)
    providerResponseService?.invalidatePlaylistResponses(this, playlist.id)
    val playlists = providerResponseService?.playlists(this, limit = playlistLimit)
        ?: playlists(limit = playlistLimit)
    return PlaylistDeleteRefresh(playlists = playlists)
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

fun addToPlaylistStateUpdate(refresh: AddToPlaylistRefresh): AddToPlaylistStateUpdate =
    AddToPlaylistStateUpdate(
        playlists = refresh.playlists,
        closeDialog = refresh.update.closeDialog,
        addToPlaylistStatus = refresh.update.addToPlaylistStatus,
        connectionStatus = refresh.update.connectionStatus,
    )

fun addToPlaylistLoadingStatus(label: String): String =
    "Adding $label to playlist..."

fun addToPlaylistResolvingTracksStatus(): String =
    "Loading tracks..."

fun addToPlaylistErrorMessage(error: Throwable, label: String): String =
    error.message ?: "Could not add $label to playlist."

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

fun playlistRenameStateUpdate(
    currentSelectedPlaylist: Playlist?,
    refresh: PlaylistRenameRefresh,
    playlistId: String,
): PlaylistRenameStateUpdate {
    val selectedPlaylistChanged = currentSelectedPlaylist?.id == playlistId
    val selectedPlaylist = renamedSelectedPlaylist(
        current = currentSelectedPlaylist,
        playlistId = playlistId,
        requestedName = refresh.requestedName,
        refreshedPlaylists = refresh.playlists,
    )
    return PlaylistRenameStateUpdate(
        playlists = refresh.playlists,
        selectionApplication = if (selectedPlaylistChanged) {
            PlaylistRenameSelectionApplication(selectedPlaylist)
        } else {
            null
        },
        status = playlistRenamedStatus(),
    )
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

fun playlistDeleteApplicationUpdate(
    refresh: PlaylistDeleteRefresh,
    currentSelectedPlaylist: Playlist?,
    currentSelectedPlaylistTracks: List<Track>,
    currentPlaylistTracksById: Map<String, List<Track>>,
    currentRecentPlaylistIds: List<String>,
    deletedPlaylistId: String,
): PlaylistDeleteApplicationUpdate {
    val stateUpdate = playlistDeleteStateUpdate(
        currentSelectedPlaylist = currentSelectedPlaylist,
        currentSelectedPlaylistTracks = currentSelectedPlaylistTracks,
        currentPlaylistTracksById = currentPlaylistTracksById,
        currentRecentPlaylistIds = currentRecentPlaylistIds,
        deletedPlaylistId = deletedPlaylistId,
    )
    return PlaylistDeleteApplicationUpdate(
        playlists = refresh.playlists,
        playlistTracksById = stateUpdate.playlistTracksById,
        recentPlaylistIds = stateUpdate.recentPlaylistIds,
        selectionApplication = if (stateUpdate.deletedSelectedPlaylist) {
            PlaylistDeleteSelectionApplication(
                selectedPlaylist = stateUpdate.selectedPlaylist,
                selectedPlaylistTracks = stateUpdate.selectedPlaylistTracks,
            )
        } else {
            null
        },
        status = playlistDeletedStatus(),
    )
}

suspend fun saveSmartPlaylistAndRefresh(
    provider: MediaProvider,
    definition: SmartPlaylistDefinition,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): PlaylistDetailsRefresh {
    val playlist = provider.createSmartPlaylist(definition)
    providerResponseService?.invalidatePlaylistResponses(provider, playlist.id)
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
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): PlaylistDetailsRefresh {
    provider.updateSmartPlaylist(playlist.id, definition)
    providerResponseService?.invalidatePlaylistResponses(provider, playlist.id)
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

fun smartPlaylistSaveStateUpdate(
    refresh: PlaylistDetailsRefresh,
    currentPlaylistTracksById: Map<String, List<Track>>,
): SmartPlaylistMutationStateUpdate =
    SmartPlaylistMutationStateUpdate(
        playlists = refresh.playlists,
        displayPlaylist = refresh.displayPlaylist,
        tracks = refresh.tracks,
        playlistTracksById = currentPlaylistTracksById + (refresh.displayPlaylist.id to refresh.tracks),
        selectionApplication = null,
        status = smartPlaylistSavedStatus(refresh.displayPlaylist, refresh.tracks.size),
    )

suspend fun saveSmartPlaylistStateUpdate(
    provider: MediaProvider,
    definition: SmartPlaylistDefinition,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): SmartPlaylistMutationStateUpdate =
    smartPlaylistSaveStateUpdate(
        refresh = saveSmartPlaylistAndRefresh(
            provider = provider,
            definition = definition,
            providerResponseService = providerResponseService,
            playlistLimit = playlistLimit,
        ),
        currentPlaylistTracksById = currentPlaylistTracksById,
    )

fun smartPlaylistUpdateStateUpdate(
    refresh: PlaylistDetailsRefresh,
    currentSelectedPlaylist: Playlist?,
    currentPlaylistTracksById: Map<String, List<Track>>,
): SmartPlaylistMutationStateUpdate {
    val selectedPlaylistChanged = currentSelectedPlaylist?.id == refresh.displayPlaylist.id
    return SmartPlaylistMutationStateUpdate(
        playlists = refresh.playlists,
        displayPlaylist = refresh.displayPlaylist,
        tracks = refresh.tracks,
        playlistTracksById = currentPlaylistTracksById + (refresh.displayPlaylist.id to refresh.tracks),
        selectionApplication = if (selectedPlaylistChanged) {
            PlaylistDetailsSelectionApplication(
                playlist = refresh.displayPlaylist,
                tracks = refresh.tracks,
                status = null,
            )
        } else {
            null
        },
        status = smartPlaylistUpdatedStatus(refresh.displayPlaylist, refresh.tracks.size),
    )
}

suspend fun updateSmartPlaylistStateUpdate(
    provider: MediaProvider,
    playlist: Playlist,
    definition: SmartPlaylistDefinition,
    currentSelectedPlaylist: Playlist?,
    currentPlaylistTracksById: Map<String, List<Track>>,
    providerResponseService: ProviderResponseService? = null,
    playlistLimit: Int = 500,
): SmartPlaylistMutationStateUpdate =
    smartPlaylistUpdateStateUpdate(
        refresh = updateSmartPlaylistAndRefresh(
            provider = provider,
            playlist = playlist,
            definition = definition,
            providerResponseService = providerResponseService,
            playlistLimit = playlistLimit,
        ),
        currentSelectedPlaylist = currentSelectedPlaylist,
        currentPlaylistTracksById = currentPlaylistTracksById,
    )

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
