package app.naviamp.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCoreAppSmokeTest {
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
}
