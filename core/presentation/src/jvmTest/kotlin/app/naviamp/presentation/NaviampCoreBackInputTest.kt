package app.naviamp.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import app.naviamp.domain.provider.*
import app.naviamp.ui.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampCoreBackInputTest {
    @Test fun mouseAndPhoneDispatcherUseTheSameVisibleAurralBackCommand() = runDesktopComposeUiTest(1100, 850) {
        val commands = mutableListOf<NaviampCoreCommand>()
        val handler = object : NaviampCoreCommandHandler {
            override fun dispatch(command: NaviampCoreCommand) { commands += command }
            override suspend fun execute(command: NaviampCoreCommand) = NaviampCoreCommandResult.Completed
        }
        val actions = createNaviampCoreActions(handler)
        val dispatcher = NaviampSystemBackDispatcher()
        val state = NaviampAppShellUiState(connectionSettings = NaviampConnectionSettingsUi(
            connection = NaviampShellConnectionUi(connected = true)), home = NaviampHomeScreenUi(
            providerDiscovery = SharedProviderDiscoveryUi(ProviderIdAurral, browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Hub))))
        setContent { CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
            NaviampSharedAppShell(uiState = state, actions = actions.shell, syncActions = actions.settingsSync)
        } }
        onNodeWithText("Back").assertIsDisplayed()
        runOnIdle { commands.clear() } // Initial viewport reports are not navigation input.
        onRoot().performMouseInput { moveTo(center); click(button = MouseButton(3)) }
        assertEquals(1, commands.size, commands.toString())
        runOnIdle { assertNotNull(dispatcher.currentHandler).invoke() }
        assertEquals(2, commands.size, commands.toString())
        assertEquals(commands[0], commands[1])
        val browse = commands.first() as NaviampCoreCommand.Home.Browse
        assertEquals(DiscoveryBrowseAction.Close, (browse.action as DiscoveryBrowseAction.Scoped).action)
    }
}
