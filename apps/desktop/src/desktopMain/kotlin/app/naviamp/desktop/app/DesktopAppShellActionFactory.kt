package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.ResolvedTrackRowActionHandlers
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedDetailActionSources
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedInternetRadioActionSources
import app.naviamp.ui.SharedPlaylistActionSources
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.handleResolvedTrackRowAction

internal fun desktopConnectionSettingsActions(
    connectionForm: DesktopConnectionFormStateHolder,
    savedMediaSources: List<SavedMediaSource>,
    appActions: DesktopAppActions,
    connectionLifecycleController: DesktopConnectionLifecycleController,
): NaviampConnectionSettingsActions = NaviampConnectionSettingsActions(
    onFormChanged = { form ->
        connectionForm.connectionName = form.displayName
        connectionForm.updateServerUrl(form.serverUrl)
        connectionForm.updateUsername(form.username)
        connectionForm.password = form.password
        connectionForm.insecureSkipTlsVerification = form.skipTlsVerification
        connectionForm.customCertificatePath = form.customCertificatePath
        connectionForm.clientCertificateKeyStorePath = form.clientCertificatePath
        connectionForm.clientCertificateKeyStorePassword = form.clientCertificatePassword
        connectionForm.secondaryUrls = form.secondaryUrls
        connectionForm.customHeaders = form.customHeaders
        connectionForm.selectedMusicFolderIds = form.selectedMusicFolderIds
    },
    onConnect = { appActions.connectToServer() },
    onNewConnection = connectionLifecycleController::openNewConnectionForm,
    onEditConnection = { item ->
        savedMediaSources.firstOrNull { it.id == item.id }
            ?.let(connectionLifecycleController::openSavedConnectionForm)
    },
    onConnectSavedConnection = { item ->
        savedMediaSources.firstOrNull { it.id == item.id }
            ?.let(connectionLifecycleController::connectSavedConnection)
    },
    onDeleteConnection = { item ->
        savedMediaSources.firstOrNull { it.id == item.id }
            ?.let(appActions::deleteConnection)
    },
    onCancelConnectionForm = connectionLifecycleController::closeConnectionForm,
)

internal fun desktopSettingsValueActions(
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    settingsMaintenanceController: PlaybackSettingsMaintenanceController,
    cacheSettingsController: app.naviamp.app.NaviampCacheSettingsController,
): NaviampSettingsValueActions = NaviampSettingsValueActions(
    onInterfaceSettingsChanged = onInterfaceSettingsChanged,
    onPlaybackSettingsChanged = settingsMaintenanceController::applyPlaybackSettings,
    onPlaybackSettingsChangedAndRedownload = settingsMaintenanceController::applyPlaybackSettingsAndRedownload,
    onCacheSettingsChanged = cacheSettingsController::apply,
)

internal fun desktopSettingsMaintenanceActions(
    onOpenStatsForNerds: () -> Unit,
    appActions: DesktopAppActions,
    libraryController: DesktopLibraryController,
): NaviampSettingsMaintenanceActions = NaviampSettingsMaintenanceActions(
    onOpenStatsForNerds = onOpenStatsForNerds,
    onClearCache = appActions::clearCacheData,
    onClearLibrary = appActions::clearLibraryData,
    onRefreshLibrary = libraryController::refreshLibrarySnapshot,
    onResetDatabase = appActions::resetDatabase,
)

internal data class DesktopAppShellActionContext(
    val route: NaviampRoute,
    val setRoute: (NaviampRoute) -> Unit,
    val provider: NavidromeProvider?,
    val homeContent: HomeContent,
    val downloadedTracks: List<DownloadedTrack>,
    val appActions: DesktopAppActions,
    val homeController: DesktopHomeController,
    val sonicHomeDiscoveryController: DesktopSonicHomeDiscoveryController,
    val searchController: DesktopSearchController,
    val libraryController: DesktopLibraryController,
    val playlistsController: DesktopPlaylistsController,
    val smartPlaylistsController: DesktopSmartPlaylistsController,
    val internetRadioController: DesktopInternetRadioController,
    val albumController: DesktopAlbumController,
    val artistController: DesktopArtistController,
    val mixBuilderController: DesktopMixBuilderController,
    val radioController: DesktopRadioController,
    val sonicPathController: DesktopSonicPathController,
    val sonicMixController: DesktopSonicMixController,
    val connectionActions: NaviampConnectionSettingsActions,
    val valueActions: NaviampSettingsValueActions,
    val maintenanceActions: NaviampSettingsMaintenanceActions,
)

internal fun desktopAppShellActions(context: DesktopAppShellActionContext): NaviampAppShellActions =
    with(context) {
        val playlistSources = SharedPlaylistActionSources(
            playlists = playlistsController.playlists,
            playlistTracksById = playlistsController.playlistTracksById,
            selectedPlaylist = playlistsController.selectedPlaylist,
            selectedPlaylistTracks = playlistsController.selectedPlaylistTracks,
        )
        val detailSources = SharedDetailActionSources(
            selectedAlbum = albumController.selectedAlbum,
            albumDetail = albumController.selectedAlbumDetails,
            selectedArtist = artistController.selectedArtist,
            artistDetail = artistController.selectedArtistDetails,
            artistPopularTracks = artistController.selectedArtistPopularTracks,
            artistSimilarArtists = artistController.selectedArtistSimilarArtists,
        )

        NaviampAppShellActions(
            navigationActions = app.naviamp.ui.NaviampShellNavigationActions(
                onRouteSelected = { selected -> setRoute(selected.toAppRoute()) },
            ),
            homeActions = NaviampHomeActions(
                onRefresh = { provider?.let(homeController::loadHomeContent) },
                onRecentRadioSelected = { appActions.playHomeRecentRadio(it.id) },
                onInternetRadioStationSelected = { appActions.playHomeInternetRadio(it.id) },
                onMixBuilderSelected = { builder ->
                    setRoute(
                        when (builder.id) {
                            "artist" -> NaviampRoute.ArtistMix
                            "album" -> NaviampRoute.AlbumMix
                            "genre" -> NaviampRoute.GenreMix
                            "sonic-path" -> NaviampRoute.SonicPath
                            "sonic-mix" -> NaviampRoute.SonicMix
                            else -> route
                        },
                    )
                },
                onStationSelected = { appActions.playHomeStation(it.id) },
                onSonicDiscoveryTrackAction = { request ->
                    val track = sonicHomeDiscoveryController.trackFor(request)
                    when (request.action) {
                        SharedTrackRowAction.ToggleFavorite -> track?.let(appActions::toggleTrackFavorite)
                        SharedTrackRowAction.GoToAlbum -> track?.let(appActions::openTrackAlbumDetails)
                        SharedTrackRowAction.GoToArtist -> track?.let(appActions::openTrackArtistDetails)
                        else -> sonicHomeDiscoveryController.handleAction(request)
                    }
                },
                onRecentlyPlayedTrackAction = { request ->
                    val tracks = homeContent.recentlyPlayedTracks
                    handleResolvedTrackRowAction(
                        request,
                        tracks,
                        ResolvedTrackRowActionHandlers(
                            onSelect = { index, _ -> appActions.playPopularTracks(tracks, index) },
                            onPlayNext = playlistsController::playNext,
                            onStartRadio = { _, track -> appActions.playTrackRadio(track) },
                            onPlayTrackRadioNext = appActions::playTrackRadioNext,
                            onAddTrackRadioToQueue = appActions::addTrackRadioToQueue,
                            onDownload = { _, track -> appActions.downloadTrack(track) },
                            onAddToQueue = { _, track -> playlistsController.addTrackToQueue(track) },
                            onAddToPlaylist = { _, track, _ -> playlistsController.openTrackAddToPlaylist(track) },
                            onToggleFavorite = appActions::toggleTrackFavorite,
                            onGoToAlbum = appActions::openTrackAlbumDetails,
                            onGoToArtist = appActions::openTrackArtistDetails,
                        ),
                    )
                },
            ),
            searchActions = NaviampSearchActions(
                onQueryChanged = searchController::updateQuery,
                onClear = searchController::clearSearch,
            ),
            libraryActions = NaviampLibraryActions(
                onQueryChanged = libraryController::updateQuery,
                onRefresh = libraryController::refreshArtistIndex,
                onJumpToLetter = libraryController::jumpLibraryToLetter,
            ),
            playlistsActions = NaviampPlaylistsActions(
                onRefresh = { playlistsController.refreshPlaylists(useCache = false) },
                onSortModeChanged = playlistsController::updateSortMode,
                onSmartPlaylistSave = smartPlaylistsController::saveSmartPlaylist,
                onSmartPlaylistUpdate = { item, definition ->
                    playlistSources.playlist(item.id)?.let { playlist ->
                        smartPlaylistsController.updateSmartPlaylist(playlist, definition)
                    }
                },
                onSmartPlaylistSaveWithPassword = smartPlaylistsController::saveSmartPlaylistWithPassword,
                onSmartPlaylistUpdateWithPassword = { item, definition, password ->
                    playlistSources.playlist(item.id)?.let { playlist ->
                        smartPlaylistsController.updateSmartPlaylistWithPassword(playlist, definition, password)
                    }
                },
                onSmartPlaylistLoad = { item ->
                    playlistSources.playlist(item.id)
                        ?.let { smartPlaylistsController.loadSmartPlaylistDefinition(it) }
                        ?: error("Playlist ${item.title} is no longer available.")
                },
                onSmartPlaylistLoadWithPassword = { item, password ->
                    playlistSources.playlist(item.id)
                        ?.let { smartPlaylistsController.loadSmartPlaylistDefinitionWithPassword(it, password) }
                        ?: error("Playlist ${item.title} is no longer available.")
                },
            ),
            radioActions = desktopInternetRadioActions(
                actionSources = SharedInternetRadioActionSources(internetRadioController.stations),
                onRefresh = internetRadioController::refreshStations,
                onPlayStation = internetRadioController::playStation,
                onSaveStation = internetRadioController::saveStation,
                onDeleteStation = internetRadioController::deleteStation,
            ),
            albumDetailActions = desktopAlbumDetailActions(
                actionSources = detailSources,
                appActions = appActions,
                playlistsController = playlistsController,
                onBack = appActions::closeAlbumDetails,
            ),
            artistDetailActions = desktopArtistDetailActions(detailSources, appActions, playlistsController),
            playlistDetailActions = desktopPlaylistDetailActions(
                actionSources = playlistSources,
                appActions = appActions,
                playlistsController = playlistsController,
                onBack = { setRoute(NaviampRoute.Playlists) },
            ),
            mediaActions = desktopMediaActions(
                playlistActionSources = playlistSources,
                artists = if (route == NaviampRoute.Search) {
                    searchController.results.artists
                } else {
                    libraryController.snapshot.artists
                },
                albums = searchController.results.albums,
                tracks = searchController.results.tracks,
                appActions = appActions,
                playlistsController = playlistsController,
            ),
            downloadsActions = desktopDownloadsActions(downloadedTracks, appActions, playlistsController),
            connectionActions = connectionActions,
            valueActions = valueActions,
            maintenanceActions = maintenanceActions,
            artistMixActions = SharedArtistMixBuilderActions(
                onQueryChanged = mixBuilderController::setArtistQuery,
                onSearch = mixBuilderController::searchArtistSuggestions,
                onArtistSelected = { mixBuilderController.selectArtistByItemId(it.id) },
                onArtistRemoved = { mixBuilderController.removeArtistByItemId(it.id) },
                onReset = mixBuilderController::resetArtistBuilder,
                onPlay = { mixBuilderController.playArtistMix(radioController) },
            ),
            albumMixActions = SharedAlbumMixBuilderActions(
                onQueryChanged = mixBuilderController::setAlbumQuery,
                onSearch = mixBuilderController::searchAlbumSuggestions,
                onAlbumSelected = { mixBuilderController.selectAlbumByItemId(it.id) },
                onAlbumRemoved = { mixBuilderController.removeAlbumByItemId(it.id) },
                onReset = mixBuilderController::resetAlbumBuilder,
                onPlay = { mixBuilderController.playAlbumMix(radioController) },
            ),
            genreMixActions = SharedGenreMixBuilderActions(
                onQueryChanged = mixBuilderController::setGenreQuery,
                onSearch = mixBuilderController::refreshGenreSuggestions,
                onGenreSelected = { mixBuilderController.selectGenreByItemId(it.id) },
                onGenreRemoved = { mixBuilderController.removeGenreByItemId(it.id) },
                onReset = mixBuilderController::resetGenreBuilder,
                onPlay = { mixBuilderController.playGenreMix(radioController) },
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
                    playlistsController.saveTracksAsPlaylist(name, sonicPathController.playlistTracks(), "sonic path")
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
                    playlistsController.saveTracksAsPlaylist(name, sonicMixController.playlistTracks(), "sonic mix")
                },
            ),
        )
    }
