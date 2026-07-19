package app.naviamp.android

import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.toInternetRadioStation
import app.naviamp.ui.toNaviampRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun androidMainShellActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorageDependencies,
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
): NaviampAppShellActions =
    androidAppShellActions(
        state = state,
        changePlaybackVolume = playbackAppController::changeVolume,
        settingsStore = settingsStore,
        onSyncedSettingsChanged = onSyncedSettingsChanged,
        navigationActions = NaviampShellNavigationActions(
            onRouteSelected = { route ->
                state.navigationState = state.navigationState.copy(route = route.toNaviampRoute())
                state.contentState = state.contentState.clearDetails()
                state.artistDetailBackStack = emptyList()
                state.nowPlayingOpen = false
            },
            onOpenNowPlaying = { state.nowPlayingOpen = true },
            onCloseNowPlaying = { state.nowPlayingOpen = false },
        ),
        connectionActions = NaviampConnectionSettingsActions(
            onFormChanged = settingsMaintenanceController::handleConnectionFormChanged,
            onConnect = connectionSessionController::connectToNavidrome,
            onEditCurrentConnection = { state.editingConnection = true },
            onNewConnection = connectionSessionController::openNewConnectionForm,
            onEditConnection = { connection ->
                state.savedMediaSources.firstOrNull { it.id == connection.id }
                    ?.let(connectionSessionController::openSavedConnectionForm)
                    ?: run { state.status = "Connection not found." }
            },
            onConnectSavedConnection = { connection ->
                state.savedMediaSources.firstOrNull { it.id == connection.id }
                    ?.let(connectionSessionController::connectSavedConnection)
                    ?: run { state.status = "Connection not found." }
            },
            onDeleteConnection = { connection ->
                state.savedMediaSources.firstOrNull { it.id == connection.id }
                    ?.let(connectionSessionController::deleteConnection)
                    ?: run { state.status = "Connection not found." }
            },
            onCancelConnectionForm = { state.editingConnection = false },
        ),
        valueActions = NaviampSettingsValueActions(
            onInterfaceSettingsChanged = { settings ->
                state.interfaceSettings = settings.normalized()
                settingsStore.saveInterfaceSettings(state.interfaceSettings)
                onSyncedSettingsChanged()
            },
            onPlaybackSettingsChanged = settingsMaintenanceController::handlePlaybackSettingsChanged,
            onPlaybackSettingsChangedAndRedownload =
                settingsMaintenanceController::handlePlaybackSettingsChangedAndRedownload,
            onCacheSettingsChanged = settingsMaintenanceController::handleCacheSettingsChanged,
            onDownloadLocationChanged = { location ->
                settingsMaintenanceController.handleCacheSettingsChanged(
                    state.cacheSettings.copy(customDownloadDirectory = location.path).normalized(),
                )
            },
            onAudioCacheLocationChanged = { location ->
                settingsMaintenanceController.handleCacheSettingsChanged(
                    state.cacheSettings.copy(customAudioCacheDirectory = location.path).normalized(),
                )
            },
        ),
        maintenanceActions = NaviampSettingsMaintenanceActions(
            onClearCache = settingsMaintenanceController::handleClearCache,
            onClearLibrary = settingsMaintenanceController::handleClearLibrary,
            onResetDatabase = settingsMaintenanceController::handleResetDatabase,
        ),
        searchActions = NaviampSearchActions(
            onQueryChanged = { state.contentState = state.contentState.copy(searchQuery = it) },
            onSearch = { searchController.launchSearch(scope) },
            onClear = {
                state.contentState = state.contentState.copy(
                    searchQuery = "",
                    searchResults = app.naviamp.domain.provider.MediaSearchResults(),
                )
                state.tracks = emptyList()
                state.status = ""
            },
        ),
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
        handlePlaybackSettingsChanged = settingsMaintenanceController::handlePlaybackSettingsChanged,
        handlePlaybackSettingsChangedAndRedownload = settingsMaintenanceController::handlePlaybackSettingsChangedAndRedownload,
        handleCurrentTrackRadioRefresh = shellPlaybackController::startCurrentTrackRadio,
        artistMixActions = SharedArtistMixBuilderActions(
            onQueryChanged = { state.artistMixQuery = it },
            onSearch = mixBuilderController::searchArtistSuggestions,
            onArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
            onArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
            onReset = mixBuilderController::resetArtistBuilder,
            onPlay = mixBuilderController::playArtistMix,
        ),
        albumMixActions = SharedAlbumMixBuilderActions(
            onQueryChanged = { state.albumMixQuery = it },
            onSearch = mixBuilderController::searchAlbumSuggestions,
            onAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
            onAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
            onReset = mixBuilderController::resetAlbumBuilder,
            onPlay = mixBuilderController::playAlbumMix,
        ),
        genreMixActions = SharedGenreMixBuilderActions(
            onQueryChanged = { state.genreMixQuery = it },
            onSearch = mixBuilderController::refreshGenreSuggestions,
            onGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
            onGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
            onReset = mixBuilderController::resetGenreBuilder,
            onPlay = mixBuilderController::playGenreMix,
        ),
        sonicPathActions = SharedSonicPathBuilderActions(
            onStartQueryChanged = sonicPathController::updateStartQuery,
            onEndQueryChanged = sonicPathController::updateEndQuery,
            onStartSearch = sonicPathController::searchStartTracks,
            onEndSearch = sonicPathController::searchEndTracks,
            onStartTrackSelected = sonicPathController::selectStartTrack,
            onEndTrackSelected = sonicPathController::selectEndTrack,
            onStartTrackCleared = sonicPathController::clearStartTrack,
            onEndTrackCleared = sonicPathController::clearEndTrack,
            onCountChanged = sonicPathController::updateCount,
            onBuild = sonicPathController::buildPath,
            onReset = sonicPathController::reset,
            onPlay = sonicPathController::playPath,
            onAddToQueue = sonicPathController::addPathToQueue,
            onSaveAsPlaylist = { name ->
                playlistActionController.saveTracksAsPlaylist(name, sonicPathController.playlistTracks(), "sonic path")
            },
        ),
        sonicMixActions = SharedSonicMixBuilderActions(
            onQueryChanged = sonicMixController::updateQuery,
            onSearch = sonicMixController::searchTracks,
            onTrackSelected = sonicMixController::selectTrack,
            onTrackRemoved = sonicMixController::removeTrack,
            onTargetLengthChanged = sonicMixController::updateTargetLength,
            onBiasChanged = sonicMixController::updateBias,
            onBuild = sonicMixController::buildMix,
            onReset = sonicMixController::reset,
            onPlay = sonicMixController::playMix,
            onAddToQueue = sonicMixController::addMixToQueue,
            onSaveAsPlaylist = { name ->
                playlistActionController.saveTracksAsPlaylist(name, sonicMixController.playlistTracks(), "sonic mix")
            },
        ),
        downloadsActions = NaviampDownloadsActions(
            onTrackAction = trackActionController::handleDownloadedTrackAction,
            onCancelJob = downloadActionController::cancelDownloadJob,
            onRetryJob = downloadActionController::retryDownloadJob,
            onRefresh = downloadActionController::refreshDownloads,
            onToggleKeepFavoritesDownloaded = downloadActionController::toggleKeepDownloadedFavorites,
            onDeleteAll = downloadActionController::deleteAllDownloads,
        ),
        libraryActions = NaviampLibraryActions(
            onQueryChanged = apiLibraryController::updateQuery,
            onRefresh = apiLibraryController::refresh,
            onLoadMore = apiLibraryController::loadNext,
        ),
        playlistsActions = NaviampPlaylistsActions(
            onRefresh = playlistActionController::refreshPlaylists,
            onSortModeChanged = { state.playlistSortMode = it },
            onSmartPlaylistSave = playlistActionController::saveSmartPlaylist,
            onSmartPlaylistUpdate = { playlist, definition ->
                state.homeState.playlists.firstOrNull { it.id == playlist.id }
                    ?.let { playlistActionController.updateSmartPlaylist(it, definition) }
                    ?: run { state.status = "Playlist not found." }
            },
            onSmartPlaylistSaveWithPassword = playlistActionController::saveSmartPlaylistWithPassword,
            onSmartPlaylistUpdateWithPassword = { playlist, definition, password ->
                state.homeState.playlists.firstOrNull { it.id == playlist.id }
                    ?.let { playlistActionController.updateSmartPlaylistWithPassword(it, definition, password) }
                    ?: run { state.status = "Playlist not found." }
            },
            onSmartPlaylistLoad = { playlist ->
                state.homeState.playlists.firstOrNull { it.id == playlist.id }
                    ?.let { playlistActionController.loadSmartPlaylistDefinition(it) }
                    ?: throw IllegalArgumentException("Playlist not found.")
            },
        ),
        radioActions = NaviampInternetRadioActions(
            onRefresh = shellMediaController::refreshInternetRadioStations,
            onStationAction = { request ->
                state.homeState.radioStations.firstOrNull { it.id == request.station.id }?.let { station ->
                    when (request.action) {
                        StationRowAction.Select -> shellMediaController.handleRadioStationSelected(station)
                        StationRowAction.Edit -> Unit
                        StationRowAction.Delete -> shellMediaController.deleteInternetRadioStation(station)
                    }
                } ?: run { state.status = "Station not found." }
            },
            onSaveStation = { draft ->
                shellMediaController.saveInternetRadioStation(draft.toInternetRadioStation())
            },
        ),
        albumDetailActions = androidAlbumDetailActions(
            scope = scope,
            state = state,
            mediaController = mediaAppController,
            shellMediaController = shellMediaController,
            trackActionController = trackActionController,
            playlistActionController = playlistActionController,
            downloadActionController = downloadActionController,
        ),
        artistDetailActions = androidArtistDetailActions(
            scope = scope,
            state = state,
            mediaController = mediaAppController,
            shellMediaController = shellMediaController,
            artistActionController = artistActionController,
            trackActionController = trackActionController,
            playlistActionController = playlistActionController,
            downloadActionController = downloadActionController,
        ),
        playlistDetailActions = androidPlaylistDetailActions(
            state = state,
            mediaController = mediaAppController,
            navigationController = navigationController,
            trackActionController = trackActionController,
            playlistActionController = playlistActionController,
            downloadActionController = downloadActionController,
        ),
        handleShellTrackSelected = shellMediaController::handleShellTrackSelected,
        handleShellAlbumSelected = shellMediaController::handleShellAlbumSelected,
        handleAlbumFavoriteToggled = { item ->
            toggleAndroidAlbumFavorite(scope, state, item, state.sharedControllers.providerActions)
        },
        handleMixAlbumSelected = shellMediaController::handleMixAlbumSelected,
        appendTracksToQueue = mediaAppController::appendTracksToQueue,
        downloadTracks = downloadActionController::downloadTracks,
        addTracksToPlaylist = playlistActionController::addTracksToPlaylist,
        handleTrackAction = trackActionController::handleTrackAction,
        openArtistDetails = { artistId, fallbackName ->
            mediaAppController.openArtistDetails(artistId, fallbackName)
        },
        handleArtistFavoriteToggled = { item ->
            toggleAndroidArtistFavorite(scope, state, item, state.sharedControllers.providerActions)
        },
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
        handleRecentRadioSelected = shellMediaController::handleShellRecentRadioSelected,
        handleMixBuilderSelected = navigationController::handleMixBuilderSelected,
        handleRadioStationSelected = shellMediaController::handleRadioStationSelected,
        handleShellHomeStationSelected = shellMediaController::handleShellHomeStationSelected,
        handleSonicDiscoveryTrackAction = { request ->
            val track = sonicHomeDiscoveryController.trackFor(request)
            when (request.action) {
                app.naviamp.ui.SharedTrackRowAction.ToggleFavorite ->
                    track?.let(mediaAppController::toggleTrackFavorite)
                app.naviamp.ui.SharedTrackRowAction.GoToAlbum ->
                    track?.let(shellMediaController::handleTrackGoToAlbum)
                app.naviamp.ui.SharedTrackRowAction.GoToArtist ->
                    track?.let { selectedTrack ->
                        shellMediaController.handleTrackGoToArtist(
                            selectedTrack,
                            request.artistId,
                            request.artistName,
                        )
                    }
                else -> sonicHomeDiscoveryController.handleAction(request)
            }
        },
        closeActiveDetail = navigationController::closeActiveDetail,
        handleShellPlayPause = playbackAppController::handlePlayPauseCommand,
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
