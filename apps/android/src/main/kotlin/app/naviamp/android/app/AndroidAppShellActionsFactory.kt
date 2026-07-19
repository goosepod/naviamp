package app.naviamp.android

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackVolumeCommand
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.toInternetRadioStation
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.SharedSonicMixBiasUi
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.resolveAction
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.toNaviampRoute

fun androidAppShellActions(
    state: AndroidAppState,
    changePlaybackVolume: (Int) -> PlaybackVolumeCommand,
    settingsStore: AndroidSettingsStore,
    onSyncedSettingsChanged: () -> Unit = {},
    handleConnectionFormChanged: (ConnectionFormState) -> Unit,
    refreshHome: () -> Unit,
    connectToNavidrome: () -> Unit,
    handleNewConnection: () -> Unit,
    handleEditSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    handleConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    handleDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    handlePlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    handlePlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    handleCacheSettingsChanged: (CacheSettings) -> Unit,
    handleClearCache: () -> Unit,
    handleClearLibrary: () -> Unit,
    handleResetDatabase: () -> Unit,
    handleCurrentTrackRadioRefresh: () -> Unit,
    handleSearch: () -> Unit,
    handleArtistMixSearch: () -> Unit,
    handleArtistMixArtistSelected: (SharedMediaItemUi) -> Unit,
    handleArtistMixArtistRemoved: (SharedMediaItemUi) -> Unit,
    handleArtistMixReset: () -> Unit,
    handleArtistMixPlay: () -> Unit,
    handleAlbumMixSearch: () -> Unit,
    handleAlbumMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleAlbumMixAlbumRemoved: (SharedMediaItemUi) -> Unit,
    handleAlbumMixReset: () -> Unit,
    handleAlbumMixPlay: () -> Unit,
    handleGenreMixSearch: () -> Unit,
    handleGenreMixGenreSelected: (SharedGenreMixItemUi) -> Unit,
    handleGenreMixGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    handleGenreMixReset: () -> Unit,
    handleGenreMixPlay: () -> Unit,
    handleSonicPathStartQueryChanged: (String) -> Unit,
    handleSonicPathEndQueryChanged: (String) -> Unit,
    handleSonicPathStartSearch: () -> Unit,
    handleSonicPathEndSearch: () -> Unit,
    handleSonicPathStartTrackSelected: (SharedTrackRowUi) -> Unit,
    handleSonicPathEndTrackSelected: (SharedTrackRowUi) -> Unit,
    handleSonicPathStartTrackCleared: () -> Unit,
    handleSonicPathEndTrackCleared: () -> Unit,
    handleSonicPathCountChanged: (Int) -> Unit,
    handleSonicPathBuild: () -> Unit,
    handleSonicPathReset: () -> Unit,
    handleSonicPathPlay: () -> Unit,
    handleSonicPathAddToQueue: () -> Unit,
    handleSonicPathSaveAsPlaylist: (String) -> Unit,
    handleSonicMixQueryChanged: (String) -> Unit,
    handleSonicMixSearch: () -> Unit,
    handleSonicMixTrackSelected: (SharedTrackRowUi) -> Unit,
    handleSonicMixTrackRemoved: (SharedTrackRowUi) -> Unit,
    handleSonicMixTargetLengthChanged: (Int) -> Unit,
    handleSonicMixBiasChanged: (SharedSonicMixBiasUi) -> Unit,
    handleSonicMixBuild: () -> Unit,
    handleSonicMixReset: () -> Unit,
    handleSonicMixPlay: () -> Unit,
    handleSonicMixAddToQueue: () -> Unit,
    handleSonicMixSaveAsPlaylist: (String) -> Unit,
    updateAndroidLibraryQuery: (String) -> Unit,
    refreshAndroidLibrary: () -> Unit,
    loadNextAndroidLibraryPage: () -> Unit,
    refreshPlaylists: () -> Unit,
    refreshInternetRadioStations: () -> Unit,
    handleShellTrackSelected: (SharedTrackRowUi) -> Unit,
    handleDownloadedTrackAction: (DownloadedTrackActionRequest) -> Unit,
    cancelDownloadJob: (String) -> Unit,
    retryDownloadJob: (String) -> Unit,
    refreshDownloads: () -> Unit,
    toggleKeepFavoritesDownloaded: () -> Unit,
    deleteAllDownloads: () -> Unit,
    handleShellAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    handleMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleShellAlbumPlay: (Boolean) -> Unit,
    handleShellAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    handleShellAlbumRadio: () -> Unit,
    appendTracksToQueue: (List<Track>, String) -> Unit,
    downloadTracks: (List<Track>, String) -> Unit,
    addTracksToPlaylist: (List<Track>, NaviampPlaylistChoiceUi?, String?, String) -> Unit,
    handleTrackAction: (SharedTrackRowActionRequest) -> Unit,
    handleShellArtistRadio: (SharedArtistDetailUi) -> Unit,
    handleShellArtistPlay: (SharedArtistDetailUi) -> Unit,
    handleShellArtistShuffle: (SharedArtistDetailUi) -> Unit,
    loadArtistTracks: ((List<Track>) -> Unit) -> Unit,
    handleArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    handleShellArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    handleArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    handleArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    findSimilarArtists: (app.naviamp.domain.ArtistId, String) -> Unit,
    handleSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    openExternalArtistUrl: (String) -> Unit,
    openArtistDetails: (app.naviamp.domain.ArtistId, String) -> Unit,
    handleArtistFavoriteToggled: (SharedMediaItemUi) -> Unit,
    handleArtistAlbumRadio: (SharedMediaItemUi) -> Unit,
    loadArtistAlbumTracks: (SharedMediaItemUi, (List<Track>) -> Unit) -> Unit,
    openPlaylistDetails: (Playlist) -> Unit,
    playPlaylist: (Playlist, Boolean) -> Unit,
    downloadPlaylist: (Playlist) -> Unit,
    toggleKeepDownloadedPlaylist: (Playlist) -> Unit,
    addPlaylistToQueue: (Playlist) -> Unit,
    addPlaylistToPlaylist: (Playlist, NaviampPlaylistChoiceUi?, String?) -> Unit,
    renamePlaylist: (Playlist, String) -> Unit,
    deletePlaylist: (Playlist) -> Unit,
    updateStandardPlaylistTracks: suspend (Playlist, List<Track>) -> Unit,
    saveSmartPlaylist: suspend (SmartPlaylistDefinition) -> Unit,
    updateSmartPlaylist: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
    saveSmartPlaylistWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit,
    updateSmartPlaylistWithPassword: suspend (Playlist, SmartPlaylistDefinition, String) -> Unit,
    loadSmartPlaylist: suspend (Playlist) -> SmartPlaylistDefinition,
    closeActivePlaylist: () -> Unit,
    handlePlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    handleRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    handleMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    handleRadioStationSelected: (InternetRadioStation) -> Unit,
    saveInternetRadioStation: (InternetRadioStation) -> Unit,
    deleteInternetRadioStation: (InternetRadioStation) -> Unit,
    handleStationAction: (StationRowActionRequest) -> Unit = { request ->
        state.homeState.radioStations.firstOrNull { it.id == request.station.id }?.let { station ->
            when (request.action) {
                StationRowAction.Select -> handleRadioStationSelected(station)
                StationRowAction.Edit -> Unit
                StationRowAction.Delete -> deleteInternetRadioStation(station)
            }
        } ?: run { state.status = "Station not found." }
    },
    handleShellHomeStationSelected: (SharedHomeStationUi) -> Unit,
    handleSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
    closeActiveDetail: () -> Unit,
    handleShellPlayPause: () -> Unit,
    playAdjacentTrack: (Int) -> Unit,
    performSeek: (Double) -> Unit,
    handleShellToggleShuffle: () -> Unit,
    loadLyrics: (Track) -> Unit,
    handleLyricsOffsetChanged: (Int) -> Unit,
    handleShellTrackRadio: () -> Unit,
    handleNowPlayingAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    handleNowPlayingCreatePlaylistAndAdd: (String) -> Unit,
    handleSaveQueueAsPlaylist: (String) -> Unit,
    handleSleepTimerSelected: (SleepTimerRequest) -> Unit,
    handleCancelSleepTimer: () -> Unit,
    downloadTrack: (Track) -> Unit,
    handleShellGoToAlbum: () -> Unit,
    handleShellGoToArtist: (artistId: String?, artistName: String?) -> Unit,
    handleTrackGoToAlbum: (Track) -> Unit,
    handleTrackGoToArtist: (Track) -> Unit,
    handleShellQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    handleQueueItemPlayNext: (NaviampNowPlayingItemUi) -> Unit,
    handleQueueItemAddToQueue: (NaviampNowPlayingItemUi) -> Unit,
    handleQueueItemSelected: (Int) -> Unit,
    handleQueueItemRemoveFromQueue: (Int) -> Unit,
    handleQueueItemMoveNext: (Int) -> Unit,
    handleEmptyQueue: () -> Unit,
    handleTrackRadioNext: (Track) -> Unit,
    handleAddTrackRadioToQueue: (Track) -> Unit,
    resolveNowPlayingItemTrack: (NaviampNowPlayingItemUi) -> Track?,
    addTrackToPlaylist: (Track, NaviampPlaylistChoiceUi?, String?) -> Unit,
    toggleTrackFavorite: (Track) -> Unit,
    handleQueueItemAction: (NowPlayingItemActionRequest) -> Unit = { request ->
        val action = request.resolveAction(fallbackTrack = resolveNowPlayingItemTrack(request.item))
        when (action.action) {
            NowPlayingItemAction.StartRadio -> handleShellQueueItemRadio(action.item)
            NowPlayingItemAction.PlayTrackRadioNext -> action.track?.let(handleTrackRadioNext)
            NowPlayingItemAction.AddTrackRadioToQueue -> action.track?.let(handleAddTrackRadioToQueue)
            NowPlayingItemAction.PlayNext -> handleQueueItemPlayNext(action.item)
            NowPlayingItemAction.AddToQueue -> handleQueueItemAddToQueue(action.item)
            NowPlayingItemAction.AddToPlaylist -> action.track?.let { addTrackToPlaylist(it, action.playlistChoice, null) }
            NowPlayingItemAction.CreatePlaylistAndAdd ->
                action.track?.let { addTrackToPlaylist(it, null, action.playlistName) }
            NowPlayingItemAction.Download -> action.track?.let(downloadTrack)
            NowPlayingItemAction.GoToAlbum -> action.track?.let(handleTrackGoToAlbum)
            NowPlayingItemAction.GoToArtist -> action.track?.let(handleTrackGoToArtist)
            NowPlayingItemAction.ToggleFavorite -> action.track?.let(toggleTrackFavorite)
            NowPlayingItemAction.RemoveFromQueue ->
                (request.target as? app.naviamp.ui.NowPlayingItemTarget.QueueIndex)
                    ?.let { handleQueueItemRemoveFromQueue(it.index) }
        }
    },
    toggleCurrentFavorite: () -> Unit,
    handleShellRatingSelected: (Int?) -> Unit,
): NaviampAppShellActions =
    with(state) {
        NaviampAppShellActions(
            navigationActions = NaviampShellNavigationActions(
                onRouteSelected = { route ->
                    navigationState = navigationState.copy(route = route.toNaviampRoute())
                    contentState = contentState.clearDetails()
                    artistDetailBackStack = emptyList()
                    nowPlayingOpen = false
                },
                onOpenNowPlaying = { nowPlayingOpen = true },
                onCloseNowPlaying = { nowPlayingOpen = false },
            ),
            connectionActions = NaviampConnectionSettingsActions(
                onFormChanged = handleConnectionFormChanged,
                onConnect = { connectToNavidrome() },
                onEditCurrentConnection = { editingConnection = true },
                onNewConnection = handleNewConnection,
                onEditConnection = handleEditSavedConnection,
                onConnectSavedConnection = handleConnectSavedConnection,
                onDeleteConnection = handleDeleteSavedConnection,
                onCancelConnectionForm = { editingConnection = false },
            ),
            valueActions = NaviampSettingsValueActions(
                onInterfaceSettingsChanged = { settings: InterfaceSettings ->
                    interfaceSettings = settings.normalized()
                    settingsStore.saveInterfaceSettings(interfaceSettings)
                    onSyncedSettingsChanged()
                },
                onPlaybackSettingsChanged = handlePlaybackSettingsChanged,
                onPlaybackSettingsChangedAndRedownload = handlePlaybackSettingsChangedAndRedownload,
                onCacheSettingsChanged = handleCacheSettingsChanged,
                onDownloadLocationChanged = { location ->
                    handleCacheSettingsChanged(cacheSettings.copy(customDownloadDirectory = location.path).normalized())
                },
                onAudioCacheLocationChanged = { location ->
                    handleCacheSettingsChanged(cacheSettings.copy(customAudioCacheDirectory = location.path).normalized())
                },
            ),
            maintenanceActions = NaviampSettingsMaintenanceActions(
                onClearCache = handleClearCache,
                onClearLibrary = handleClearLibrary,
                onResetDatabase = handleResetDatabase,
            ),
            searchActions = NaviampSearchActions(
                onQueryChanged = { contentState = contentState.copy(searchQuery = it) },
                onSearch = handleSearch,
                onClear = {
                    contentState = contentState.copy(
                        searchQuery = "",
                        searchResults = app.naviamp.domain.provider.MediaSearchResults(),
                    )
                    tracks = emptyList()
                    status = ""
                },
            ),
            artistMixActions = SharedArtistMixBuilderActions(
                onQueryChanged = { artistMixQuery = it },
                onSearch = handleArtistMixSearch,
                onArtistSelected = handleArtistMixArtistSelected,
                onArtistRemoved = handleArtistMixArtistRemoved,
                onReset = handleArtistMixReset,
                onPlay = handleArtistMixPlay,
            ),
            albumMixActions = SharedAlbumMixBuilderActions(
                onQueryChanged = { albumMixQuery = it },
                onSearch = handleAlbumMixSearch,
                onAlbumSelected = handleAlbumMixAlbumSelected,
                onAlbumRemoved = handleAlbumMixAlbumRemoved,
                onReset = handleAlbumMixReset,
                onPlay = handleAlbumMixPlay,
            ),
            genreMixActions = SharedGenreMixBuilderActions(
                onQueryChanged = { genreMixQuery = it },
                onSearch = handleGenreMixSearch,
                onGenreSelected = handleGenreMixGenreSelected,
                onGenreRemoved = handleGenreMixGenreRemoved,
                onReset = handleGenreMixReset,
                onPlay = handleGenreMixPlay,
            ),
            sonicPathActions = SharedSonicPathBuilderActions(
                onStartQueryChanged = handleSonicPathStartQueryChanged,
                onEndQueryChanged = handleSonicPathEndQueryChanged,
                onStartSearch = handleSonicPathStartSearch,
                onEndSearch = handleSonicPathEndSearch,
                onStartTrackSelected = handleSonicPathStartTrackSelected,
                onEndTrackSelected = handleSonicPathEndTrackSelected,
                onStartTrackCleared = handleSonicPathStartTrackCleared,
                onEndTrackCleared = handleSonicPathEndTrackCleared,
                onCountChanged = handleSonicPathCountChanged,
                onBuild = handleSonicPathBuild,
                onReset = handleSonicPathReset,
                onPlay = handleSonicPathPlay,
                onAddToQueue = handleSonicPathAddToQueue,
                onSaveAsPlaylist = handleSonicPathSaveAsPlaylist,
            ),
            sonicMixActions = SharedSonicMixBuilderActions(
                onQueryChanged = handleSonicMixQueryChanged,
                onSearch = handleSonicMixSearch,
                onTrackSelected = handleSonicMixTrackSelected,
                onTrackRemoved = handleSonicMixTrackRemoved,
                onTargetLengthChanged = handleSonicMixTargetLengthChanged,
                onBiasChanged = handleSonicMixBiasChanged,
                onBuild = handleSonicMixBuild,
                onReset = handleSonicMixReset,
                onPlay = handleSonicMixPlay,
                onAddToQueue = handleSonicMixAddToQueue,
                onSaveAsPlaylist = handleSonicMixSaveAsPlaylist,
            ),
            downloadsActions = NaviampDownloadsActions(
                onTrackAction = handleDownloadedTrackAction,
                onCancelJob = cancelDownloadJob,
                onRetryJob = retryDownloadJob,
                onRefresh = refreshDownloads,
                onToggleKeepFavoritesDownloaded = toggleKeepFavoritesDownloaded,
                onDeleteAll = deleteAllDownloads,
            ),
            libraryActions = NaviampLibraryActions(
                onQueryChanged = updateAndroidLibraryQuery,
                onRefresh = refreshAndroidLibrary,
                onLoadMore = loadNextAndroidLibraryPage,
            ),
            playlistsActions = NaviampPlaylistsActions(
                onRefresh = refreshPlaylists,
                onSortModeChanged = { playlistSortMode = it },
                onSmartPlaylistSave = { definition -> saveSmartPlaylist(definition) },
                onSmartPlaylistUpdate = { playlist, definition ->
                    homeState.playlists.firstOrNull { it.id == playlist.id }?.let { updateSmartPlaylist(it, definition) }
                        ?: run { status = "Playlist not found." }
                },
                onSmartPlaylistSaveWithPassword = { definition, password ->
                    saveSmartPlaylistWithPassword(definition, password)
                },
                onSmartPlaylistUpdateWithPassword = { playlist, definition, password ->
                    homeState.playlists.firstOrNull { it.id == playlist.id }?.let {
                        updateSmartPlaylistWithPassword(it, definition, password)
                    } ?: run { status = "Playlist not found." }
                },
                onSmartPlaylistLoad = { playlist ->
                    homeState.playlists.firstOrNull { it.id == playlist.id }?.let { loadSmartPlaylist(it) }
                        ?: throw IllegalArgumentException("Playlist not found.")
                },
            ),
            radioActions = NaviampInternetRadioActions(
                onRefresh = refreshInternetRadioStations,
                onStationAction = handleStationAction,
                onSaveStation = { draft -> saveInternetRadioStation(draft.toInternetRadioStation()) },
            ),
            albumDetailActions = NaviampAlbumDetailActions(
                onBack = { nowPlayingOpen = false },
                onPlay = { _, shuffle -> handleShellAlbumPlay(shuffle) },
                onRadio = { handleShellAlbumRadio() },
                onDownload = { downloadTracks(albumDetail?.tracks.orEmpty(), "album") },
                onAddToQueue = { appendTracksToQueue(albumDetail?.tracks.orEmpty(), "album tracks") },
                onAddToPlaylist = { _, playlist ->
                    addTracksToPlaylist(albumDetail?.tracks.orEmpty(), playlist, null, "album")
                },
                onCreatePlaylistAndAdd = { _, name ->
                    addTracksToPlaylist(albumDetail?.tracks.orEmpty(), null, name, "album")
                },
                onFavoriteToggled = handleAlbumFavoriteToggled,
                onTrackSelected = handleShellAlbumTrackSelected,
                onTrackAction = handleTrackAction,
            ),
            artistDetailActions = NaviampArtistDetailActions(
                onBack = { nowPlayingOpen = false },
                onRadio = handleShellArtistRadio,
                onPlay = handleShellArtistPlay,
                onShuffle = handleShellArtistShuffle,
                onAddToQueue = { loadArtistTracks { appendTracksToQueue(it, "artist tracks") } },
                onAddToPlaylist = { _, playlist -> loadArtistTracks { addTracksToPlaylist(it, playlist, null, "artist") } },
                onCreatePlaylistAndAdd = { _, name -> loadArtistTracks { addTracksToPlaylist(it, null, name, "artist") } },
                onFavoriteToggled = handleArtistFavoriteToggled,
                onPopularPlay = handleArtistPopularPlay,
                onPopularRadio = handleShellArtistPopularRadio,
                onPopularAddToQueue = handleArtistPopularAddToQueue,
                onPopularTrackSelected = handleArtistPopularTrackSelected,
                onTrackAction = handleTrackAction,
                onFindSimilar = { detail ->
                    findSimilarArtists(app.naviamp.domain.ArtistId(detail.artist.id), detail.artist.title)
                },
                onSimilarArtistSelected = handleSimilarArtistSelected,
                onSimilarArtistExternalSelected = openExternalArtistUrl,
                onAlbumSelected = handleShellAlbumSelected,
                onAlbumAction = { request ->
                    when (request.action) {
                        SharedMediaItemAction.Select -> handleShellAlbumSelected(request.item)
                        SharedMediaItemAction.StartRadio -> handleArtistAlbumRadio(request.item)
                        SharedMediaItemAction.AddToQueue ->
                            loadArtistAlbumTracks(request.item) { appendTracksToQueue(it, "album tracks") }
                        SharedMediaItemAction.Download ->
                            loadArtistAlbumTracks(request.item) { downloadTracks(it, request.item.title) }
                        SharedMediaItemAction.AddToPlaylist ->
                            loadArtistAlbumTracks(request.item) {
                                addTracksToPlaylist(it, request.playlistChoice, null, request.item.title)
                            }
                        SharedMediaItemAction.CreatePlaylistAndAdd ->
                            loadArtistAlbumTracks(request.item) {
                                addTracksToPlaylist(it, null, request.playlistName, request.item.title)
                            }
                        SharedMediaItemAction.ToggleFavorite -> handleAlbumFavoriteToggled(request.item)
                        else -> Unit
                    }
                },
                onAlbumFavoriteToggled = handleAlbumFavoriteToggled,
            ),
            playlistDetailActions = NaviampPlaylistDetailActions(
                onBack = { closeActivePlaylist() },
                onPlay = { selectedPlaylist, shuffle ->
                    homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                        ?: run { status = "Playlist not found." }
                },
                onAddToQueue = { appendTracksToQueue(selectedPlaylistTracks, "playlist tracks") },
                onAddToPlaylist = { _, playlist ->
                    selectedPlaylist?.let { addPlaylistToPlaylist(it, playlist, null) }
                        ?: run { status = "Playlist not found." }
                },
                onCreatePlaylistAndAdd = { _, name ->
                    selectedPlaylist?.let { addPlaylistToPlaylist(it, null, name) }
                        ?: run { status = "Playlist not found." }
                },
                onCopy = { _, name, deduplicate ->
                    val tracks = if (deduplicate) selectedPlaylistTracks.distinctBy { it.id } else selectedPlaylistTracks
                    addTracksToPlaylist(tracks, null, name, "playlist")
                },
                onRename = { selectedPlaylist, name ->
                    homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { renamePlaylist(it, name) }
                        ?: run { status = "Playlist not found." }
                },
                onDelete = { selectedPlaylist ->
                    homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(deletePlaylist)
                        ?: run { status = "Playlist not found." }
                },
                onUpdateStandardPlaylist = { playlistItem, trackRows ->
                    val playlist = homeState.playlists.firstOrNull { it.id == playlistItem.id }
                        ?: throw IllegalArgumentException("Playlist not found.")
                    val sourceTracks = playlistTracksById[playlist.id].orEmpty()
                    val editedTracks = trackRows.map { row ->
                        sourceTracks.firstOrNull { track -> track.id.value == row.id }
                            ?: throw IllegalArgumentException("Track ${row.title} is no longer in the playlist.")
                    }
                    updateStandardPlaylistTracks(playlist, editedTracks)
                },
                onMediaItemAction = { request ->
                    val playlist = homeState.playlists.firstOrNull { it.id == request.item.id }
                    if (playlist == null) {
                        status = "Playlist not found."
                    } else if (request.action == SharedMediaItemAction.Download) {
                        if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
                            toggleKeepDownloadedPlaylist(playlist)
                        } else {
                            downloadPlaylist(playlist)
                        }
                    }
                },
                onTrackSelected = handlePlaylistTrackSelected,
                onTrackAction = handleTrackAction,
            ),
            homeActions = NaviampHomeActions(
                onRefresh = refreshHome,
                onRecentRadioSelected = handleRecentRadioSelected,
                onInternetRadioStationSelected = { item ->
                    homeState.radioStations.firstOrNull { station -> station.id == item.id }
                        ?.let(handleRadioStationSelected)
                        ?: run { status = "Station not found." }
                },
                onMixBuilderSelected = handleMixBuilderSelected,
                onStationSelected = handleShellHomeStationSelected,
                onSonicDiscoveryTrackAction = handleSonicDiscoveryTrackAction,
                onRecentlyPlayedTrackAction = { request ->
                    if (request.action == SharedTrackRowAction.Select) {
                        handleShellTrackSelected(request.track)
                    } else {
                        handleTrackAction(request)
                    }
                },
            ),
            mediaActions = NaviampMediaActions(
            onTrackSelected = handleShellTrackSelected,
            onAlbumSelected = handleShellAlbumSelected,
            onAlbumFavoriteToggled = handleAlbumFavoriteToggled,
            onMixAlbumSelected = handleMixAlbumSelected,
            onTrackAction = handleTrackAction,
            onArtistSelected = { selectedArtist ->
                openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
            },
            onArtistFavoriteToggled = handleArtistFavoriteToggled,
            onPlaylistSelected = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(openPlaylistDetails)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistPlay = { selectedPlaylist, shuffle ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistRename = { selectedPlaylist, name ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { renamePlaylist(it, name) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistDelete = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(deletePlaylist)
                    ?: run { status = "Playlist not found." }
            },
            onMediaItemAction = { request ->
                when (request.kind) {
                    SharedMediaItemKind.Album -> {
                        when (request.action) {
                            SharedMediaItemAction.Select -> handleShellAlbumSelected(request.item)
                            SharedMediaItemAction.StartRadio -> handleArtistAlbumRadio(request.item)
                            SharedMediaItemAction.AddToQueue ->
                                loadArtistAlbumTracks(request.item) { appendTracksToQueue(it, "album tracks") }
                            SharedMediaItemAction.Download ->
                                loadArtistAlbumTracks(request.item) { downloadTracks(it, request.item.title) }
                            SharedMediaItemAction.AddToPlaylist ->
                                loadArtistAlbumTracks(request.item) {
                                    addTracksToPlaylist(it, request.playlistChoice, null, request.item.title)
                                }
                            SharedMediaItemAction.CreatePlaylistAndAdd ->
                                loadArtistAlbumTracks(request.item) {
                                    addTracksToPlaylist(it, null, request.playlistName, request.item.title)
                                }
                            SharedMediaItemAction.ToggleFavorite -> handleAlbumFavoriteToggled(request.item)
                            SharedMediaItemAction.Play,
                            SharedMediaItemAction.Shuffle,
                            SharedMediaItemAction.FindSimilar,
                            SharedMediaItemAction.CopyPlaylist,
                            SharedMediaItemAction.CopyPlaylistDeduplicated,
                            SharedMediaItemAction.Rename,
                            SharedMediaItemAction.EditSmartPlaylist,
                            SharedMediaItemAction.Delete,
                            SharedMediaItemAction.EditStation,
                            SharedMediaItemAction.DeleteStation,
                            -> Unit
                        }
                    }
                    SharedMediaItemKind.Artist -> {
                        when (request.action) {
                            SharedMediaItemAction.Select ->
                                openArtistDetails(app.naviamp.domain.ArtistId(request.item.id), request.item.title)
                            SharedMediaItemAction.ToggleFavorite -> handleArtistFavoriteToggled(request.item)
                            SharedMediaItemAction.Play,
                            SharedMediaItemAction.Shuffle,
                            SharedMediaItemAction.StartRadio,
                            SharedMediaItemAction.FindSimilar,
                            SharedMediaItemAction.AddToQueue,
                            SharedMediaItemAction.Download,
                            SharedMediaItemAction.AddToPlaylist,
                            SharedMediaItemAction.CreatePlaylistAndAdd,
                            SharedMediaItemAction.CopyPlaylist,
                            SharedMediaItemAction.CopyPlaylistDeduplicated,
                            SharedMediaItemAction.Rename,
                            SharedMediaItemAction.EditSmartPlaylist,
                            SharedMediaItemAction.Delete,
                            SharedMediaItemAction.EditStation,
                            SharedMediaItemAction.DeleteStation,
                            -> Unit
                        }
                    }
                    SharedMediaItemKind.Playlist -> {
                        val playlist = homeState.playlists.firstOrNull { it.id == request.item.id }
                        if (playlist == null) {
                            status = "Playlist not found."
                        } else {
                            when (request.action) {
                                SharedMediaItemAction.Select -> openPlaylistDetails(playlist)
                                SharedMediaItemAction.Play -> playPlaylist(playlist, false)
                                SharedMediaItemAction.Shuffle -> playPlaylist(playlist, true)
                                SharedMediaItemAction.AddToQueue -> addPlaylistToQueue(playlist)
                                SharedMediaItemAction.Download -> {
                                    if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
                                        toggleKeepDownloadedPlaylist(playlist)
                                    } else {
                                        downloadPlaylist(playlist)
                                    }
                                }
                                SharedMediaItemAction.AddToPlaylist ->
                                    addPlaylistToPlaylist(playlist, request.playlistChoice, null)
                                SharedMediaItemAction.CreatePlaylistAndAdd ->
                                    addPlaylistToPlaylist(playlist, null, request.playlistName)
                                SharedMediaItemAction.CopyPlaylist ->
                                    addPlaylistToPlaylist(playlist, null, request.playlistName)
                                SharedMediaItemAction.CopyPlaylistDeduplicated -> {
                                    val tracks = if (selectedPlaylist?.id == playlist.id) {
                                        selectedPlaylistTracks.distinctBy { track -> track.id }
                                    } else {
                                        emptyList()
                                    }
                                    if (tracks.isNotEmpty()) {
                                        addTracksToPlaylist(tracks, null, request.playlistName, playlist.name)
                                    } else {
                                        status = "Open the playlist before copying a deduplicated version."
                                    }
                                }
                                SharedMediaItemAction.Rename ->
                                    request.textValue?.let { name -> renamePlaylist(playlist, name) }
                                SharedMediaItemAction.Delete -> deletePlaylist(playlist)
                                SharedMediaItemAction.StartRadio,
                                SharedMediaItemAction.FindSimilar,
                                SharedMediaItemAction.ToggleFavorite,
                                SharedMediaItemAction.EditSmartPlaylist,
                                SharedMediaItemAction.EditStation,
                                SharedMediaItemAction.DeleteStation,
                                -> Unit
                            }
                        }
                    }
                    SharedMediaItemKind.Unknown,
                    SharedMediaItemKind.RadioStation,
                    SharedMediaItemKind.MixBuilder,
                    -> Unit
                }
            },
            ),
            nowPlayingActions = NaviampNowPlayingActions(
            onPlaybackAction = { request ->
                when (request.action) {
                    NowPlayingPlaybackAction.Pause,
                    NowPlayingPlaybackAction.Resume,
                    NowPlayingPlaybackAction.PlayCurrent,
                    -> handleShellPlayPause()
                    NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let(performSeek)
                    NowPlayingPlaybackAction.Previous -> playAdjacentTrack(-1)
                    NowPlayingPlaybackAction.Next -> playAdjacentTrack(1)
                    NowPlayingPlaybackAction.ToggleShuffle -> handleShellToggleShuffle()
                    NowPlayingPlaybackAction.CycleRepeatMode -> {
                        repeatMode = sharedQueueCoordinator.cycleRepeatMode()
                    }
                    NowPlayingPlaybackAction.ChangeVolume -> request.volumePercent?.let { percent ->
                        val command = changePlaybackVolume(percent)
                        volumePercent = command.volumePercent
                    }
                }
            },
            onDisplayAction = { request ->
                when (request.action) {
                    NowPlayingDisplayAction.ToggleLyrics -> {
                        lyricsVisible = !lyricsVisible
                        if (lyricsVisible) {
                            nowPlaying?.let(loadLyrics)
                        }
                    }
                    NowPlayingDisplayAction.ChangeLyricsOffset ->
                        request.lyricsOffsetMillis?.let(handleLyricsOffsetChanged)
                    NowPlayingDisplayAction.ToggleVisualizer ->
                        visualizerRequestedVisible = !visualizerRequestedVisible
                    NowPlayingDisplayAction.SelectVisualizer -> request.visualizer?.let { visualizer ->
                        selectedVisualizer = visualizer
                        if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
                            nowPlaying?.let(loadLyrics)
                        }
                        settingsStore.saveVisualizerSettings(VisualizerSettings(selectedVisualizer = visualizer.name))
                        onSyncedSettingsChanged()
                    }
                    NowPlayingDisplayAction.SelectRadioDj -> {
                        val selectedDj = request.radioDjId
                            ?.let { id -> playbackSettings.radioDjs.firstOrNull { it.id == id } }
                        handlePlaybackSettingsChanged(
                            playbackSettings.copy(
                                radioTuning = selectedDj?.tuning ?: RadioTuningSettings(),
                                activeRadioDjId = selectedDj?.id,
                            ),
                        )
                        handleCurrentTrackRadioRefresh()
                        status = selectedDj
                            ?.let { "Selected ${it.name} DJ. Rebuilding Up Next..." }
                            ?: "Default radio selected. Rebuilding Up Next..."
                    }
                    NowPlayingDisplayAction.Collapse -> {
                        if (nowPlayingOpen) {
                            nowPlayingOpen = false
                        } else {
                            closeActiveDetail()
                        }
                    }
                }
            },
            onCurrentTrackAction = { request: NowPlayingCurrentTrackUiActionRequest ->
                when (request.action) {
                    NowPlayingCurrentTrackAction.StartRadio -> handleShellTrackRadio()
                    NowPlayingCurrentTrackAction.AddToPlaylist ->
                        handleNowPlayingAddToPlaylist(request.playlistChoice)
                    NowPlayingCurrentTrackAction.CreatePlaylistAndAdd ->
                        request.playlistName?.let(handleNowPlayingCreatePlaylistAndAdd)
                    NowPlayingCurrentTrackAction.Download -> nowPlaying?.let(downloadTrack)
                    NowPlayingCurrentTrackAction.GoToAlbum -> handleShellGoToAlbum()
                    NowPlayingCurrentTrackAction.GoToArtist ->
                        handleShellGoToArtist(request.artistId, request.artistName)
                    NowPlayingCurrentTrackAction.ToggleFavorite -> toggleCurrentFavorite()
                    NowPlayingCurrentTrackAction.SetRating -> handleShellRatingSelected(request.rating)
                }
            },
            onQueueAction = { request: NowPlayingQueueActionRequest ->
                when (request.action) {
                    NowPlayingQueueAction.SaveQueueAsPlaylist -> request.playlistName?.let(handleSaveQueueAsPlaylist)
                    NowPlayingQueueAction.MoveToNext -> request.queueIndex?.let(handleQueueItemMoveNext)
                    NowPlayingQueueAction.RemoveFromQueue -> request.queueIndex?.let(handleQueueItemRemoveFromQueue)
                    NowPlayingQueueAction.EmptyQueue -> handleEmptyQueue()
                }
            },
            onSleepTimerAction = { request: NowPlayingSleepTimerActionRequest ->
                when (request.action) {
                    NowPlayingSleepTimerAction.Select -> request.request?.let(handleSleepTimerSelected)
                    NowPlayingSleepTimerAction.Cancel -> handleCancelSleepTimer()
                }
            },
            onSelectionAction = { request: NowPlayingSelectionActionRequest ->
                when (request.action) {
                    NowPlayingSelectionAction.SelectQueueItem ->
                        nowPlayingQueueIndex(request.item)?.let(handleQueueItemSelected)
                    NowPlayingSelectionAction.SelectRelatedItem ->
                        resolveNowPlayingItemTrack(request.item)?.let { track ->
                            handleShellTrackSelected(
                                SharedTrackRowUi(
                                    id = track.id.value,
                                    title = request.item.title,
                                    subtitle = request.item.subtitle,
                                    coverArtUrl = request.item.coverArtUrl,
                                    meta = request.item.meta,
                                ),
                            )
                        }
                    NowPlayingSelectionAction.SelectRadioStation ->
                        homeState.radioStations.firstOrNull { it.id == request.item.id }
                            ?.let(handleRadioStationSelected)
                }
            },
            onQueueItemAction = handleQueueItemAction,
            ),
        )
    }
