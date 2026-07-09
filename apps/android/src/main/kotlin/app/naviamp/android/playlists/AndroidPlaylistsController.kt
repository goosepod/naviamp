package app.naviamp.android

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.PlaylistHomeProjection
import app.naviamp.domain.provider.PlaylistListApplication
import app.naviamp.domain.provider.playlistDeleteApplication
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteApplicationUpdate
import app.naviamp.domain.provider.playlistDetailsErrorMessage
import app.naviamp.domain.provider.playlistDetailsLoadedStatus
import app.naviamp.domain.provider.playlistDetailsLoadingStatus
import app.naviamp.domain.provider.playlistDetailsOpenPlan
import app.naviamp.domain.provider.playlistListApplication
import app.naviamp.domain.provider.playlistPlaybackCompletionApplication
import app.naviamp.domain.provider.playlistPlaybackErrorMessage
import app.naviamp.domain.provider.playlistPlaybackPreparedApplication
import app.naviamp.domain.provider.playlistPlaybackStartApplication
import app.naviamp.domain.provider.playlistPlaybackStartPlan
import app.naviamp.domain.provider.preparePlaylistPlaybackApplication
import app.naviamp.domain.provider.playlistRenameApplication
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenameStateUpdate
import app.naviamp.domain.provider.queuePlaylistSaveErrorMessage
import app.naviamp.domain.provider.queuePlaylistSaveLoadingStatus
import app.naviamp.domain.provider.refreshPlaylistDetailsApplication
import app.naviamp.domain.provider.refreshPlaylistListState
import app.naviamp.domain.provider.renamePlaylistAndRefresh
import app.naviamp.domain.provider.saveQueueAsPlaylistApplication
import app.naviamp.domain.provider.selectedPlaylistTracksForPlayback
import app.naviamp.domain.provider.loadSmartPlaylistDefinition
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.saveSmartPlaylistStateUpdate
import app.naviamp.domain.provider.smartPlaylistSavingStatus
import app.naviamp.domain.provider.smartPlaylistUpdateErrorMessage
import app.naviamp.domain.provider.smartPlaylistUpdatingStatus
import app.naviamp.domain.provider.deletePlaylistAndRefresh
import app.naviamp.domain.provider.preloadPlaylistTracksStateUpdate
import app.naviamp.domain.provider.updateSmartPlaylistStateUpdate
import app.naviamp.domain.source.visibleServerConnections
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun refreshAndroidPlaylistDetailsFromServer(
    state: AndroidAppState,
    activeProvider: NavidromeProvider,
    playlist: Playlist,
    showLoadingStatus: Boolean,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    with(state) {
        if (showLoadingStatus) status = playlistDetailsLoadingStatus(playlist)
        val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
        val update = withContext(Dispatchers.IO) {
            activeProvider.refreshPlaylistDetailsApplication(
                playlist = playlist,
                currentSelectedPlaylist = selectedPlaylist,
                currentSelectedPlaylistTracks = selectedPlaylistTracks,
                currentPlaylistTracksById = playlistTracksById,
                providerResponseService = providerResponseService,
                status = if (showLoadingStatus) playlistDetailsLoadedStatus() else null,
            )
        }
        applyAndroidPlaylistListApplication(state, update.playlists)
        playlistTracksById = update.playlistTracksById
        update.selectionApplication?.let { selection ->
            contentState = contentState.showPlaylist(selection.playlist, selection.tracks)
            tracks = selection.tracks
            selection.status?.let { status = it }
        }
    }
}

fun openAndroidPlaylistDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val openPlan = playlistDetailsOpenPlan(playlist, state.recentPlaylistIds, recentPlaylistLimit = 20)
    with(state) {
        contentState = contentState.showPlaylist(playlist)
        navigationState = navigationState.copy(route = NaviampRoute.Playlists)
        nowPlayingOpen = false
        recentPlaylistIds = openPlan.recentPlaylistIds
    }
    scope.launch {
        with(state) {
            status = openPlan.loadingStatus
            runCatching {
                refreshAndroidPlaylistDetailsFromServer(
                    state = state,
                    activeProvider = activeProvider,
                    playlist = playlist,
                    showLoadingStatus = true,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                )
            }.onFailure { error ->
                contentState = contentState.showPlaylist(playlist)
                status = playlistDetailsErrorMessage(error)
            }
        }
    }
}

fun playAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    shuffle: Boolean,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val startPlan = playlistPlaybackStartPlan(playlist, shuffle, state.pendingPlaybackAction)
    val startApplication = playlistPlaybackStartApplication(startPlan)
    with(state) {
        if (!startPlan.shouldStart) {
            playlistActionStatus = startApplication.status
            status = startApplication.status
            return
        }
        pendingPlaybackAction = startApplication.pendingPlaybackAction
        playlistActionStatus = startApplication.status
        status = startApplication.status
    }
    scope.launch {
        with(state) {
            try {
                val update = runCatching {
                    activeProvider.preparePlaylistPlaybackApplication(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylist = selectedPlaylist,
                        selectedPlaylistTracks = selectedPlaylistTracks,
                        recentPlaylistIds = recentPlaylistIds,
                        recentPlaylistLimit = 20,
                        currentPlaylistTracksById = playlistTracksById,
                        providerResponseService = providerResponseService,
                    )
                }.getOrElse { error ->
                    status = playlistPlaybackErrorMessage(error, playlist)
                    return@launch
                }
                val prepared = playlistPlaybackPreparedApplication(update)
                playlistTracksById = prepared.playlistTracksById
                playlistActionStatus = null
                prepared.status?.let { status = it }
                prepared.loadedTracksToStore?.let { loadedTracks ->
                    contentState = contentState.showPlaylist(playlist, loadedTracks)
                    tracks = loadedTracks
                }
                prepared.playbackWork?.let { work ->
                    recentPlaylistIds = work.recentPlaylistIds
                    playTrack(work.firstTrack, work.playbackTracks)
                }
            } finally {
                pendingPlaybackAction = playlistPlaybackCompletionApplication(
                    pending = pendingPlaybackAction,
                    completed = startPlan.action,
                ).pendingPlaybackAction
            }
        }
    }
}

fun renameAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    name: String,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = playlistRenameLoadingStatus(playlist)
            runCatching {
                activeProvider.renamePlaylistAndRefresh(
                    playlist = playlist,
                    name = name,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                val update = playlistRenameStateUpdate(selectedPlaylist, refresh, playlist.id)
                val application = playlistRenameApplication(
                    update = update,
                    currentHomeContent = homeState,
                    recentPlaylistIds = recentPlaylistIds,
                    projection = PlaylistHomeProjection.All,
                )
                applyAndroidPlaylistListApplication(state, application.playlistListApplication)
                application.selectionApplication?.let { selection ->
                    contentState = contentState.copy(selectedPlaylist = selection.selectedPlaylist)
                }
                status = application.status
            }.onFailure { error ->
                status = playlistRenameErrorMessage(error)
            }
        }
    }
}

fun deleteAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = playlistDeleteLoadingStatus(playlist)
            runCatching {
                activeProvider.deletePlaylistAndRefresh(
                    playlist = playlist,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                val update = playlistDeleteApplicationUpdate(
                    refresh = refresh,
                    currentSelectedPlaylist = selectedPlaylist,
                    currentSelectedPlaylistTracks = selectedPlaylistTracks,
                    currentPlaylistTracksById = playlistTracksById,
                    currentRecentPlaylistIds = recentPlaylistIds,
                    deletedPlaylistId = playlist.id,
                )
                val application = playlistDeleteApplication(
                    update = update,
                    currentHomeContent = homeState,
                    projection = PlaylistHomeProjection.All,
                )
                applyAndroidPlaylistListApplication(state, application.playlistListApplication)
                application.selectionApplication?.let { selection ->
                    contentState = contentState.copy(
                        selectedPlaylist = selection.selectedPlaylist,
                        selectedPlaylistTracks = selection.selectedPlaylistTracks,
                    )
                }
                playlistTracksById = application.playlistTracksById
                recentPlaylistIds = application.recentPlaylistIds
                status = application.status
            }.onFailure { error ->
                status = playlistDeleteErrorMessage(error)
            }
        }
    }
}

fun preloadAndroidPlaylistTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    activeProvider: NavidromeProvider,
    playlists: List<Playlist>,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        state.playlistTracksById = activeProvider.preloadPlaylistTracksStateUpdate(
            playlists = playlists,
            currentPlaylistTracksById = state.playlistTracksById,
            providerResponseService = providerResponseService,
        ).playlistTracksById
    }
}

fun withAndroidPlaylistTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    onTracks: (List<Track>) -> Unit,
) {
    val knownTracks = selectedPlaylistTracksForPlayback(
        selectedPlaylist = state.selectedPlaylist,
        selectedPlaylistTracks = state.selectedPlaylistTracks,
        playlist = playlist,
        loadedTracks = state.playlistTracksById[playlist.id].orEmpty(),
    )
    if (knownTracks.isNotEmpty()) {
        onTracks(knownTracks)
        return
    }
    val activeProvider = state.provider ?: run {
        state.status = "Connect to Navidrome first."
        return
    }
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        state.status = "Loading ${playlist.name}..."
        runCatching {
            withContext(Dispatchers.IO) {
                providerResponseService?.playlistTracks(activeProvider, playlist.id)
                    ?: activeProvider.playlistTracks(playlist.id)
            }
        }.onSuccess { tracks ->
            state.playlistTracksById = state.playlistTracksById + (playlist.id to tracks)
            if (state.selectedPlaylist?.id == playlist.id) {
                state.contentState = state.contentState.showPlaylist(playlist, tracks)
                state.tracks = tracks
            }
            state.status = ""
            onTracks(tracks)
        }.onFailure { error ->
            state.status = error.message ?: "Could not load ${playlist.name}."
        }
    }
}

fun addAndroidPlaylistToQueue(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    appendTracksToQueue: (List<Track>, String) -> Unit,
) {
    withAndroidPlaylistTracks(scope, state, playlist, providerResponseCacheRepository) { tracks ->
        appendTracksToQueue(tracks, playlist.name)
    }
}

fun addAndroidPlaylistToPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    targetPlaylist: NaviampPlaylistChoiceUi?,
    newPlaylistName: String?,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    addTracksToPlaylist: (List<Track>, NaviampPlaylistChoiceUi?, String?, String) -> Unit,
) {
    withAndroidPlaylistTracks(scope, state, playlist, providerResponseCacheRepository) { tracks ->
        addTracksToPlaylist(tracks, targetPlaylist, newPlaylistName, playlist.name)
    }
}

fun downloadAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    downloadTracks: (List<Track>, String) -> Unit,
) {
    val activeProvider = state.provider ?: run {
        state.downloadStatus = downloadConnectionRequiredStatus()
        state.status = state.downloadStatus.orEmpty()
        return
    }
    val loadedTracks = selectedPlaylistTracksForPlayback(
        selectedPlaylist = state.selectedPlaylist,
        selectedPlaylistTracks = state.selectedPlaylistTracks,
        playlist = playlist,
        loadedTracks = state.playlistTracksById[playlist.id].orEmpty(),
    )
    if (loadedTracks.isNotEmpty()) {
        downloadTracks(loadedTracks, playlist.name)
        return
    }
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    state.downloadStatus = "Loading ${playlist.name}..."
    state.status = state.downloadStatus.orEmpty()
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                providerResponseService?.playlistTracks(activeProvider, playlist.id)
                    ?: activeProvider.playlistTracks(playlist.id)
            }
        }.onSuccess { tracks ->
            state.playlistTracksById = state.playlistTracksById + (playlist.id to tracks)
            downloadTracks(tracks, playlist.name)
        }.onFailure { error ->
            state.downloadStatus = error.message ?: "Could not load ${playlist.name}."
            state.status = state.downloadStatus.orEmpty()
        }
    }
}

private fun applyAndroidPlaylistListApplication(
    state: AndroidAppState,
    playlists: List<Playlist>,
) {
    applyAndroidPlaylistListApplication(
        state = state,
        application = playlistListApplication(
            playlists = playlists,
            currentHomeContent = state.homeState,
            recentPlaylistIds = state.recentPlaylistIds,
            projection = PlaylistHomeProjection.All,
        ),
    )
}

private fun applyAndroidPlaylistListApplication(
    state: AndroidAppState,
    application: PlaylistListApplication,
) {
    state.homeState = application.homeContent
}

fun refreshAndroidPlaylists(
    scope: CoroutineScope,
    state: AndroidAppState,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    if (state.isPlaylistRefreshing) return
    state.isPlaylistRefreshing = true
    scope.launch {
        try {
            runCatching {
                activeProvider.refreshPlaylistListState(
                    providerResponseService = providerResponseService,
                    useCache = false,
                    playlistTracksById = state.playlistTracksById,
                )
            }.onSuccess { update ->
                applyAndroidPlaylistListApplication(state, update.playlists)
                preloadAndroidPlaylistTracks(
                    scope,
                    state,
                    activeProvider,
                    update.playlists,
                    providerResponseCacheRepository,
                )
            }
        } finally {
            state.isPlaylistRefreshing = false
        }
    }
}

fun saveQueueAsPlaylistFromState(
    scope: CoroutineScope,
    state: AndroidAppState,
    name: String,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    saveTracksAsPlaylistFromState(
        scope = scope,
        state = state,
        name = name,
        tracks = state.playbackQueue.tracks,
        label = "queue",
        providerResponseCacheRepository = providerResponseCacheRepository,
    )
}

fun saveTracksAsPlaylistFromState(
    scope: CoroutineScope,
    state: AndroidAppState,
    name: String,
    tracks: List<Track>,
    label: String,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    state.playlistActionStatus = queuePlaylistSaveLoadingStatus(label)
    scope.launch {
        with(state) {
            runCatching {
                activeProvider.saveQueueAsPlaylistApplication(
                    name = name,
                    tracks = tracks,
                    currentHomeContent = homeState,
                    recentPlaylistIds = recentPlaylistIds,
                    projection = PlaylistHomeProjection.All,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { application ->
                applyAndroidPlaylistListApplication(state, application.playlistListApplication)
                playlistActionStatus = null
                status = application.status
            }.onFailure { error ->
                playlistActionStatus = queuePlaylistSaveErrorMessage(error, label)
                status = playlistActionStatus.orEmpty()
            }
        }
    }
}

private suspend fun refreshAndroidSmartPlaylistProvider(
    state: AndroidAppState,
    storage: AndroidStorageDependencies?,
    passwordOverride: String?,
): NavidromeProvider {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
    val savedConnection = state.savedConnectionForLogin
    if (savedConnection?.nativeToken?.isNotBlank() == true) return activeProvider
    val passwordToUse = passwordOverride?.takeIf { it.isNotBlank() } ?: state.password.takeIf { it.isNotBlank() }
        ?: return activeProvider
    val connection = savedConnection
        ?: throw IllegalStateException("Reconnect to Navidrome with your password before saving smart playlists.")
    val refreshedConnection = withContext(Dispatchers.IO) {
        connection.withNativeTokenFromPassword(passwordToUse, required = true)
    }
    val refreshedProvider = NavidromeProvider(refreshedConnection)
    state.provider = refreshedProvider
    state.savedConnectionForLogin = refreshedConnection
    state.password = ""
    storage?.let { dependencies ->
        val mediaSource = dependencies.upsertProviderMediaSource(
            connection = refreshedConnection.toProviderMediaSourceConnection(),
            cacheNamespace = refreshedProvider.cacheNamespace,
            providerId = refreshedProvider.id.value,
        )
        state.activeSourceId = mediaSource.id
        state.savedMediaSources = dependencies.mediaSources().visibleServerConnections(state.activeSourceId)
    }
    state.status = "Smart playlist authentication refreshed."
    return refreshedProvider
}

suspend fun saveAndroidSmartPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    definition: SmartPlaylistDefinition,
    storage: AndroidStorageDependencies? = null,
    passwordOverride: String? = null,
) {
    val activeProvider = refreshAndroidSmartPlaylistProvider(state, storage, passwordOverride)
    state.status = smartPlaylistSavingStatus(definition)
    try {
        val providerResponseService = storage?.let { ProviderResponseService(it) }
        val update = saveSmartPlaylistStateUpdate(
            provider = activeProvider,
            definition = definition,
            currentPlaylistTracksById = state.playlistTracksById,
            providerResponseService = providerResponseService,
        )
        state.playlistTracksById = update.playlistTracksById
        applyAndroidPlaylistListApplication(state, update.playlists)
        preloadAndroidPlaylistTracks(scope, state, activeProvider, update.playlists, providerResponseCacheRepository = null)
        state.status = update.status
    } catch (error: Exception) {
        state.status = smartPlaylistSaveErrorMessage(error)
        throw error
    }
}

private fun NavidromeConnection.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = resolvedDisplayName(),
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        nativeToken = nativeToken,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )

suspend fun updateAndroidSmartPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    definition: SmartPlaylistDefinition,
    storage: AndroidStorageDependencies? = null,
    passwordOverride: String? = null,
) {
    val activeProvider = refreshAndroidSmartPlaylistProvider(state, storage, passwordOverride)
    state.status = smartPlaylistUpdatingStatus(definition)
    try {
        val providerResponseService = storage?.let { ProviderResponseService(it) }
        val update = updateSmartPlaylistStateUpdate(
            provider = activeProvider,
            playlist = playlist,
            definition = definition,
            currentSelectedPlaylist = state.selectedPlaylist,
            currentPlaylistTracksById = state.playlistTracksById,
            providerResponseService = providerResponseService,
        )
        state.playlistTracksById = update.playlistTracksById
        applyAndroidPlaylistListApplication(state, update.playlists)
        update.selectionApplication?.let { selection ->
            state.contentState = state.contentState.showPlaylist(selection.playlist, selection.tracks)
            state.tracks = selection.tracks
        }
        preloadAndroidPlaylistTracks(scope, state, activeProvider, update.playlists, providerResponseCacheRepository = null)
        state.status = update.status
    } catch (error: Exception) {
        state.status = smartPlaylistUpdateErrorMessage(error)
        throw error
    }
}

suspend fun loadAndroidSmartPlaylistDefinition(
    state: AndroidAppState,
    playlist: Playlist,
): SmartPlaylistDefinition {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before editing smart playlists.")
    return withContext(Dispatchers.IO) {
        activeProvider.loadSmartPlaylistDefinition(playlist)
    }
}

internal class AndroidPlaylistActionController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val playTrack: (Track, List<Track>) -> Unit,
    private val appendTracksToQueue: (List<Track>, String) -> Unit,
) {
    fun openPlaylistDetails(playlist: Playlist) {
        openAndroidPlaylistDetails(scope, state, playlist, storage)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        playAndroidPlaylist(scope, state, playlist, shuffle, playTrack, storage)
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        renameAndroidPlaylist(scope, state, playlist, name, storage)
    }

    fun deletePlaylist(playlist: Playlist) {
        deleteAndroidPlaylist(scope, state, playlist, storage)
    }

    fun preloadPlaylistTracks(activeProvider: NavidromeProvider, playlists: List<Playlist>) {
        preloadAndroidPlaylistTracks(scope, state, activeProvider, playlists, storage)
    }

    fun refreshPlaylists() {
        refreshAndroidPlaylists(scope, state, storage)
    }

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        saveAndroidSmartPlaylist(scope, state, definition, storage)
    }

    suspend fun saveSmartPlaylistWithPassword(definition: SmartPlaylistDefinition, password: String) {
        saveAndroidSmartPlaylist(scope, state, definition, storage, passwordOverride = password)
    }

    suspend fun updateSmartPlaylist(playlist: Playlist, definition: SmartPlaylistDefinition) {
        updateAndroidSmartPlaylist(scope, state, playlist, definition, storage)
    }

    suspend fun updateSmartPlaylistWithPassword(playlist: Playlist, definition: SmartPlaylistDefinition, password: String) {
        updateAndroidSmartPlaylist(scope, state, playlist, definition, storage, passwordOverride = password)
    }

    suspend fun loadSmartPlaylistDefinition(playlist: Playlist): SmartPlaylistDefinition =
        loadAndroidSmartPlaylistDefinition(state, playlist)

    fun addTrackToPlaylist(
        track: Track,
        playlist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String? = null,
    ) {
        addAndroidTrackToPlaylist(scope, state, track, playlist, newPlaylistName, storage)
    }

    fun addTracksToPlaylist(
        tracksToAdd: List<Track>,
        playlist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String? = null,
        label: String = "tracks",
    ) {
        addAndroidTracksToPlaylist(scope, state, tracksToAdd, playlist, newPlaylistName, label, storage)
    }

    fun saveQueueAsPlaylist(name: String) {
        saveQueueAsPlaylistFromState(scope, state, name, storage)
    }

    fun saveTracksAsPlaylist(name: String, tracks: List<Track>, label: String) {
        saveTracksAsPlaylistFromState(scope, state, name, tracks, label, storage)
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        addAndroidPlaylistToQueue(scope, state, playlist, storage, appendTracksToQueue)
    }

    fun addPlaylistToPlaylist(
        playlist: Playlist,
        targetPlaylist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String?,
    ) {
        addAndroidPlaylistToPlaylist(
            scope = scope,
            state = state,
            playlist = playlist,
            targetPlaylist = targetPlaylist,
            newPlaylistName = newPlaylistName,
            providerResponseCacheRepository = storage,
            addTracksToPlaylist = ::addTracksToPlaylist,
        )
    }
}
