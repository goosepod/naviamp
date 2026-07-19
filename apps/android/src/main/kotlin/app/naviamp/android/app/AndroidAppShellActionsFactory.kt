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
    navigationActions: NaviampShellNavigationActions,
    connectionActions: NaviampConnectionSettingsActions,
    valueActions: NaviampSettingsValueActions,
    maintenanceActions: NaviampSettingsMaintenanceActions,
    searchActions: NaviampSearchActions,
    handlePlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    handlePlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    handleCurrentTrackRadioRefresh: () -> Unit,
    artistMixActions: SharedArtistMixBuilderActions,
    albumMixActions: SharedAlbumMixBuilderActions,
    genreMixActions: SharedGenreMixBuilderActions,
    sonicPathActions: SharedSonicPathBuilderActions,
    sonicMixActions: SharedSonicMixBuilderActions,
    downloadsActions: NaviampDownloadsActions,
    libraryActions: NaviampLibraryActions,
    playlistsActions: NaviampPlaylistsActions,
    radioActions: NaviampInternetRadioActions,
    albumDetailActions: NaviampAlbumDetailActions,
    artistDetailActions: NaviampArtistDetailActions,
    playlistDetailActions: NaviampPlaylistDetailActions,
    homeActions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    nowPlayingActions: NaviampNowPlayingActions,
    handleShellTrackSelected: (SharedTrackRowUi) -> Unit,
    handleShellAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    handleMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    appendTracksToQueue: (List<Track>, String) -> Unit,
    downloadTracks: (List<Track>, String) -> Unit,
    addTracksToPlaylist: (List<Track>, NaviampPlaylistChoiceUi?, String?, String) -> Unit,
    handleTrackAction: (SharedTrackRowActionRequest) -> Unit,
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
    handleRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    handleMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    handleRadioStationSelected: (InternetRadioStation) -> Unit,
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
            navigationActions = navigationActions,
            connectionActions = connectionActions,
            valueActions = valueActions,
            maintenanceActions = maintenanceActions,
            searchActions = searchActions,
            artistMixActions = artistMixActions,
            albumMixActions = albumMixActions,
            genreMixActions = genreMixActions,
            sonicPathActions = sonicPathActions,
            sonicMixActions = sonicMixActions,
            downloadsActions = downloadsActions,
            libraryActions = libraryActions,
            playlistsActions = playlistsActions,
            radioActions = radioActions,
            albumDetailActions = albumDetailActions,
            artistDetailActions = artistDetailActions,
            playlistDetailActions = playlistDetailActions,
            homeActions = homeActions,
            mediaActions = mediaActions,
            nowPlayingActions = nowPlayingActions,
        )
    }
