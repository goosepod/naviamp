package app.naviamp.presentation

import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCoreActionGraphTest {
    @Test fun previewSyncUsesItsDedicatedAwaitedCommandAndForwardsProgress() = kotlinx.coroutines.test.runTest {
        val received = mutableListOf<app.naviamp.domain.library.LibrarySyncProgress>()
        val handler = object : NaviampCoreCommandHandler {
            override fun dispatch(command: NaviampCoreCommand) = error("Sync must be awaited")
            override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
                val sync = command as NaviampCoreCommand.SmartPlaylist.SyncLibrary
                sync.onProgress(app.naviamp.domain.library.LibrarySyncProgress(
                    app.naviamp.domain.library.LibrarySyncProgressPhase.LoadingTracks, trackCount = 500, total = 2000))
                return NaviampCoreCommandResult.Completed
            }
        }
        createNaviampCoreActions(handler).shell.playlistsActions.smartPlaylist.onSyncLibrary!!(received::add)
        assertEquals(500, received.single().trackCount)
        assertEquals(2000, received.single().total)
    }

    @Test fun artistPlaylistActionsUseSharedPlaylistNavigationAndRefreshCommands() {
        val handler = RecordingCoreCommandHandler()
        val actions = createNaviampCoreActions(handler).shell.artistDetailActions
        actions.onPlaylistSelected(SharedMediaItemUi("mine", "My playlist", ""))
        actions.onPlaylistsRefresh("artist")
        val selected = assertIs<NaviampCoreCommand.Media.ItemAction>(handler.dispatched[0])
        assertEquals("mine", selected.request.item.id)
        assertEquals(app.naviamp.ui.NaviampMediaItemCommand.Playlist(app.naviamp.ui.NaviampPlaylistMediaCommand.Select), selected.request.command)
        assertEquals(NaviampCoreCommand.Detail.RefreshArtistPlaylists("artist"), handler.dispatched[1])
    }
    @Test
    fun routesProductActionsFromEveryMajorAreaIntoCore() {
        val handler = RecordingCoreCommandHandler()
        val actions = createNaviampCoreActions(handler).shell

        actions.navigationActions.onRouteSelected(SharedRoute.Library)
        actions.connectionActions.onConnect()
        actions.valueActions.onPlaybackSettingsChanged(actionsStatePlaybackSettings())
        actions.maintenanceActions.onClearCache()
        actions.searchActions.onQueryChanged("query")
        actions.libraryActions.onLoadMore()
        actions.libraryActions.onTrackAction(
            SharedTrackRowActionRequest(
                SharedTrackRowUi("track", "Track", "Artist"),
                SharedTrackRowAction.Select,
            ),
        )
        actions.downloadsActions.onRefresh()
        actions.playlistsActions.onRefresh()
        actions.radioActions.onRefresh()
        actions.albumDetailActions.onBack()
        actions.artistDetailActions.onBack()
        actions.playlistDetailActions.onBack()
        actions.homeActions.onRefresh()
        actions.artistMixActions.onSearch()
        actions.albumMixActions.onSearch()
        actions.genreMixActions.onSearch()
        actions.sonicPathActions.onBuild()
        actions.sonicMixActions.onBuild()

        assertEquals(19, handler.dispatched.size)
        assertIs<NaviampCoreCommand.Navigation.SelectRoute>(handler.dispatched[0])
        assertIs<NaviampCoreCommand.Connection.Connect>(handler.dispatched[1])
        assertIs<NaviampCoreCommand.Settings.ChangePlayback>(handler.dispatched[2])
        assertIs<NaviampCoreCommand.Search.ChangeQuery>(handler.dispatched[4])
        assertIs<NaviampCoreCommand.Library.TrackAction>(handler.dispatched[6])
        assertTrue(handler.dispatched.contains(NaviampCoreCommand.Navigation.BackFromAlbum))
        assertTrue(handler.dispatched.contains(NaviampCoreCommand.Navigation.BackFromArtist))
        assertTrue(handler.dispatched.contains(NaviampCoreCommand.Navigation.BackFromPlaylist))
    }

    @Test
    fun keepsOptionalOsMechanismsCapabilityDrivenButCoreOwned() {
        val handler = RecordingCoreCommandHandler()
        val unavailable = createNaviampCoreActions(handler).settingsSync

        assertNull(unavailable.onImportFile)
        assertNull(unavailable.onChooseFolder)

        val available = createNaviampCoreActions(
            handler = handler,
            availability = NaviampCoreActionAvailability(
                importFile = true,
                chooseSyncFolder = true,
                importFolder = true,
                exportFolder = true,
            ),
        ).settingsSync

        assertNotNull(available.onImportFile).invoke()
        assertNotNull(available.onChooseFolder).invoke()
        assertNotNull(available.onImportFolder).invoke()
        assertNotNull(available.onExportFolder).invoke()

        assertEquals(
            listOf<NaviampCoreCommand>(
                NaviampCoreCommand.SettingsSync.ImportFile,
                NaviampCoreCommand.SettingsSync.ChooseFolder,
                NaviampCoreCommand.SettingsSync.ImportFolder,
                NaviampCoreCommand.SettingsSync.ExportFolder,
            ),
            handler.dispatched,
        )
    }

    @Test
    fun smartPlaylistRoundTripsThroughCoreRatherThanAHostCallback() = runTest {
        val definition = smartPlaylistDefinition()
        val handler = RecordingCoreCommandHandler(
            result = NaviampCoreCommandResult.SmartPlaylistLoaded(definition),
        )
        val smart = createNaviampCoreActions(handler).shell.playlistsActions.smartPlaylist
        val playlist = SharedMediaItemUi(id = "playlist-1", title = "Smart", subtitle = "")

        val loaded = smart.onLoadWithPassword(playlist, "secret")

        assertEquals(definition, loaded)
        val command = assertIs<NaviampCoreCommand.SmartPlaylist.Load>(handler.executed.single())
        assertEquals(playlist, command.playlist)
        assertEquals("secret", command.password)
    }

    @Test
    fun inspectionAndPasswordRecoveryUseTheCommonCommandGraph() = runTest {
        val editing = app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult.ReadOnly("original rules")
        val handler = RecordingCoreCommandHandler(NaviampCoreCommandResult.SmartPlaylistInspected(editing))
        val smart = createNaviampCoreActions(handler).shell.playlistsActions.smartPlaylist
        val item = SharedMediaItemUi("p", "Smart", "")
        assertEquals(editing, smart.onInspectWithPassword(item, "secret"))
        val command = assertIs<NaviampCoreCommand.SmartPlaylist.Inspect>(handler.executed.single())
        assertEquals(item, command.playlist)
        assertEquals("secret", command.password)
    }

    @Test
    fun builderLibrarySyncAwaitsTheDedicatedTrackSyncCommand() = runTest {
        val handler = RecordingCoreCommandHandler()
        val smart = createNaviampCoreActions(handler).shell.playlistsActions.smartPlaylist
        requireNotNull(smart.onSyncLibrary).invoke {}
        assertIs<NaviampCoreCommand.SmartPlaylist.SyncLibrary>(handler.executed.single())
        assertEquals(emptyList<NaviampCoreCommand>(), handler.dispatched)
    }

    private fun actionsStatePlaybackSettings() = app.naviamp.domain.settings.PlaybackSettings()

    private fun smartPlaylistDefinition() = SmartPlaylistDefinition(
        name = "Smart",
        rules = listOf(
            SmartPlaylistCondition(
                operator = SmartPlaylistOperator.Contains,
                field = "genre",
                value = SmartPlaylistValue.Text("ambient"),
            ),
        ),
    )
}

private class RecordingCoreCommandHandler(
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
