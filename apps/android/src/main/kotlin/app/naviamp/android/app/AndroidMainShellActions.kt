package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.ui.nowPlayingQueueIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun androidMainShellActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorageDependencies,
    playbackEngine: AndroidPlaybackEngine,
    settingsStore: AndroidSettingsStore,
    searchController: AndroidSearchController,
    mediaAppController: AndroidMediaAppController,
    playbackAppController: AndroidPlaybackAppController,
    navigationController: AndroidNavigationController,
    artistActionController: AndroidArtistActionController,
    mixBuilderController: AndroidMixBuilderController,
    playlistActionController: AndroidPlaylistActionController,
    sleepTimerController: SleepTimerController,
    downloadActionController: AndroidDownloadActionController,
    settingsMaintenanceController: AndroidSettingsMaintenanceController,
    connectionSessionController: AndroidConnectionSessionController,
    shellPlaybackController: AndroidShellPlaybackController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    sonicPathController: AndroidSonicPathController,
    sonicMixController: AndroidSonicMixController,
    sonicHomeDiscoveryController: AndroidSonicHomeDiscoveryController,
    nowPlayingSidecarController: AndroidNowPlayingSidecarController,
    apiLibraryController: AndroidApiLibraryController,
    onSyncedSettingsChanged: () -> Unit = {},
): AndroidAppShellActions =
    androidAppShellActions(
        state = state,
        playbackEngine = playbackEngine,
        settingsStore = settingsStore,
        onSyncedSettingsChanged = onSyncedSettingsChanged,
        handleConnectionFormChanged = settingsMaintenanceController::handleConnectionFormChanged,
        refreshHome = {
            val provider = state.provider
            if (provider != null && !state.isHomeRefreshing) {
                state.isHomeRefreshing = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            loadBrowseState(
                                provider = provider,
                                providerResponseCacheRepository = storage,
                                libraryRepository = storage.asHomeLibraryRepository(),
                                sourceId = state.activeSourceId,
                                recentRadioStreams = state.homeState.recentRadioStreams,
                                recentInternetRadioStations = state.homeState.recentInternetRadioStations,
                            )
                        }
                    }.onSuccess { content ->
                        state.homeState = content
                        state.status = "Home refreshed."
                    }.onFailure { error ->
                        state.status = error.message ?: "Could not refresh Home."
                    }
                    state.isHomeRefreshing = false
                }
            }
        },
        connectToNavidrome = connectionSessionController::connectToNavidrome,
        handleNewConnection = connectionSessionController::openNewConnectionForm,
        handleEditSavedConnection = { connection ->
            state.savedMediaSources.firstOrNull { it.id == connection.id }
                ?.let(connectionSessionController::openSavedConnectionForm)
                ?: run { state.status = "Connection not found." }
        },
        handleConnectSavedConnection = { connection ->
            state.savedMediaSources.firstOrNull { it.id == connection.id }
                ?.let(connectionSessionController::connectSavedConnection)
                ?: run { state.status = "Connection not found." }
        },
        handleDeleteSavedConnection = { connection ->
            state.savedMediaSources.firstOrNull { it.id == connection.id }
                ?.let(connectionSessionController::deleteConnection)
                ?: run { state.status = "Connection not found." }
        },
        handlePlaybackSettingsChanged = settingsMaintenanceController::handlePlaybackSettingsChanged,
        handlePlaybackSettingsChangedAndRedownload = settingsMaintenanceController::handlePlaybackSettingsChangedAndRedownload,
        handleCacheSettingsChanged = settingsMaintenanceController::handleCacheSettingsChanged,
        handleClearCache = settingsMaintenanceController::handleClearCache,
        handleClearLibrary = settingsMaintenanceController::handleClearLibrary,
        handleResetDatabase = settingsMaintenanceController::handleResetDatabase,
        handleCurrentTrackRadioRefresh = shellPlaybackController::startCurrentTrackRadio,
        handleSearch = { searchController.launchSearch(scope) },
        handleArtistMixSearch = mixBuilderController::searchArtistSuggestions,
        handleArtistMixArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
        handleArtistMixArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
        handleArtistMixReset = mixBuilderController::resetArtistBuilder,
        handleArtistMixPlay = mixBuilderController::playArtistMix,
        handleAlbumMixSearch = mixBuilderController::searchAlbumSuggestions,
        handleAlbumMixAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
        handleAlbumMixAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
        handleAlbumMixReset = mixBuilderController::resetAlbumBuilder,
        handleAlbumMixPlay = mixBuilderController::playAlbumMix,
        handleGenreMixSearch = mixBuilderController::refreshGenreSuggestions,
        handleGenreMixGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
        handleGenreMixGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
        handleGenreMixReset = mixBuilderController::resetGenreBuilder,
        handleGenreMixPlay = mixBuilderController::playGenreMix,
        handleSonicPathStartQueryChanged = sonicPathController::updateStartQuery,
        handleSonicPathEndQueryChanged = sonicPathController::updateEndQuery,
        handleSonicPathStartSearch = sonicPathController::searchStartTracks,
        handleSonicPathEndSearch = sonicPathController::searchEndTracks,
        handleSonicPathStartTrackSelected = sonicPathController::selectStartTrack,
        handleSonicPathEndTrackSelected = sonicPathController::selectEndTrack,
        handleSonicPathStartTrackCleared = sonicPathController::clearStartTrack,
        handleSonicPathEndTrackCleared = sonicPathController::clearEndTrack,
        handleSonicPathCountChanged = sonicPathController::updateCount,
        handleSonicPathBuild = sonicPathController::buildPath,
        handleSonicPathReset = sonicPathController::reset,
        handleSonicPathPlay = sonicPathController::playPath,
        handleSonicPathAddToQueue = sonicPathController::addPathToQueue,
        handleSonicPathSaveAsPlaylist = { name ->
            playlistActionController.saveTracksAsPlaylist(name, sonicPathController.playlistTracks(), "sonic path")
        },
        handleSonicMixQueryChanged = sonicMixController::updateQuery,
        handleSonicMixSearch = sonicMixController::searchTracks,
        handleSonicMixTrackSelected = sonicMixController::selectTrack,
        handleSonicMixTrackRemoved = sonicMixController::removeTrack,
        handleSonicMixTargetLengthChanged = sonicMixController::updateTargetLength,
        handleSonicMixBiasChanged = sonicMixController::updateBias,
        handleSonicMixBuild = sonicMixController::buildMix,
        handleSonicMixReset = sonicMixController::reset,
        handleSonicMixPlay = sonicMixController::playMix,
        handleSonicMixAddToQueue = sonicMixController::addMixToQueue,
        handleSonicMixSaveAsPlaylist = { name ->
            playlistActionController.saveTracksAsPlaylist(name, sonicMixController.playlistTracks(), "sonic mix")
        },
        updateAndroidLibraryQuery = apiLibraryController::updateQuery,
        refreshAndroidLibrary = apiLibraryController::refresh,
        loadNextAndroidLibraryPage = apiLibraryController::loadNext,
        refreshPlaylists = playlistActionController::refreshPlaylists,
        refreshInternetRadioStations = shellMediaController::refreshInternetRadioStations,
        handleShellTrackSelected = shellMediaController::handleShellTrackSelected,
        handleDownloadedTrackAction = trackActionController::handleDownloadedTrackAction,
        cancelDownloadJob = downloadActionController::cancelDownloadJob,
        retryDownloadJob = downloadActionController::retryDownloadJob,
        refreshDownloads = downloadActionController::refreshDownloads,
        toggleKeepFavoritesDownloaded = downloadActionController::toggleKeepDownloadedFavorites,
        deleteAllDownloads = downloadActionController::deleteAllDownloads,
        handleShellAlbumSelected = shellMediaController::handleShellAlbumSelected,
        handleAlbumFavoriteToggled = { item -> toggleAndroidAlbumFavorite(scope, state, item, storage) },
        handleMixAlbumSelected = shellMediaController::handleMixAlbumSelected,
        handleShellAlbumPlay = shellMediaController::handleShellAlbumPlay,
        handleShellAlbumTrackSelected = shellMediaController::handleShellAlbumTrackSelected,
        handleShellAlbumRadio = shellMediaController::handleShellAlbumRadio,
        appendTracksToQueue = mediaAppController::appendTracksToQueue,
        downloadTracks = downloadActionController::downloadTracks,
        addTracksToPlaylist = playlistActionController::addTracksToPlaylist,
        handleTrackAction = trackActionController::handleTrackAction,
        handleShellArtistRadio = artistActionController::handleShellArtistRadio,
        handleShellArtistShuffle = artistActionController::handleShellArtistShuffle,
        loadArtistTracks = artistActionController::loadArtistTracks,
        handleArtistPopularPlay = artistActionController::handleArtistPopularPlay,
        handleShellArtistPopularRadio = artistActionController::handleShellArtistPopularRadio,
        handleArtistPopularTrackSelected = artistActionController::handleArtistPopularTrackSelected,
        handleArtistPopularAddToQueue = artistActionController::handleArtistPopularAddToQueue,
        findSimilarArtists = artistActionController::findSimilarArtists,
        handleSimilarArtistSelected = artistActionController::handleSimilarArtistSelected,
        openExternalArtistUrl = artistActionController::openExternalArtistUrl,
        openArtistDetails = { artistId, fallbackName ->
            mediaAppController.openArtistDetails(artistId, fallbackName)
        },
        handleArtistFavoriteToggled = { item -> toggleAndroidArtistFavorite(scope, state, item, storage) },
        handleArtistAlbumRadio = artistActionController::handleArtistAlbumRadio,
        loadArtistAlbumTracks = artistActionController::loadArtistAlbumTracks,
        openPlaylistDetails = playlistActionController::openPlaylistDetails,
        playPlaylist = playlistActionController::playPlaylist,
        downloadPlaylist = downloadActionController::downloadPlaylist,
        toggleKeepDownloadedPlaylist = downloadActionController::toggleKeepDownloadedPlaylist,
        addPlaylistToQueue = playlistActionController::addPlaylistToQueue,
        addPlaylistToPlaylist = playlistActionController::addPlaylistToPlaylist,
        renamePlaylist = playlistActionController::renamePlaylist,
        deletePlaylist = playlistActionController::deletePlaylist,
        updateStandardPlaylistTracks = playlistActionController::updateStandardPlaylistTracks,
        saveSmartPlaylist = playlistActionController::saveSmartPlaylist,
        updateSmartPlaylist = playlistActionController::updateSmartPlaylist,
        saveSmartPlaylistWithPassword = playlistActionController::saveSmartPlaylistWithPassword,
        updateSmartPlaylistWithPassword = playlistActionController::updateSmartPlaylistWithPassword,
        loadSmartPlaylist = playlistActionController::loadSmartPlaylistDefinition,
        closeActivePlaylist = navigationController::closeActivePlaylist,
        handlePlaylistTrackSelected = trackActionController::handlePlaylistTrackSelected,
        handleRecentRadioSelected = shellMediaController::handleShellRecentRadioSelected,
        handleMixBuilderSelected = navigationController::handleMixBuilderSelected,
        handleRadioStationSelected = shellMediaController::handleRadioStationSelected,
        saveInternetRadioStation = shellMediaController::saveInternetRadioStation,
        deleteInternetRadioStation = shellMediaController::deleteInternetRadioStation,
        handleShellHomeStationSelected = shellMediaController::handleShellHomeStationSelected,
        handleSonicDiscoveryTrackAction = { request ->
            val track = sonicHomeDiscoveryController.trackFor(request)
            when (request.action) {
                app.naviamp.ui.SharedTrackRowAction.ToggleFavorite ->
                    track?.let(mediaAppController::toggleTrackFavorite)
                app.naviamp.ui.SharedTrackRowAction.GoToAlbum ->
                    track?.let(shellMediaController::handleTrackGoToAlbum)
                app.naviamp.ui.SharedTrackRowAction.GoToArtist ->
                    track?.let(shellMediaController::handleTrackGoToArtist)
                else -> sonicHomeDiscoveryController.handleAction(request)
            }
        },
        closeActiveDetail = navigationController::closeActiveDetail,
        handleShellResume = shellPlaybackController::resume,
        playAdjacentTrack = playbackAppController::playAdjacentTrack,
        performSeek = playbackAppController::performSeek,
        handleShellToggleShuffle = shellPlaybackController::toggleShuffle,
        loadLyrics = nowPlayingSidecarController::loadLyrics,
        handleLyricsOffsetChanged = nowPlayingSidecarController::handleLyricsOffsetChanged,
        handleShellTrackRadio = shellPlaybackController::startCurrentTrackRadio,
        handleNowPlayingAddToPlaylist = trackActionController::handleNowPlayingAddToPlaylist,
        handleNowPlayingCreatePlaylistAndAdd = trackActionController::handleNowPlayingCreatePlaylistAndAdd,
        handleSaveQueueAsPlaylist = playlistActionController::saveQueueAsPlaylist,
        handleSleepTimerSelected = sleepTimerController::select,
        handleCancelSleepTimer = sleepTimerController::cancel,
        downloadTrack = downloadActionController::downloadTrack,
        handleShellGoToAlbum = shellMediaController::handleShellGoToAlbum,
        handleShellGoToArtist = shellMediaController::handleShellGoToArtist,
        handleTrackGoToAlbum = shellMediaController::handleTrackGoToAlbum,
        handleTrackGoToArtist = shellMediaController::handleTrackGoToArtist,
        handleShellQueueItemRadio = shellPlaybackController::startQueueItemRadio,
        handleQueueItemPlayNext = { item ->
            nowPlayingQueueIndex(item)?.let(mediaAppController::moveQueueTrackNext)
                ?: mediaAppController.resolveNowPlayingItemTrack(item)?.let(mediaAppController::playNext)
        },
        handleQueueItemAddToQueue = { item ->
            mediaAppController.resolveNowPlayingItemTrack(item)?.let(mediaAppController::addToQueue)
        },
        handleQueueItemSelected = playbackAppController::playQueueTrack,
        handleQueueItemRemoveFromQueue = mediaAppController::removeFromQueue,
        handleQueueItemMoveNext = mediaAppController::moveQueueTrackNext,
        handleEmptyQueue = mediaAppController::emptyQueue,
        handleTrackRadioNext = trackActionController::playTrackRadioNext,
        handleAddTrackRadioToQueue = trackActionController::addTrackRadioToQueue,
        resolveNowPlayingItemTrack = mediaAppController::resolveNowPlayingItemTrack,
        addTrackToPlaylist = playlistActionController::addTrackToPlaylist,
        toggleTrackFavorite = mediaAppController::toggleTrackFavorite,
        toggleCurrentFavorite = mediaAppController::toggleCurrentFavorite,
        handleShellRatingSelected = shellMediaController::handleShellRatingSelected,
    )
