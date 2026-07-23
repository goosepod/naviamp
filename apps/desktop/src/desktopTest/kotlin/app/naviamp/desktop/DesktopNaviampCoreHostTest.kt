package app.naviamp.desktop

import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.createNaviampCore
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.testkit.naviampCoreTestServices
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.NaviampConnectionCapabilitiesUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopNaviampCoreHostTest {
    @Test
    fun replacementHostConstructsTheSharedProductWithoutDesktopControllers() = runTest {
        val environment = NaviampCoreEnvironment(
            services = naviampCoreTestServices(),
            initialState = NaviampCoreInitialState(
                navigation = NaviampNavigationState(
                    route = NaviampRoute.Search,
                    lastContentRoute = NaviampRoute.Search,
                ),
            ),
            actionAvailability = DesktopCapabilityPresentation.toCoreActionAvailability(),
        )

        val core = createNaviampCore(this, environment)

        assertEquals(SharedRoute.Search, core.state.value.shell.shellChrome.selectedRoute)
        assertNotNull(core.actions.settingsSync.onImportFile)
        assertNotNull(core.actions.settingsSync.onChooseFolder)
        assertNotNull(core.actions.settingsSync.onImportFolder)
        assertNotNull(core.actions.settingsSync.onExportFolder)
    }

    @Test
    fun replacementEnvironmentInstallsOneRealConnectionAndContentBoundary() = runTest {
        val providerSessions = DesktopCoreProviderSessionPort(
            mediaSources = HostTestMediaSourceRepository(hostSavedSource()),
            sessionOpener = DesktopNavidromeSessionOpener { _, _ -> error("Network is not used by this test") },
            musicFolders = { emptyList() },
        )
        val environment = desktopNaviampCoreEnvironment(
            services = naviampCoreTestServices(),
            providerSessions = providerSessions,
            externalUri = app.naviamp.presentation.NaviampCoreExternalUriPort {},
            shellCapabilities = NaviampShellCapabilitiesUi(
                replayGain = true,
                gapless = true,
                crossfade = true,
                equalizer = true,
                connection = NaviampConnectionCapabilitiesUi(
                    insecureServerVerification = true,
                    customServerCertificates = true,
                    clientCertificates = true,
                ),
            ),
        )
        val core = createNaviampCore(this, environment)

        core.execute(
            NaviampCoreCommand.Connection.Edit(
                core.state.value.shell.connectionSettings.connection.savedConnections.single(),
            ),
        )

        assertEquals(
            "https://music.example",
            core.state.value.shell.connectionSettings.connection.form.serverUrl,
        )
        assertEquals("source-1", environment.initialState.connectionInventory.connections.single().id)
        assertEquals(
            NaviampConnectionCapabilitiesUi(true, true, true),
            core.state.value.shell.connectionSettings.capabilities,
        )
        assertEquals(true, core.state.value.shell.playback.replayGainAvailable)
        assertEquals(true, core.state.value.shell.playback.crossfadeAvailable)
        assertEquals(true, core.state.value.shell.playback.equalizerAvailable)
        assertNotNull(environment.applicationUpdateChecker)
    }
}

private fun hostSavedSource() = SavedMediaSource(
    id = "source-1",
    providerId = "navidrome",
    cacheNamespace = "navidrome:demo",
    displayName = "Home Music",
    baseUrl = "https://music.example",
    username = "demo",
    token = "token",
    salt = "salt",
    createdAtEpochMillis = 1L,
    lastConnectedAtEpochMillis = 2L,
    lastSyncStartedAtEpochMillis = null,
    lastSyncCompletedAtEpochMillis = null,
)

private class HostTestMediaSourceRepository(source: SavedMediaSource) : MediaSourceRepository {
    private val sources = mutableMapOf(source.id to source)

    override fun latestMediaSource() = sources.values.firstOrNull()
    override fun mediaSources() = sources.values.toList()
    override fun mediaSource(sourceId: String) = sources[sourceId]
    override fun deleteMediaSource(sourceId: String) {
        sources.remove(sourceId)
    }
}
