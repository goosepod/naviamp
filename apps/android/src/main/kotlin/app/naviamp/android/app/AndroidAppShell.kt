package app.naviamp.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.playbackVolumeCommand
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.resolveAction
import app.naviamp.ui.toNaviampRoute
import app.naviamp.ui.toSharedGenreMixItemUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toNaviampSleepTimerUi

@Composable
fun rememberAndroidAppShellUiState(
    state: AndroidAppState,
    modifier: Modifier,
    context: Context,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
): AndroidAppShellUiState =
    with(state) {
        val activeQueueForUi = playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }
        val streamQualityForUi = playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())
        val diagnostics = rememberAndroidDiagnostics(
            selectedRoute = selectedRoute,
            storageStats = storageStats,
            provider = provider,
            validation = validation,
            activeSourceId = activeSourceId,
            bassLoadReport = bassLoadReport,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackQueue = playbackQueue,
            playbackSettings = playbackSettings,
            streamQuality = streamQualityForUi,
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            nowPlayingOpen = nowPlayingOpen,
            visualizerVisible = visualizerVisible,
            activeTlsSettings = activeTlsSettings,
        )
        val shellModels = rememberAndroidShellModels(
            connectionName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
            provider = provider,
            homeState = homeState,
            playlistTracksById = playlistTracksById,
            searchResults = searchResults,
            libraryStatus = libraryStatus,
            isLibrarySyncing = isLibrarySyncing,
            downloadedTracks = downloadedTracks,
            selectedPlaylistTracks = selectedPlaylistTracks,
            selectedPlaylist = selectedPlaylist,
            albumDetail = albumDetail,
            artistDetail = artistDetail,
            artistPopularTracksByArtistId = artistPopularTracksByArtistId,
            artistPopularTracksStatusByArtistId = artistPopularTracksStatusByArtistId,
            artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId,
            artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId,
        )
        val nowPlayingUi = androidNowPlayingUi(
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            visualizerVisible = visualizerVisible,
            volumePercent = volumePercent,
            knownTracks = activeQueueForUi,
            repeatMode = repeatMode,
            shuffledUpNextSnapshot = shuffledUpNextSnapshot,
            waveformByTrackId = waveformByTrackId,
            audioTagsByTrackId = audioTagsByTrackId,
            lyricsByTrackId = lyricsByTrackId,
            lyricsStatusByTrackId = lyricsStatusByTrackId,
            lyricsVisible = lyricsVisible,
            nowPlayingOpen = nowPlayingOpen,
            streamQuality = streamQualityForUi,
            provider = provider,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            canSaveQueueAsPlaylist = playbackQueue.tracks.isNotEmpty(),
            sleepTimer = sleepTimer.toNaviampSleepTimerUi(sleepTimerNowEpochMillis),
            relatedTracks = relatedTracks,
            sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
            radioTrackArtworkByKey = radioTrackArtworkByKey,
            radioStations = homeState.radioStations,
        )

        AndroidAppShellUiState(
            modifier = modifier,
            status = status,
            serverVersion = validation?.serverVersion,
            connected = provider != null,
            editingConnection = editingConnection,
            restoringConnection = restoringConnection,
            connectionForm = shellModels.connectionForm,
            playbackSettings = playbackSettings,
            diagnostics = diagnostics,
            supportsReplayGain = playbackEngine.supportsReplayGain,
            supportsGapless = playbackEngine.supportsGapless,
            supportsCrossfade = playbackEngine.supportsCrossfade,
            supportsEqualizer = (playbackEngine as? EqualizerPlaybackEngine)?.supportsEqualizer == true,
            showMobileNetworkQuality = true,
            selectedVisualizer = selectedVisualizer,
            visualizerBandsProvider = { visualizerFrame?.bands.orEmpty() },
            query = query,
            home = shellModels.home,
            searchResults = shellModels.searchResults,
            artistMixBuilder = SharedArtistMixBuilderUi(
                query = artistMixQuery,
                selectedArtists = artistMixSelectedArtists.map { artist ->
                    artist.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                suggestedArtists = artistMixSuggestions.map { artist ->
                    artist.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                status = artistMixStatus,
                loading = artistMixLoading,
            ),
            albumMixBuilder = SharedAlbumMixBuilderUi(
                query = albumMixQuery,
                selectedAlbums = albumMixSelectedAlbums.map { album ->
                    album.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                suggestedAlbums = albumMixSuggestions.map { album ->
                    album.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                status = albumMixStatus,
                loading = albumMixLoading,
            ),
            genreMixBuilder = SharedGenreMixBuilderUi(
                query = genreMixQuery,
                selectedGenres = genreMixSelectedGenres.map { genre -> genre.toSharedGenreMixItemUi() },
                suggestedGenres = genreMixSuggestions.map { genre -> genre.toSharedGenreMixItemUi() },
                status = genreMixStatus,
                loading = genreMixLoading,
            ),
            libraryArtists = shellModels.libraryArtists,
            libraryQuery = libraryQuery,
            librarySyncStatus = shellModels.librarySyncStatus,
            downloads = shellModels.downloads,
            downloadBytes = storageStats.downloadBytes,
            maxDownloadBytes = AndroidMaxDownloadBytes,
            downloadStatus = downloadStatus,
            playlistItems = shellModels.playlistItems,
            recentPlaylistIds = recentPlaylistIds,
            playlistSortMode = playlistSortMode,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            radioStations = homeState.radioStations,
            albumDetail = shellModels.albumDetail,
            artistDetail = shellModels.artistDetail,
            playlistDetail = shellModels.playlistDetail,
            nowPlaying = nowPlayingUi,
            nowPlayingOpen = nowPlayingOpen,
            selectedRoute = selectedRoute,
        )
    }

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
    startAndroidLibrarySync: (Boolean) -> Unit,
    handleShellTrackSelected: (SharedTrackRowUi) -> Unit,
    handleDownloadedTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    handleDownloadedTrackAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleDownloadedTrackCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    removeDownload: (NaviampDownloadedTrackUi) -> Unit,
    handleShellAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    handleMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleShellAlbumPlay: (Boolean) -> Unit,
    handleShellAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    handleShellAlbumRadio: () -> Unit,
    appendTracksToQueue: (List<Track>, String) -> Unit,
    downloadTracks: (List<Track>, String) -> Unit,
    addTracksToPlaylist: (List<Track>, NaviampPlaylistChoiceUi?, String?, String) -> Unit,
    handleAlbumTrackDownload: (SharedTrackRowUi) -> Unit,
    handleAlbumTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleAlbumTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    handleShellArtistRadio: (SharedArtistDetailUi) -> Unit,
    handleShellArtistShuffle: () -> Unit,
    loadArtistTracks: ((List<Track>) -> Unit) -> Unit,
    handleArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    handleShellArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    handleArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    handleArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    handleTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    handleTrackDownload: (SharedTrackRowUi) -> Unit,
    handleTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
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
    resolveNowPlayingItemTrack: (NaviampNowPlayingItemUi) -> Track?,
    addTrackToPlaylist: (Track, NaviampPlaylistChoiceUi?, String?) -> Unit,
    handleQueueItemAction: (NowPlayingItemActionRequest) -> Unit = { request ->
        val action = request.resolveAction(fallbackTrack = resolveNowPlayingItemTrack(request.item))
        when (action.action) {
            NowPlayingItemAction.StartRadio -> handleShellQueueItemRadio(action.item)
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
            onLibraryQueryChanged = { libraryQuery = it },
            onRefreshLibrary = { startAndroidLibrarySync(true) },
            onTrackSelected = handleShellTrackSelected,
            onDownloadedTrackSelected = handleDownloadedTrackSelected,
            onDownloadedTrackAddToPlaylist = handleDownloadedTrackAddToPlaylist,
            onDownloadedTrackCreatePlaylistAndAdd = handleDownloadedTrackCreatePlaylistAndAdd,
            onRemoveDownload = removeDownload,
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
            onAlbumTrackDownload = handleAlbumTrackDownload,
            onAlbumTrackAddToPlaylist = handleAlbumTrackAddToPlaylist,
            onAlbumTrackCreatePlaylistAndAdd = handleAlbumTrackCreatePlaylistAndAdd,
            onArtistRadio = handleShellArtistRadio,
            onArtistShuffle = { handleShellArtistShuffle() },
            onArtistAddToQueue = { loadArtistTracks { appendTracksToQueue(it, "artist tracks") } },
            onArtistAddToPlaylist = { _, playlist -> loadArtistTracks { addTracksToPlaylist(it, playlist, null, "artist") } },
            onArtistCreatePlaylistAndAdd = { _, name -> loadArtistTracks { addTracksToPlaylist(it, null, name, "artist") } },
            onArtistPopularPlay = handleArtistPopularPlay,
            onArtistPopularRadio = handleShellArtistPopularRadio,
            onArtistPopularTrackSelected = handleArtistPopularTrackSelected,
            onArtistPopularAddToQueue = handleArtistPopularAddToQueue,
            onArtistPopularTrackAddToQueue = handleTrackAddToQueue,
            onArtistPopularTrackDownload = handleTrackDownload,
            onArtistPopularTrackAddToPlaylist = handleTrackAddToPlaylist,
            onArtistPopularTrackCreatePlaylistAndAdd = handleTrackCreatePlaylistAndAdd,
            onFindSimilarArtists = { detail ->
                findSimilarArtists(app.naviamp.domain.ArtistId(detail.artist.id), detail.artist.title)
            },
            onSimilarArtistSelected = handleSimilarArtistSelected,
            onSimilarArtistExternalSelected = openExternalArtistUrl,
            onArtistSelected = { selectedArtist ->
                openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
            },
            onArtistFavoriteToggled = handleArtistFavoriteToggled,
            onArtistAlbumRadio = handleArtistAlbumRadio,
            onArtistAlbumDownload = { selectedAlbum -> loadArtistAlbumTracks(selectedAlbum) { downloadTracks(it, selectedAlbum.title) } },
            onArtistAlbumAddToQueue = { selectedAlbum -> loadArtistAlbumTracks(selectedAlbum) { appendTracksToQueue(it, "album tracks") } },
            onArtistAlbumAddToPlaylist = { selectedAlbum, playlist ->
                loadArtistAlbumTracks(selectedAlbum) { addTracksToPlaylist(it, playlist, null, selectedAlbum.title) }
            },
            onArtistAlbumCreatePlaylistAndAdd = { selectedAlbum, name ->
                loadArtistAlbumTracks(selectedAlbum) { addTracksToPlaylist(it, null, name, selectedAlbum.title) }
            },
            onPlaylistSelected = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(openPlaylistDetails)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistSortModeChanged = { playlistSortMode = it },
            onPlaylistPlay = { selectedPlaylist, shuffle ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemAddToQueue = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(addPlaylistToQueue)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemAddToPlaylist = { selectedPlaylist, playlist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { addPlaylistToPlaylist(it, playlist, null) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemCreatePlaylistAndAdd = { selectedPlaylist, name ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { addPlaylistToPlaylist(it, null, name) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistAddToQueue = { appendTracksToQueue(selectedPlaylistTracks, "playlist tracks") },
            onPlaylistDownload = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(downloadPlaylist)
                    ?: run { status = "Playlist not found." }
            },
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
            onTrackAddToQueue = handleTrackAddToQueue,
            onRecentRadioSelected = handleRecentRadioSelected,
            onMixBuilderSelected = handleMixBuilderSelected,
            onRadioStationSelected = handleRadioStationSelected,
            onRadioStationSave = saveInternetRadioStation,
            onRadioStationDelete = deleteInternetRadioStation,
            onHomeStationSelected = handleShellHomeStationSelected,
            onOpenNowPlaying = { nowPlayingOpen = true },
            onCloseNowPlaying = {
                if (nowPlayingOpen) {
                    nowPlayingOpen = false
                } else {
                    closeActiveDetail()
                }
            },
            onPause = playbackEngine::pause,
            onResume = handleShellResume,
            onStop = playbackEngine::stop,
            onPrevious = { playAdjacentTrack(-1) },
            onNext = { playAdjacentTrack(1) },
            onSeek = performSeek,
            onVolumeChanged = { percent ->
                val command = playbackVolumeCommand(
                    requestedPercent = percent,
                    supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
                )
                volumePercent = command.volumePercent
                if (command.shouldApplyToEngine) {
                    playbackEngine.setVolume(command.volumePercent)
                }
            },
            onToggleShuffle = handleShellToggleShuffle,
            onCycleRepeatMode = {
                repeatMode = PlaybackQueueManager().cycleRepeatMode(repeatMode)
            },
            onToggleLyrics = {
                lyricsVisible = !lyricsVisible
                if (lyricsVisible) {
                    nowPlaying?.let(loadLyrics)
                }
            },
            onLyricsOffsetChanged = handleLyricsOffsetChanged,
            onToggleVisualizer = { visualizerRequestedVisible = !visualizerRequestedVisible },
            onVisualizerSelected = { visualizer ->
                selectedVisualizer = visualizer
                settingsStore.saveVisualizerSettings(VisualizerSettings(selectedVisualizer = visualizer.name))
            },
            onTrackRadio = handleShellTrackRadio,
            onAddToPlaylist = handleNowPlayingAddToPlaylist,
            onCreatePlaylistAndAdd = handleNowPlayingCreatePlaylistAndAdd,
            onSaveQueueAsPlaylist = handleSaveQueueAsPlaylist,
            onSleepTimerSelected = handleSleepTimerSelected,
            onCancelSleepTimer = handleCancelSleepTimer,
            onDownloadTrack = { nowPlaying?.let(downloadTrack) },
            onGoToAlbum = handleShellGoToAlbum,
            onGoToArtist = handleShellGoToArtist,
            onQueueItemRadio = handleShellQueueItemRadio,
            onQueueItemPlayNext = handleQueueItemPlayNext,
            onQueueItemAddToQueue = handleQueueItemAddToQueue,
            onQueueItemAddToPlaylist = { item, playlist ->
                resolveNowPlayingItemTrack(item)?.let { addTrackToPlaylist(it, playlist, null) }
            },
            onQueueItemCreatePlaylistAndAdd = { item, name ->
                resolveNowPlayingItemTrack(item)?.let { addTrackToPlaylist(it, null, name) }
            },
            onQueueItemDownload = { item -> resolveNowPlayingItemTrack(item)?.let(downloadTrack) },
            onQueueItemAction = handleQueueItemAction,
            onToggleFavorite = { toggleCurrentFavorite() },
            onRatingSelected = handleShellRatingSelected,
        )
    }
