package app.naviamp.presentation

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.ui.SharedHome
import app.naviamp.ui.NaviampColors
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampRecentRadioPresentationTest {
    @Test fun repeatedReplaysRenderOneRecentStation() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val station = libraryRecentRadioStream().copy(startedAtEpochMillis = 1,
            sessionTracks = listOf(SavedTrack("one", "One", artistName = "Artist")))
        var stored = listOf(station, station.copy(id = "library:session:0", startedAtEpochMillis = 0))
        var now = 10L
        val defaults = fakeCoreServices()
        val core = NaviampCore.create(scope, defaults.copy(clockEpochMillis = { now },
            radio = defaults.radio.copy(generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                load = { stored }, save = { stored = it }))))
        try {
            runBlocking { core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
                SharedMediaItemUi(station.id, station.label, "Radio"))) }
            setContent {
                val shell = core.state.collectAsState().value.shell
                SharedHome(
                    colors = NaviampColors.Dark,
                    home = shell.home.content.copy(collectionSections = shell.home.content.collectionSections
                        .filter { it.id == app.naviamp.domain.settings.HomeSectionIds.RecentRadio }
                        .map { it.copy(visible = true) }),
                    actions = core.actions.shell.homeActions, mediaActions = core.actions.shell.mediaActions,
                )
            }
            repeat(3) {
                now++
                runBlocking { core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
                    SharedMediaItemUi(station.id, station.label, "Radio"))) }
                waitForIdle()
                onAllNodesWithText(station.label).assertCountEquals(1)
                assertEquals(1, stored.size)
                assertEquals(now, stored.single().startedAtEpochMillis)
            }
        } finally {
            core.close()
            scope.cancel()
        }
    }
}
