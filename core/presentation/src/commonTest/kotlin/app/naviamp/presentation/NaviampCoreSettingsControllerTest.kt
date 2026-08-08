package app.naviamp.presentation

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.HomeSectionPageLayout
import app.naviamp.domain.settings.homeSectionPresentation
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.normalized
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreSettingsControllerTest {
    @Test
    fun dedicatedHomeSectionPageLayoutIsPersistedInInterfaceSettings() {
        val fixture = fixture()

        fixture.controller.dispatch(
            NaviampCoreCommand.Settings.ChangeHomeSectionPageLayout(
                sectionId = "navibeat-mixes",
                layout = HomeSectionPageLayout.List,
            ),
        )

        val saved = fixture.savedInterface.single()
        assertEquals(HomeSectionPageLayout.List, saved.homeSectionPresentation("navibeat-mixes").pageLayout)
        assertEquals(saved, fixture.store.state.value.shell.general.interfaceSettings)
    }

    @Test
    fun appliesAndPublishesSettingsThroughCommonPolicyControllers() {
        val fixture = fixture()
        val interfaceSettings = InterfaceSettings()
        val playbackSettings = PlaybackSettings(volumePercent = 47)
        val cacheSettings = CacheSettings(
            customDownloadDirectory = "  /downloads  ",
            maxDownloadBytes = 12_000,
            maxAudioCacheBytes = 8_000,
        )

        fixture.controller.dispatch(NaviampCoreCommand.Settings.ChangeInterface(interfaceSettings))
        fixture.controller.dispatch(NaviampCoreCommand.Settings.ChangePlayback(playbackSettings, redownload = false))
        fixture.controller.dispatch(NaviampCoreCommand.Settings.ChangeCache(cacheSettings))

        assertEquals(interfaceSettings.normalized(), fixture.savedInterface.single())
        assertEquals(47, fixture.store.state.value.shell.playback.settings.volumePercent)
        assertEquals("/downloads", fixture.store.state.value.shell.cache.settings.customDownloadDirectory)
        assertEquals(cacheSettings.normalized().maxDownloadBytes, fixture.store.state.value.shell.downloads.maxDownloadBytes)
        assertEquals(
            cacheSettings.normalized().maxAudioCacheBytes,
            fixture.store.state.value.shell.downloads.offlineDashboard.maxAudioCacheBytes,
        )
        assertEquals(fixture.store.state.value.shell.playback.settings, fixture.savedPlayback.single())
        assertEquals(fixture.store.state.value.shell.cache.settings, fixture.savedCache.single())
    }

    @Test
    fun storageLocationsUpdateTheSameCoreCacheSettingsTransaction() {
        val fixture = fixture()

        fixture.controller.dispatch(
            NaviampCoreCommand.Settings.ChangeDownloadLocation(
                NaviampStorageLocationUi("downloads", "Downloads", "/media/downloads"),
            ),
        )
        fixture.controller.dispatch(
            NaviampCoreCommand.Settings.ChangeAudioCacheLocation(
                NaviampStorageLocationUi("cache", "Cache", "/media/cache"),
            ),
        )

        val settings = fixture.store.state.value.shell.cache.settings
        assertEquals("/media/downloads", settings.customDownloadDirectory)
        assertEquals("/media/cache", settings.customAudioCacheDirectory)
        assertEquals("downloads", fixture.store.state.value.shell.cache.selectedDownloadLocationId)
        assertEquals("cache", fixture.store.state.value.shell.cache.selectedAudioCacheLocationId)
    }

    @Test
    fun statsOverlayIsCoreProductState() {
        val fixture = fixture()

        fixture.controller.dispatch(NaviampCoreCommand.Settings.OpenStats)
        assertTrue(fixture.store.state.value.overlays.statsForNerdsVisible)

        fixture.controller.dispatch(NaviampCoreCommand.Settings.CloseStats)
        assertFalse(fixture.store.state.value.overlays.statsForNerdsVisible)
    }

    @Test
    fun maintenanceRunsAsADeferredCoreTransactionAndPublishesItsResult() = runTest {
        val fixture = fixture(
            maintenance = { operation -> NaviampCoreMaintenanceResult("Completed $operation") },
        )
        val router = NaviampCoreCommandRouter(this, listOf(fixture.controller))

        router.dispatch(NaviampCoreCommand.Settings.ClearLibrary)
        advanceUntilIdle()

        assertEquals("Completed ClearLibrary", fixture.store.state.value.overlays.status)
    }

    @Test
    fun maintenanceRepublishesStorageDiagnosticsAndClearsResetDownloads() = runTest {
        var resetCompleted = false
        val fixture = fixture(
            maintenance = {
                NaviampCoreMaintenanceResult(
                    status = "Database reset.",
                    storageStats = StorageCacheStats(),
                )
            },
            onDatabaseReset = { resetCompleted = true },
        )
        fixture.store.updateShell { shell ->
            shell.copy(
                downloads = shell.downloads.copy(
                    downloads = listOf(
                        NaviampDownloadedTrackUi(
                            id = "download",
                            track = SharedTrackRowUi("track", "Track", "Artist"),
                            sizeBytes = 42,
                        ),
                    ),
                    downloadBytes = 42,
                    offlineDashboard = shell.downloads.offlineDashboard.copy(
                        audioCacheCount = 2,
                        audioCacheBytes = 84,
                    ),
                ),
            )
        }

        fixture.controller.execute(NaviampCoreCommand.Settings.ResetDatabase)

        val shell = fixture.store.state.value.shell
        assertTrue(resetCompleted)
        assertTrue(shell.downloads.downloads.isEmpty())
        assertEquals(0L, shell.downloads.downloadBytes)
        assertEquals(0L, shell.downloads.offlineDashboard.audioCacheBytes)
        assertEquals("0 B", shell.cache.downloadsDiagnostics.sections.single().rows[1].second)
    }

    @Test
    fun refreshLibraryUsesTheCoreCatalogTransactionInsteadOfTheNativeMaintenancePort() = runTest {
        var refreshed = false
        var maintenanceCalled = false
        val fixture = fixture(
            maintenance = {
                maintenanceCalled = true
                NaviampCoreMaintenanceResult("Unexpected")
            },
            refreshLibrary = { refreshed = true },
        )

        fixture.controller.execute(NaviampCoreCommand.Settings.RefreshLibrary)

        assertTrue(refreshed)
        assertFalse(maintenanceCalled)
        assertEquals("Library refreshed.", fixture.store.state.value.overlays.status)
    }

    private fun fixture(
        maintenance: suspend (NaviampCoreMaintenanceOperation) -> NaviampCoreMaintenanceResult = {
            NaviampCoreMaintenanceResult("Completed")
        },
        refreshLibrary: suspend () -> Unit = {},
        onDatabaseReset: suspend () -> Unit = {},
    ): SettingsFixture {
        val store = NaviampCoreStateStore()
        val savedInterface = mutableListOf<InterfaceSettings>()
        val savedPlayback = mutableListOf<PlaybackSettings>()
        val savedCache = mutableListOf<CacheSettings>()
        return SettingsFixture(
            store = store,
            savedInterface = savedInterface,
            savedPlayback = savedPlayback,
            savedCache = savedCache,
            controller = NaviampCoreSettingsController(
                stateStore = store,
                interfaceStore = NaviampCoreInterfaceSettingsStore(savedInterface::add),
                playbackSettings = NaviampCorePlaybackSettingsPort { settings, _ ->
                    settings.normalized().also(savedPlayback::add)
                },
                cacheSettings = NaviampCoreCacheSettingsPort { settings ->
                    settings.normalized().also(savedCache::add)
                },
                maintenancePort = NaviampCoreMaintenancePort(maintenance),
                refreshLibrary = refreshLibrary,
                onDatabaseReset = onDatabaseReset,
            ),
        )
    }
}

private data class SettingsFixture(
    val store: NaviampCoreStateStore,
    val savedInterface: List<InterfaceSettings>,
    val savedPlayback: List<PlaybackSettings>,
    val savedCache: List<CacheSettings>,
    val controller: NaviampCoreSettingsController,
)
