package app.naviamp.android

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import kotlin.test.*

/** Opt-in real provider, SQLDelight, Keystore and TV shell on an empty temporary emulator. */
@OptIn(ExperimentalTestApi::class)
class AndroidTvAccountSwitcherAcceptanceTest {
    @Test fun twoStoredAccountsSwitchAndRecoverFromExpiredCredentials() = runComposeUiTest {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("tvAccountFixture") == "true")
        val core = runBlocking {
            withContext(Dispatchers.Main) {
                AndroidNaviampApplicationRuntime.get(InstrumentationRegistry.getInstrumentation().targetContext).core
            }
        }
        fun waitFor(predicate: () -> Boolean) = waitUntil(timeoutMillis = 60_000, condition = predicate)
        if (arguments.getString("tvAccountFixtureMode") == "reopen") {
            waitFor { core.state.value.shell.connectionSettings.connection.connected }
            val settings = core.state.value.shell.connectionSettings
            assertEquals(setOf("alice", "bob"), settings.connection.savedConnections.map { it.username }.toSet())
            assertEquals("alice", settings.connection.savedConnections.single { it.current }.username)
            return@runComposeUiTest
        }
        check(core.state.value.shell.connectionSettings.connection.savedConnections.isEmpty()) {
            "Requires a fresh disposable TV installation"
        }
        setContent {
            NaviampCoreApp(core, applicationSurface = NaviampApplicationSurface.Television)
        }
        fun connect(username: String, add: Boolean) {
            runOnIdle {
                if (add) core.actions.shell.connectionActions.onAddAccount()
                else core.dispatch(NaviampCoreCommand.Connection.New)
                core.dispatch(NaviampCoreCommand.Connection.ChangeForm(ConnectionFormState(
                    providerId = "subsonic", displayName = "Household fixture", serverUrl = "http://127.0.0.1:18080",
                    username = username, password = "fixture",
                )))
                core.dispatch(NaviampCoreCommand.Connection.Connect)
            }
            waitFor { core.state.value.shell.connectionSettings.connection.savedConnections.any { it.current && it.username == username } }
            waitFor { core.state.value.shell.playlists.playlists.any { it.title.startsWith(username + ":") } }
        }
        connect("alice", add = false)
        connect("bob", add = true)
        val settings = core.state.value.shell.connectionSettings
        assertEquals(2, settings.connection.savedConnections.size)
        val alice = settings.connection.savedConnections.single { it.username == "alice" }
        val bob = settings.connection.savedConnections.single { it.username == "bob" }
        runOnIdle { core.dispatch(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)) }
        waitFor { core.state.value.shell.library.songs.tracks.any { it.title.startsWith("bob:") } }
        fun focused(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        val profile = onNodeWithContentDescription("Accounts: bob")
        onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(10) {
            if (!focused(profile)) onAllNodes(isFocused())[0].performKeyInput { pressKey(Key.DirectionRight) }
        }
        profile.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        val aliceCard = onNodeWithTag("account-card-${alice.id}")
        val bobCard = onNodeWithTag("account-card-${bob.id}")
        bobCard.assertIsSelected().assertIsFocused()
        val direction = if (aliceCard.fetchSemanticsNode().boundsInRoot.left < bobCard.fetchSemanticsNode().boundsInRoot.left)
            Key.DirectionLeft else Key.DirectionRight
        URL("http://127.0.0.1:18080/_test/reject?user=alice").readText()
        try {
            bobCard.performKeyInput { pressKey(direction) }
            aliceCard.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            waitFor { core.state.value.shell.connectionSettings.accountSwitcher.error != null }
            waitFor { focused(aliceCard) }
            assertEquals(bob.id, core.state.value.shell.connectionSettings.currentSourceId)
            assertTrue(core.state.value.shell.connectionSettings.connection.connected)
            assertEquals(2, core.state.value.shell.connectionSettings.connection.savedConnections.size)
            assertTrue(core.state.value.shell.library.songs.tracks.all { it.title.startsWith("bob:") })
            bobCard.assertIsSelected()
        } finally {
            URL("http://127.0.0.1:18080/_test/allow?user=alice").readText()
        }
        aliceCard.performKeyInput { pressKey(Key.Enter) }
        waitFor { core.state.value.shell.connectionSettings.currentSourceId == alice.id }
        waitFor { core.state.value.shell.library.songs.tracks.any { it.title.startsWith("alice:") } }
        assertTrue(core.state.value.shell.library.songs.tracks.all { it.title.startsWith("alice:") })
        assertFalse(core.state.value.shell.connectionSettings.accountSwitcher.visible)
        waitFor { focused(onNodeWithContentDescription("Accounts: alice")) }
    }
}
