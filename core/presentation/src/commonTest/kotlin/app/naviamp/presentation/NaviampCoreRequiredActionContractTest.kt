package app.naviamp.presentation

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import app.naviamp.ui.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NaviampCoreRequiredActionContractTest {
    @Test
    fun everyRequiredImmediateActionTerminatesInOneTypedCoreCommand() {
        val handler = CompleteActionRecordingHandler()
        val actions = createNaviampCoreActions(
            handler = handler,
            availability = NaviampCoreActionAvailability(
                importFile = true,
                chooseSyncFolder = true,
                importFolder = true,
                exportFolder = true,
            ),
        )
        val shell = actions.shell
        val item = SharedMediaItemUi("item", "Item", "Subtitle")
        val track = SharedTrackRowUi("track", "Track", "Artist")
        val trackRequest = SharedTrackRowActionRequest(track, SharedTrackRowAction.Select)
        val playlistChoice = NaviampPlaylistChoiceUi("playlist", "Playlist")
        val connection = NaviampSavedConnectionUi("source", "Source", "https://example.test", "user")

        shell.navigationActions.apply {
            onRouteSelected(SharedRoute.Library); onOpenNowPlaying(); onCloseNowPlaying()
        }
        shell.connectionActions.apply {
            onFormChanged(ConnectionFormState()); onConnect(); onEditCurrentConnection(); onNewConnection()
            onEditConnection(connection); onDeleteConnection(connection); onConnectSavedConnection(connection)
            onCancelConnectionForm()
        }
        shell.valueActions.apply {
            onInterfaceSettingsChanged(InterfaceSettings())
            onPlaybackSettingsChanged(PlaybackSettings())
            onPlaybackSettingsChangedAndRedownload(PlaybackSettings())
            onCacheSettingsChanged(CacheSettings())
            onDownloadLocationChanged(NaviampStorageLocationUi("download", "Download", "/download"))
            onAudioCacheLocationChanged(NaviampStorageLocationUi("cache", "Cache", "/cache"))
        }
        shell.maintenanceActions.apply {
            onOpenStatsForNerds(); onClearCache(); onClearLibrary(); onRefreshLibrary(); onResetDatabase()
        }
        shell.searchActions.apply { onQueryChanged("query"); onSearch(); onClear() }
        shell.artistMixActions.apply {
            onQueryChanged("artist"); onSearch(); onArtistSelected(item); onArtistRemoved(item); onReset(); onPlay()
        }
        shell.albumMixActions.apply {
            onQueryChanged("album"); onSearch(); onAlbumSelected(item); onAlbumRemoved(item); onReset(); onPlay()
        }
        val genre = SharedGenreMixItemUi("genre", "Genre")
        shell.genreMixActions.apply {
            onQueryChanged("genre"); onSearch(); onGenreSelected(genre); onGenreRemoved(genre); onReset(); onPlay()
        }
        shell.sonicPathActions.apply {
            onStartQueryChanged("start"); onEndQueryChanged("end"); onStartSearch(); onEndSearch()
            onStartTrackSelected(track); onEndTrackSelected(track); onStartTrackCleared(); onEndTrackCleared()
            onCountChanged(20); onBuild(); onReset(); onPlay(); onAddToQueue(); onSaveAsPlaylist("Path")
        }
        shell.sonicMixActions.apply {
            onQueryChanged("sonic"); onSearch(); onTrackSelected(track); onTrackRemoved(track)
            onTargetLengthChanged(30); onBiasChanged(SharedSonicMixBiasUi.Favorites)
            onBuild(); onReset(); onPlay(); onAddToQueue(); onSaveAsPlaylist("Mix")
        }
        val downloaded = NaviampDownloadedTrackUi("download", track, 100)
        shell.downloadsActions.apply {
            onTrackAction(DownloadedTrackActionRequest(downloaded, DownloadedTrackAction.Select))
            onCancelJob("job"); onRetryJob("job"); onRefresh(); onToggleKeepFavoritesDownloaded(); onDeleteAll()
        }
        shell.libraryActions.apply { onQueryChanged("library"); onRefresh(); onLoadMore(); onJumpToLetter('N') }
        shell.playlistsActions.apply { onRefresh(); onSortModeChanged(SharedPlaylistSortMode.RecentlyPlayed) }
        shell.radioActions.apply {
            onRefresh()
            onStationAction(StationRowActionRequest(item, StationRowAction.Select))
            onSaveStation(NaviampInternetRadioStationEditUi(name = "Station", streamUrl = "https://radio.test"))
        }
        shell.albumDetailActions.apply {
            onBack()
            onAlbumAction(NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.AddToPlaylist(playlistChoice)))
            onTrackAction(trackRequest)
        }
        shell.artistDetailActions.apply {
            onBack()
            onArtistAction(NaviampArtistDetailActionRequest(item, NaviampArtistDetailCommand.AddToPlaylist(playlistChoice)))
            onAlbumAction(NaviampArtistAlbumActionRequest(item, NaviampArtistAlbumCommand.AddToPlaylist(playlistChoice)))
            onPopularTrackAction(trackRequest)
        }
        shell.playlistDetailActions.apply {
            onBack()
            onPlaylistAction(NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.AddToQueue))
            onTrackAction(trackRequest)
        }
        shell.homeActions.apply {
            onRefresh(); onRecentRadioSelected(item); onInternetRadioStationSelected(item)
            onMixBuilderSelected(SharedMixBuilderUi("mix", "Mix", ""))
            onStationSelected(SharedHomeStationUi("station", "Station", ""))
            onSonicDiscoveryTrackAction(
                SharedHomeDiscoveryTrackActionRequest("row", track, SharedTrackRowAction.Select),
            )
            onRecentlyPlayedTrackAction(trackRequest)
        }
        shell.mediaActions.apply {
            onTrackAction(trackRequest)
            onMediaItemAction(NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.PlayAlbum))
        }
        val nowPlayingItem = NaviampNowPlayingItemUi("now", "Now", "Artist")
        shell.nowPlayingActions.apply {
            onPlaybackAction(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Pause))
            onDisplayAction(NowPlayingDisplayActionRequest(NowPlayingDisplayAction.ToggleLyrics))
            onCurrentTrackAction(NowPlayingCurrentTrackUiActionRequest(NowPlayingCurrentTrackAction.Download))
            onQueueAction(NowPlayingQueueActionRequest(NowPlayingQueueAction.EmptyQueue))
            onSleepTimerAction(NowPlayingSleepTimerActionRequest(NowPlayingSleepTimerAction.Cancel))
            onSelectionAction(NowPlayingSelectionActionRequest(nowPlayingItem, NowPlayingSelectionAction.SelectQueueItem))
            onQueueItemAction(
                NowPlayingItemActionRequest(
                    nowPlayingItem,
                    NowPlayingItemTarget.QueueIndex(0),
                    NowPlayingItemAction.RemoveFromQueue,
                ),
            )
        }
        actions.settingsSync.apply {
            onDirectoryChanged("/sync"); onDirectorySelectedForImport("/import"); onAutoExportChanged(true)
            onExport(); onImport(); assertNotNull(onImportFile).invoke(); assertNotNull(onChooseFolder).invoke()
            assertNotNull(onImportFolder).invoke(); assertNotNull(onExportFolder).invoke()
        }

        assertEquals(118, handler.dispatched.size)
        assertEquals(118, handler.dispatched.distinct().size)
    }

    @Test
    fun everyRequiredSuspendingActionTerminatesInOneTypedCoreCommand() = runTest {
        val definition = smartDefinition()
        val handler = CompleteActionRecordingHandler(
            result = NaviampCoreCommandResult.SmartPlaylistLoaded(definition),
        )
        val actions = createNaviampCoreActions(handler).shell
        val playlist = SharedMediaItemUi("playlist", "Playlist", "")
        val track = SharedTrackRowUi("track", "Track", "Artist")

        actions.playlistsActions.smartPlaylist.apply {
            onSave(definition)
            onUpdate(playlist, definition)
            onSaveWithPassword(definition, "password")
            onUpdateWithPassword(playlist, definition, "password")
            assertEquals(definition, onLoad(playlist))
            assertEquals(definition, onLoadWithPassword(playlist, "password"))
        }
        actions.playlistDetailActions.onUpdateStandardPlaylist(playlist, listOf(track))

        assertEquals(7, handler.executed.size)
        assertIs<NaviampCoreCommand.SmartPlaylist.Save>(handler.executed[0])
        assertIs<NaviampCoreCommand.SmartPlaylist.Update>(handler.executed[1])
        assertIs<NaviampCoreCommand.SmartPlaylist.Save>(handler.executed[2])
        assertIs<NaviampCoreCommand.SmartPlaylist.Update>(handler.executed[3])
        assertIs<NaviampCoreCommand.SmartPlaylist.Load>(handler.executed[4])
        assertIs<NaviampCoreCommand.SmartPlaylist.Load>(handler.executed[5])
        assertIs<NaviampCoreCommand.Playlists.UpdateTracks>(handler.executed[6])
    }

    @Test
    fun osAvailabilityOnlyAddsPickerActionsAndNeverRemovesProductActions() {
        val unavailableHandler = CompleteActionRecordingHandler()
        val availableHandler = CompleteActionRecordingHandler()
        val unavailable = createNaviampCoreActions(unavailableHandler)
        val available = createNaviampCoreActions(
            availableHandler,
            NaviampCoreActionAvailability(true, true, true, true),
        )

        unavailable.shell.navigationActions.onRouteSelected(SharedRoute.Home)
        available.shell.navigationActions.onRouteSelected(SharedRoute.Home)
        unavailable.shell.homeActions.onRefresh()
        available.shell.homeActions.onRefresh()
        unavailable.shell.nowPlayingActions.onPlaybackAction(
            NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Pause),
        )
        available.shell.nowPlayingActions.onPlaybackAction(
            NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Pause),
        )

        assertEquals(unavailableHandler.dispatched, availableHandler.dispatched)
        assertEquals(null, unavailable.settingsSync.onImportFile)
        assertNotNull(available.settingsSync.onImportFile)
    }

    private fun smartDefinition() = SmartPlaylistDefinition(
        name = "Smart",
        rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.Contains, "genre", SmartPlaylistValue.Text("ambient")),
        ),
    )
}

private class CompleteActionRecordingHandler(
    private val result: NaviampCoreCommandResult = NaviampCoreCommandResult.Completed,
) : NaviampCoreCommandHandler {
    val dispatched = mutableListOf<NaviampCoreCommand>()
    val executed = mutableListOf<NaviampCoreCommand>()

    override fun dispatch(command: NaviampCoreCommand) {
        dispatched += command
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
        executed += command
        return result
    }
}
