package app.naviamp.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampCoreAppSmokeTest {
    @Test
    fun connectionEditingUsesOneNavigationBarWhileThePlayerRemainsSelected() = runDesktopComposeUiTest(1000, 740) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val core = NaviampCore.create(scope, fakeCoreServices())
        val ui = mutableStateOf(app.naviamp.ui.NaviampAppShellUiState(
            connectionSettings = app.naviamp.ui.NaviampConnectionSettingsUi(
                connection = app.naviamp.ui.NaviampShellConnectionUi(connected = true),
            ),
            shellChrome = app.naviamp.ui.NaviampShellChromeUi(
                selectedRoute = SharedRoute.Settings, nowPlayingOpen = true,
            ),
            nowPlaying = app.naviamp.ui.NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused"),
        ))
        try {
            setContent {
                app.naviamp.ui.NaviampSharedAppShell(
                    uiState = ui.value, actions = core.actions.shell, syncActions = core.actions.settingsSync,
                )
            }
            onAllNodesWithContentDescription("Home").assertCountEquals(1)
            onNodeWithTag("docked-player").assertExists()
            ui.value = ui.value.copy(connectionSettings = ui.value.connectionSettings.copy(
                connection = ui.value.connectionSettings.connection.copy(editingConnection = true, editingSavedConnection = true),
            ))
            waitForIdle()
            onAllNodesWithContentDescription("Home").assertCountEquals(1)
            onNodeWithTag("docked-player").assertDoesNotExist()
            onNodeWithTag("connection-name").assertExists()
            ui.value = ui.value.copy(connectionSettings = ui.value.connectionSettings.copy(
                connection = ui.value.connectionSettings.connection.copy(editingConnection = false),
            ))
            waitForIdle()
            onAllNodesWithContentDescription("Home").assertCountEquals(1)
            onNodeWithTag("docked-player").assertExists()
            onNodeWithTag("connection-name").assertDoesNotExist()
            ui.value = ui.value.copy(connectionSettings = ui.value.connectionSettings.copy(
                connection = ui.value.connectionSettings.connection.copy(restoringConnection = true),
            ))
            waitForIdle()
            onAllNodesWithContentDescription("Home").assertCountEquals(1)
            onNodeWithTag("docked-player").assertDoesNotExist()
        } finally { scope.cancel() }
    }

    @Test
    fun fakeHostMountsAndNavigatesEveryProductRoute() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val core = NaviampCore.create(scope, fakeCoreServices())

        try {
            setContent { NaviampCoreApp(core) }
            waitForIdle()

            SharedRoute.entries.forEach { route ->
                core.actions.shell.navigationActions.onRouteSelected(route)
                waitForIdle()
                assertEquals(route, core.state.value.shell.shellChrome.selectedRoute)
            }

            core.actions.shell.navigationActions.onOpenNowPlaying()
            waitForIdle()
            assertEquals(true, core.state.value.shell.shellChrome.nowPlayingOpen)

            core.actions.shell.navigationActions.onCloseNowPlaying()
            waitForIdle()
            assertEquals(false, core.state.value.shell.shellChrome.nowPlayingOpen)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun fakeHostLoadsAndRendersAlbumArtistAndPlaylistDetails() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val provider = FakeCoreMediaProvider()
        val core = NaviampCore.create(
            scope = scope,
            services = fakeCoreServices(provider),
            initialState = NaviampCoreInitialState(
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = provider.id.value,
                    status = "Connected.",
                ),
            ),
        )

        try {
            setContent { NaviampCoreApp(core) }
            waitForIdle()

            core.actions.shell.mediaActions.onMediaItemAction(
                NaviampMediaItemActionRequest(
                    item = SharedMediaItemUi(provider.album.id.value, provider.album.title, provider.artist.name),
                    command = NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select),
                ),
            )
            waitForIdle()
            assertNotNull(core.state.value.shell.albumDetail.detail)
            assertTrue(onAllNodesWithText(provider.album.title).fetchSemanticsNodes().isNotEmpty())

            core.actions.shell.mediaActions.onMediaItemAction(
                NaviampMediaItemActionRequest(
                    item = SharedMediaItemUi(provider.artist.id.value, provider.artist.name, ""),
                    command = NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select),
                ),
            )
            waitForIdle()
            assertNotNull(core.state.value.shell.artistDetail.detail)
            assertTrue(onAllNodesWithText(provider.artist.name).fetchSemanticsNodes().isNotEmpty())

            core.actions.shell.mediaActions.onMediaItemAction(
                NaviampMediaItemActionRequest(
                    item = SharedMediaItemUi(
                        id = provider.playlist.id,
                        title = provider.playlist.name,
                        subtitle = "1 track",
                        trackCount = provider.playlist.trackCount,
                    ),
                    command = NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Select),
                ),
            )
            waitForIdle()
            assertNotNull(core.state.value.shell.playlistDetail.detail)
            assertTrue(onAllNodesWithText(provider.playlist.name).fetchSemanticsNodes().isNotEmpty())
        } finally {
            scope.cancel()
        }
    }
}
