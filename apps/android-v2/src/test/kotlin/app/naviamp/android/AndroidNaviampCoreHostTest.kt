package app.naviamp.android

import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.createNaviampCore
import app.naviamp.testkit.naviampCoreTestServices
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AndroidNaviampCoreHostTest {
    @Test
    fun replacementHostConstructsSharedProductWithoutAndroidControllers() = runTest {
        val environment = androidNaviampCoreEnvironment(
            services = naviampCoreTestServices(),
            initialState = NaviampCoreInitialState(
                navigation = NaviampNavigationState(
                    route = NaviampRoute.Search,
                    lastContentRoute = NaviampRoute.Search,
                ),
            ),
        )

        val core = createNaviampCore(this, environment)

        assertEquals(SharedRoute.Search, core.state.value.shell.shellChrome.selectedRoute)
        assertNotNull(core.actions.settingsSync.onImportFile)
        assertNotNull(core.actions.settingsSync.onChooseFolder)
        assertNotNull(core.actions.settingsSync.onImportFolder)
        assertNotNull(core.actions.settingsSync.onExportFolder)
    }
}
