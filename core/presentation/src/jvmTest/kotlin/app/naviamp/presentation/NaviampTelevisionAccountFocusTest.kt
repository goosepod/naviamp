package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.ui.NaviampApplicationSurface
import app.naviamp.ui.SharedRoute
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionAccountFocusTest {
    @Test fun selectingCurrentAccountFocusesHomeWhileCancellationReturnsToAccounts() = runDesktopComposeUiTest(1920, 1080) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val provider = FakeCoreMediaProvider()
        val id = provider.id.value
        val core = NaviampCore.create(scope, fakeCoreServices(provider), initialState = NaviampCoreInitialState(
            connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = id),
            connectionInventory = NaviampCoreConnectionInventory(
                connections = listOf(NaviampCoreSavedConnectionRecord(id, "Fixture", "https://fixture.example", "listener")),
                currentSourceId = id),
        ))
        try {
            setContent { NaviampCoreApp(core, applicationSurface = NaviampApplicationSurface.Television) }
            val accounts = onNodeWithContentDescription("Accounts: listener")
            fun openAccounts() {
                onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus)
                accounts.performSemanticsAction(SemanticsActions.RequestFocus)
                accounts.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            }
            openAccounts()
            onNodeWithTag("account-card-$id").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            onNodeWithText("Home").assertIsFocused()
            assertEquals(SharedRoute.Home, core.state.value.shell.shellChrome.selectedRoute)
            openAccounts()
            onNodeWithTag("account-card-$id").performKeyInput { pressKey(Key.Back) }
            accounts.assertIsFocused()
        } finally { scope.cancel() }
    }
}
