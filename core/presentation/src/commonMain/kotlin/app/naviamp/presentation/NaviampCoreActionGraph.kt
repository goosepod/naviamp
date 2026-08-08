package app.naviamp.presentation

import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.NaviampSmartPlaylistActions
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions

data class NaviampCoreActions(
    val shell: NaviampAppShellActions,
    val settingsSync: NaviampSettingsSyncActions,
)

/** Optional OS mechanisms affect visibility only; their product intent still terminates in Core. */
data class NaviampCoreActionAvailability(
    val importFile: Boolean = false,
    val chooseSyncFolder: Boolean = false,
    val importFolder: Boolean = false,
    val exportFolder: Boolean = false,
)

/** Builds the one action graph consumed unchanged by Android, Desktop, and iOS hosts. */
fun createNaviampCoreActions(
    handler: NaviampCoreCommandHandler,
    availability: NaviampCoreActionAvailability = NaviampCoreActionAvailability(),
): NaviampCoreActions {
    fun send(command: NaviampCoreCommand) = handler.dispatch(command)

    suspend fun loadSmartPlaylist(command: NaviampCoreCommand.SmartPlaylist.Load) =
        when (val result = handler.execute(command)) {
            is NaviampCoreCommandResult.SmartPlaylistLoaded -> result.definition
            NaviampCoreCommandResult.Completed -> error("Core did not return a smart-playlist definition.")
        }

    val smartPlaylist = NaviampSmartPlaylistActions(
        onSave = { definition -> handler.execute(NaviampCoreCommand.SmartPlaylist.Save(definition)) },
        onUpdate = { playlist, definition ->
            handler.execute(NaviampCoreCommand.SmartPlaylist.Update(playlist, definition))
        },
        onSaveWithPassword = { definition, password ->
            handler.execute(NaviampCoreCommand.SmartPlaylist.Save(definition, password))
        },
        onUpdateWithPassword = { playlist, definition, password ->
            handler.execute(NaviampCoreCommand.SmartPlaylist.Update(playlist, definition, password))
        },
        onLoad = { playlist -> loadSmartPlaylist(NaviampCoreCommand.SmartPlaylist.Load(playlist)) },
        onLoadWithPassword = { playlist, password ->
            loadSmartPlaylist(NaviampCoreCommand.SmartPlaylist.Load(playlist, password))
        },
    )

    val shell = NaviampAppShellActions(
        navigationActions = NaviampShellNavigationActions(
            onRouteSelected = { send(NaviampCoreCommand.Navigation.SelectRoute(it)) },
            onOpenNowPlaying = { send(NaviampCoreCommand.Navigation.OpenNowPlaying) },
            onCloseNowPlaying = { send(NaviampCoreCommand.Navigation.CloseNowPlaying) },
        ),
        connectionActions = NaviampConnectionSettingsActions(
            onFormChanged = { send(NaviampCoreCommand.Connection.ChangeForm(it)) },
            onConnect = { send(NaviampCoreCommand.Connection.Connect) },
            onEditCurrentConnection = { send(NaviampCoreCommand.Connection.EditCurrent) },
            onNewConnection = { send(NaviampCoreCommand.Connection.New) },
            onEditConnection = { send(NaviampCoreCommand.Connection.Edit(it)) },
            onDeleteConnection = { send(NaviampCoreCommand.Connection.Delete(it)) },
            onConnectSavedConnection = { send(NaviampCoreCommand.Connection.ConnectSaved(it)) },
            onCancelConnectionForm = { send(NaviampCoreCommand.Connection.CancelForm) },
        ),
        valueActions = NaviampSettingsValueActions(
            onInterfaceSettingsChanged = { send(NaviampCoreCommand.Settings.ChangeInterface(it)) },
            onPlaybackSettingsChanged = { send(NaviampCoreCommand.Settings.ChangePlayback(it, redownload = false)) },
            onPlaybackSettingsChangedAndRedownload = {
                send(NaviampCoreCommand.Settings.ChangePlayback(it, redownload = true))
            },
            onCacheSettingsChanged = { send(NaviampCoreCommand.Settings.ChangeCache(it)) },
            onDownloadLocationChanged = { send(NaviampCoreCommand.Settings.ChangeDownloadLocation(it)) },
            onAudioCacheLocationChanged = { send(NaviampCoreCommand.Settings.ChangeAudioCacheLocation(it)) },
        ),
        maintenanceActions = NaviampSettingsMaintenanceActions(
            onOpenStatsForNerds = { send(NaviampCoreCommand.Settings.OpenStats) },
            onClearCache = { send(NaviampCoreCommand.Settings.ClearCache) },
            onClearLibrary = { send(NaviampCoreCommand.Settings.ClearLibrary) },
            onRefreshLibrary = { send(NaviampCoreCommand.Settings.RefreshLibrary) },
            onResetDatabase = { send(NaviampCoreCommand.Settings.ResetDatabase) },
        ),
        searchActions = NaviampSearchActions(
            onQueryChanged = { send(NaviampCoreCommand.Search.ChangeQuery(it)) },
            onSearch = { send(NaviampCoreCommand.Search.Submit) },
            onClear = { send(NaviampCoreCommand.Search.Clear) },
        ),
        artistMixActions = SharedArtistMixBuilderActions(
            onQueryChanged = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.ChangeQuery(it))) },
            onSearch = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Search)) },
            onArtistSelected = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Select(it))) },
            onArtistRemoved = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Remove(it))) },
            onReset = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Reset)) },
            onPlay = { send(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Play)) },
        ),
        albumMixActions = SharedAlbumMixBuilderActions(
            onQueryChanged = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.ChangeQuery(it))) },
            onSearch = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Search)) },
            onAlbumSelected = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Select(it))) },
            onAlbumRemoved = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Remove(it))) },
            onReset = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Reset)) },
            onPlay = { send(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Play)) },
        ),
        genreMixActions = SharedGenreMixBuilderActions(
            onQueryChanged = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.ChangeQuery(it))) },
            onSearch = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Search)) },
            onGenreSelected = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Select(it))) },
            onGenreRemoved = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Remove(it))) },
            onReset = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Reset)) },
            onPlay = { send(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Play)) },
        ),
        sonicPathActions = SharedSonicPathBuilderActions(
            onStartQueryChanged = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.ChangeStartQuery(it))) },
            onEndQueryChanged = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.ChangeEndQuery(it))) },
            onStartSearch = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchStart)) },
            onEndSearch = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchEnd)) },
            onStartTrackSelected = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SelectStart(it))) },
            onEndTrackSelected = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SelectEnd(it))) },
            onStartTrackCleared = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.ClearStart)) },
            onEndTrackCleared = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.ClearEnd)) },
            onCountChanged = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.ChangeCount(it))) },
            onBuild = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Build)) },
            onReset = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Reset)) },
            onPlay = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Play)) },
            onAddToQueue = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.AddToQueue)) },
            onSaveAsPlaylist = { send(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SaveAsPlaylist(it))) },
        ),
        sonicMixActions = SharedSonicMixBuilderActions(
            onQueryChanged = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.ChangeQuery(it))) },
            onSearch = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Search)) },
            onTrackSelected = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Select(it))) },
            onTrackRemoved = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Remove(it))) },
            onTargetLengthChanged = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.ChangeLength(it))) },
            onBiasChanged = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.ChangeBias(it))) },
            onIncludeSeedsChanged = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.ChangeIncludeSeeds(it))) },
            onBuild = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Build)) },
            onReset = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Reset)) },
            onPlay = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.Play)) },
            onAddToQueue = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.AddToQueue)) },
            onSaveAsPlaylist = { send(NaviampCoreCommand.MixBuilder.SonicMix(NaviampCoreCommand.SonicMixAction.SaveAsPlaylist(it))) },
        ),
        downloadsActions = NaviampDownloadsActions(
            onTrackAction = { send(NaviampCoreCommand.Downloads.TrackAction(it)) },
            onCancelJob = { send(NaviampCoreCommand.Downloads.CancelJob(it)) },
            onRetryJob = { send(NaviampCoreCommand.Downloads.RetryJob(it)) },
            onRefresh = { send(NaviampCoreCommand.Downloads.Refresh) },
            onToggleKeepFavoritesDownloaded = { send(NaviampCoreCommand.Downloads.ToggleKeepFavorites) },
            onDeleteAll = { send(NaviampCoreCommand.Downloads.DeleteAll) },
        ),
        libraryActions = NaviampLibraryActions(
            onQueryChanged = { send(NaviampCoreCommand.Library.ChangeQuery(it)) },
            onRefresh = { send(NaviampCoreCommand.Library.Refresh) },
            onLoadMore = { send(NaviampCoreCommand.Library.LoadMore) },
            onJumpToLetter = { send(NaviampCoreCommand.Library.JumpToLetter(it)) },
        ),
        playlistsActions = NaviampPlaylistsActions(
            onRefresh = { send(NaviampCoreCommand.Playlists.Refresh) },
            onSortModeChanged = { send(NaviampCoreCommand.Playlists.ChangeSort(it)) },
            smartPlaylist = smartPlaylist,
        ),
        radioActions = NaviampInternetRadioActions(
            onRefresh = { send(NaviampCoreCommand.Radio.Refresh) },
            onStationAction = { send(NaviampCoreCommand.Radio.StationAction(it)) },
            onSaveStation = { send(NaviampCoreCommand.Radio.SaveStation(it)) },
        ),
        albumDetailActions = NaviampAlbumDetailActions(
            onBack = { send(NaviampCoreCommand.Navigation.BackFromAlbum) },
            onAlbumAction = { send(NaviampCoreCommand.Detail.Album(it)) },
            onTrackAction = { send(NaviampCoreCommand.Detail.AlbumTrack(it)) },
            onArtistSelected = { artist ->
                send(
                    NaviampCoreCommand.Media.ItemAction(
                        app.naviamp.ui.NaviampMediaItemActionRequest(
                            artist,
                            app.naviamp.ui.NaviampMediaItemCommand.Artist(
                                app.naviamp.ui.NaviampArtistMediaCommand.Select,
                            ),
                        ),
                    ),
                )
            },
        ),
        artistDetailActions = NaviampArtistDetailActions(
            onBack = { send(NaviampCoreCommand.Navigation.BackFromArtist) },
            onArtistAction = { send(NaviampCoreCommand.Detail.Artist(it)) },
            onAlbumAction = { send(NaviampCoreCommand.Detail.ArtistAlbum(it)) },
            onPopularTrackAction = { send(NaviampCoreCommand.Detail.ArtistPopularTrack(it)) },
        ),
        playlistDetailActions = NaviampPlaylistDetailActions(
            onBack = { send(NaviampCoreCommand.Navigation.BackFromPlaylist) },
            onPlaylistAction = { send(NaviampCoreCommand.Playlists.Detail(it)) },
            onUpdateStandardPlaylist = { playlist, tracks ->
                handler.execute(NaviampCoreCommand.Playlists.UpdateTracks(playlist, tracks))
            },
            onTrackAction = { send(NaviampCoreCommand.Detail.PlaylistTrack(it)) },
        ),
        homeActions = NaviampHomeActions(
            onRefresh = { send(NaviampCoreCommand.Home.Refresh) },
            onRecentRadioSelected = { send(NaviampCoreCommand.Home.SelectRecentRadio(it)) },
            onInternetRadioStationSelected = { send(NaviampCoreCommand.Home.SelectInternetRadio(it)) },
            onMixBuilderSelected = { send(NaviampCoreCommand.Home.SelectMixBuilder(it)) },
            onStationSelected = { send(NaviampCoreCommand.Home.SelectStation(it)) },
            onSonicDiscoveryTrackAction = { send(NaviampCoreCommand.Home.SonicTrackAction(it)) },
            onRecentlyPlayedTrackAction = { send(NaviampCoreCommand.Home.RecentTrackAction(it)) },
            onCollectionSelected = { send(NaviampCoreCommand.Home.OpenCollection(it)) },
            onCollectionBack = { send(NaviampCoreCommand.Home.CloseCollection) },
            onCollectionPageLayoutChanged = { sectionId, layout ->
                send(NaviampCoreCommand.Settings.ChangeHomeSectionPageLayout(sectionId, layout))
            },
        ),
        mediaActions = NaviampMediaActions(
            onTrackAction = { send(NaviampCoreCommand.Media.TrackAction(it)) },
            onMediaItemAction = { send(NaviampCoreCommand.Media.ItemAction(it)) },
        ),
        nowPlayingActions = NaviampNowPlayingActions(
            onPlaybackAction = { send(NaviampCoreCommand.NowPlaying.Playback(it)) },
            onDisplayAction = { send(NaviampCoreCommand.NowPlaying.Display(it)) },
            onCurrentTrackAction = { send(NaviampCoreCommand.NowPlaying.CurrentTrack(it)) },
            onQueueAction = { send(NaviampCoreCommand.NowPlaying.Queue(it)) },
            onSleepTimerAction = { send(NaviampCoreCommand.NowPlaying.SleepTimer(it)) },
            onSelectionAction = { send(NaviampCoreCommand.NowPlaying.Selection(it)) },
            onQueueItemAction = { send(NaviampCoreCommand.NowPlaying.QueueItem(it)) },
        ),
    )

    val settingsSync = NaviampSettingsSyncActions(
        onDirectoryChanged = { send(NaviampCoreCommand.SettingsSync.ChangeDirectory(it)) },
        onDirectorySelectedForImport = { send(NaviampCoreCommand.SettingsSync.SelectImportDirectory(it)) },
        onAutoExportChanged = { send(NaviampCoreCommand.SettingsSync.ChangeAutoExport(it)) },
        onExport = { send(NaviampCoreCommand.SettingsSync.Export) },
        onImport = { send(NaviampCoreCommand.SettingsSync.Import) },
        onImportFile = if (availability.importFile) ({ send(NaviampCoreCommand.SettingsSync.ImportFile) }) else null,
        onChooseFolder = if (availability.chooseSyncFolder) ({ send(NaviampCoreCommand.SettingsSync.ChooseFolder) }) else null,
        onImportFolder = if (availability.importFolder) ({ send(NaviampCoreCommand.SettingsSync.ImportFolder) }) else null,
        onExportFolder = if (availability.exportFolder) ({ send(NaviampCoreCommand.SettingsSync.ExportFolder) }) else null,
    )

    return NaviampCoreActions(shell = shell, settingsSync = settingsSync)
}
