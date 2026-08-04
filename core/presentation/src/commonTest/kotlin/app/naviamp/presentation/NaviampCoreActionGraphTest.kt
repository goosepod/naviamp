package app.naviamp.presentation

import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCoreActionGraphTest {
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

        assertEquals(18, handler.dispatched.size)
        assertIs<NaviampCoreCommand.Navigation.SelectRoute>(handler.dispatched[0])
        assertIs<NaviampCoreCommand.Connection.Connect>(handler.dispatched[1])
        assertIs<NaviampCoreCommand.Settings.ChangePlayback>(handler.dispatched[2])
        assertIs<NaviampCoreCommand.Search.ChangeQuery>(handler.dispatched[4])
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
