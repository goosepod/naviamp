package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.playbackVolumeCommand
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
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
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.resolveAction
import app.naviamp.ui.toNaviampRoute

fun androidAppShellActions(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    settingsStore: AndroidSettingsStore,
    handleConnectionFormChanged: (ConnectionFormState) -> Unit,
    connectToNavidrome: () -> Unit,
    handlePlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    handlePlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    handleClearCache: () -> Unit,
    handleClearLibrary: () -> Unit,
    handleResetDatabase: () -> Unit,
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
    startAndroidLibrarySync: (Boolean) -> Unit,
    handleShellTrackSelected: (SharedTrackRowUi) -> Unit,
    handleDownloadedTrackAction: (DownloadedTrackActionRequest) -> Unit,
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
    handleShellArtistShuffle: () -> Unit,
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
    addPlaylistToQueue: (Playlist) -> Unit,
    addPlaylistToPlaylist: (Playlist, NaviampPlaylistChoiceUi?, String?) -> Unit,
    renamePlaylist: (Playlist, String) -> Unit,
    deletePlaylist: (Playlist) -> Unit,
    saveSmartPlaylist: suspend (SmartPlaylistDefinition) -> Unit,
    updateSmartPlaylist: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
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
    closeActiveDetail: () -> Unit,
    handleShellResume: () -> Unit,
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
    handleShellGoToArtist: () -> Unit,
    handleShellQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    handleQueueItemPlayNext: (NaviampNowPlayingItemUi) -> Unit,
    handleQueueItemAddToQueue: (NaviampNowPlayingItemUi) -> Unit,
    handleTrackRadioNext: (Track) -> Unit,
    handleAddTrackRadioToQueue: (Track) -> Unit,
    resolveNowPlayingItemTrack: (NaviampNowPlayingItemUi) -> Track?,
    addTrackToPlaylist: (Track, NaviampPlaylistChoiceUi?, String?) -> Unit,
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
        }
    },
    toggleCurrentFavorite: () -> Unit,
    handleShellRatingSelected: (Int?) -> Unit,
): AndroidAppShellActions =
    with(state) {
        AndroidAppShellActions(
            onRouteSelected = { route ->
                navigationState = navigationState.copy(route = route.toNaviampRoute())
                contentState = contentState.clearDetails()
                nowPlayingOpen = false
            },
            onConnectionFormChanged = handleConnectionFormChanged,
            onConnect = { connectToNavidrome() },
            onEditConnection = { editingConnection = true },
            onCancelEditConnection = { editingConnection = false },
            onPlaybackSettingsChanged = handlePlaybackSettingsChanged,
            onPlaybackSettingsChangedAndRedownload = handlePlaybackSettingsChangedAndRedownload,
            onClearCache = handleClearCache,
            onClearLibrary = handleClearLibrary,
            onResetDatabase = handleResetDatabase,
            onQueryChanged = { contentState = contentState.copy(searchQuery = it) },
            onSearch = handleSearch,
            onClearSearch = {
                contentState = contentState.copy(
                    searchQuery = "",
                    searchResults = app.naviamp.domain.provider.MediaSearchResults(),
                )
                tracks = emptyList()
                status = ""
            },
            onArtistMixQueryChanged = { artistMixQuery = it },
            onArtistMixSearch = handleArtistMixSearch,
            onArtistMixArtistSelected = handleArtistMixArtistSelected,
            onArtistMixArtistRemoved = handleArtistMixArtistRemoved,
            onArtistMixReset = handleArtistMixReset,
            onArtistMixPlay = handleArtistMixPlay,
            onAlbumMixQueryChanged = { albumMixQuery = it },
            onAlbumMixSearch = handleAlbumMixSearch,
            onAlbumMixAlbumSelected = handleAlbumMixAlbumSelected,
            onAlbumMixAlbumRemoved = handleAlbumMixAlbumRemoved,
            onAlbumMixReset = handleAlbumMixReset,
            onAlbumMixPlay = handleAlbumMixPlay,
            onGenreMixQueryChanged = { genreMixQuery = it },
            onGenreMixSearch = handleGenreMixSearch,
            onGenreMixGenreSelected = handleGenreMixGenreSelected,
            onGenreMixGenreRemoved = handleGenreMixGenreRemoved,
            onGenreMixReset = handleGenreMixReset,
            onGenreMixPlay = handleGenreMixPlay,
            onSonicPathStartQueryChanged = handleSonicPathStartQueryChanged,
            onSonicPathEndQueryChanged = handleSonicPathEndQueryChanged,
            onSonicPathStartSearch = handleSonicPathStartSearch,
            onSonicPathEndSearch = handleSonicPathEndSearch,
            onSonicPathStartTrackSelected = handleSonicPathStartTrackSelected,
            onSonicPathEndTrackSelected = handleSonicPathEndTrackSelected,
            onSonicPathStartTrackCleared = handleSonicPathStartTrackCleared,
            onSonicPathEndTrackCleared = handleSonicPathEndTrackCleared,
            onSonicPathCountChanged = handleSonicPathCountChanged,
            onSonicPathBuild = handleSonicPathBuild,
            onSonicPathReset = handleSonicPathReset,
            onSonicPathPlay = handleSonicPathPlay,
            onSonicPathAddToQueue = handleSonicPathAddToQueue,
            onLibraryQueryChanged = { libraryQuery = it },
            onRefreshLibrary = { startAndroidLibrarySync(true) },
            onTrackSelected = handleShellTrackSelected,
            onDownloadedTrackAction = handleDownloadedTrackAction,
            onAlbumSelected = handleShellAlbumSelected,
            onAlbumFavoriteToggled = handleAlbumFavoriteToggled,
            onMixAlbumSelected = handleMixAlbumSelected,
            onAlbumPlay = { _, shuffle -> handleShellAlbumPlay(shuffle) },
            onAlbumTrackSelected = handleShellAlbumTrackSelected,
            onAlbumRadio = { handleShellAlbumRadio() },
            onAlbumAddToQueue = { appendTracksToQueue(albumDetail?.tracks.orEmpty(), "album tracks") },
            onAlbumDownload = { downloadTracks(albumDetail?.tracks.orEmpty(), "album") },
            onAlbumAddToPlaylist = { _, playlist ->
                addTracksToPlaylist(albumDetail?.tracks.orEmpty(), playlist, null, "album")
            },
            onAlbumCreatePlaylistAndAdd = { _, name ->
                addTracksToPlaylist(albumDetail?.tracks.orEmpty(), null, name, "album")
            },
            onTrackAction = handleTrackAction,
            onArtistRadio = handleShellArtistRadio,
            onArtistShuffle = { handleShellArtistShuffle() },
            onArtistAddToQueue = { loadArtistTracks { appendTracksToQueue(it, "artist tracks") } },
            onArtistAddToPlaylist = { _, playlist -> loadArtistTracks { addTracksToPlaylist(it, playlist, null, "artist") } },
            onArtistCreatePlaylistAndAdd = { _, name -> loadArtistTracks { addTracksToPlaylist(it, null, name, "artist") } },
            onArtistPopularPlay = handleArtistPopularPlay,
            onArtistPopularRadio = handleShellArtistPopularRadio,
            onArtistPopularTrackSelected = handleArtistPopularTrackSelected,
            onArtistPopularAddToQueue = handleArtistPopularAddToQueue,
            onFindSimilarArtists = { detail ->
                findSimilarArtists(app.naviamp.domain.ArtistId(detail.artist.id), detail.artist.title)
            },
            onSimilarArtistSelected = handleSimilarArtistSelected,
            onSimilarArtistExternalSelected = openExternalArtistUrl,
            onArtistSelected = { selectedArtist ->
                openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
            },
            onArtistFavoriteToggled = handleArtistFavoriteToggled,
            onPlaylistSelected = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(openPlaylistDetails)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistSortModeChanged = { playlistSortMode = it },
            onPlaylistPlay = { selectedPlaylist, shuffle ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistAddToQueue = { appendTracksToQueue(selectedPlaylistTracks, "playlist tracks") },
            onPlaylistAddToPlaylist = { _, playlist ->
                selectedPlaylist?.let { addPlaylistToPlaylist(it, playlist, null) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistCreatePlaylistAndAdd = { _, name ->
                selectedPlaylist?.let { addPlaylistToPlaylist(it, null, name) }
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
                                SharedMediaItemAction.Download -> downloadPlaylist(playlist)
                                SharedMediaItemAction.AddToPlaylist ->
                                    addPlaylistToPlaylist(playlist, request.playlistChoice, null)
                                SharedMediaItemAction.CreatePlaylistAndAdd ->
                                    addPlaylistToPlaylist(playlist, null, request.playlistName)
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
            onSmartPlaylistSave = { definition -> saveSmartPlaylist(definition) },
            onSmartPlaylistUpdate = { playlist, definition ->
                homeState.playlists.firstOrNull { it.id == playlist.id }?.let { updateSmartPlaylist(it, definition) }
                    ?: run { status = "Playlist not found." }
            },
            onSmartPlaylistLoad = { playlist ->
                homeState.playlists.firstOrNull { it.id == playlist.id }?.let { loadSmartPlaylist(it) }
                    ?: throw IllegalArgumentException("Playlist not found.")
            },
            onPlaylistBack = { closeActivePlaylist() },
            onPlaylistTrackSelected = handlePlaylistTrackSelected,
            onRecentRadioSelected = handleRecentRadioSelected,
            onMixBuilderSelected = handleMixBuilderSelected,
            onRadioStationSelected = handleRadioStationSelected,
            onRadioStationSave = saveInternetRadioStation,
            onStationAction = handleStationAction,
            onHomeStationSelected = handleShellHomeStationSelected,
            onOpenNowPlaying = { nowPlayingOpen = true },
            onCloseNowPlaying = {
                if (nowPlayingOpen) {
                    nowPlayingOpen = false
                } else {
                    closeActiveDetail()
                }
            },
            onNowPlayingPlaybackAction = { request ->
                when (request.action) {
                    NowPlayingPlaybackAction.Pause -> playbackEngine.pause()
                    NowPlayingPlaybackAction.Resume -> handleShellResume()
                    NowPlayingPlaybackAction.PlayCurrent -> handleShellResume()
                    NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let(performSeek)
                    NowPlayingPlaybackAction.Previous -> playAdjacentTrack(-1)
                    NowPlayingPlaybackAction.Next -> playAdjacentTrack(1)
                    NowPlayingPlaybackAction.ToggleShuffle -> handleShellToggleShuffle()
                    NowPlayingPlaybackAction.CycleRepeatMode -> {
                        repeatMode = PlaybackQueueManager().cycleRepeatMode(repeatMode)
                    }
                    NowPlayingPlaybackAction.ChangeVolume -> request.volumePercent?.let { percent ->
                        val command = playbackVolumeCommand(
                            requestedPercent = percent,
                            supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
                        )
                        volumePercent = command.volumePercent
                        if (command.shouldApplyToEngine) {
                            playbackEngine.setVolume(command.volumePercent)
                        }
                    }
                }
            },
            onNowPlayingDisplayAction = { request ->
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
                        settingsStore.saveVisualizerSettings(VisualizerSettings(selectedVisualizer = visualizer.name))
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
            onNowPlayingCurrentTrackAction = { request: NowPlayingCurrentTrackUiActionRequest ->
                when (request.action) {
                    NowPlayingCurrentTrackAction.StartRadio -> handleShellTrackRadio()
                    NowPlayingCurrentTrackAction.AddToPlaylist ->
                        handleNowPlayingAddToPlaylist(request.playlistChoice)
                    NowPlayingCurrentTrackAction.CreatePlaylistAndAdd ->
                        request.playlistName?.let(handleNowPlayingCreatePlaylistAndAdd)
                    NowPlayingCurrentTrackAction.Download -> nowPlaying?.let(downloadTrack)
                    NowPlayingCurrentTrackAction.GoToAlbum -> handleShellGoToAlbum()
                    NowPlayingCurrentTrackAction.GoToArtist -> handleShellGoToArtist()
                    NowPlayingCurrentTrackAction.ToggleFavorite -> toggleCurrentFavorite()
                    NowPlayingCurrentTrackAction.SetRating -> handleShellRatingSelected(request.rating)
                }
            },
            onNowPlayingQueueAction = { request: NowPlayingQueueActionRequest ->
                when (request.action) {
                    NowPlayingQueueAction.SaveQueueAsPlaylist -> handleSaveQueueAsPlaylist(request.playlistName)
                }
            },
            onNowPlayingSleepTimerAction = { request: NowPlayingSleepTimerActionRequest ->
                when (request.action) {
                    NowPlayingSleepTimerAction.Select -> request.request?.let(handleSleepTimerSelected)
                    NowPlayingSleepTimerAction.Cancel -> handleCancelSleepTimer()
                }
            },
            onNowPlayingSelectionAction = { request: NowPlayingSelectionActionRequest ->
                when (request.action) {
                    NowPlayingSelectionAction.SelectQueueItem,
                    NowPlayingSelectionAction.SelectRelatedItem -> {
                        handleShellTrackSelected(
                            SharedTrackRowUi(
                                id = request.item.id,
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
        )
    }
