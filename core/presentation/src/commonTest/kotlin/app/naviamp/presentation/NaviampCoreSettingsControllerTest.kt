package app.naviamp.presentation

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.normalized
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

    private fun fixture(
        maintenance: suspend (NaviampCoreMaintenanceOperation) -> NaviampCoreMaintenanceResult = {
            NaviampCoreMaintenanceResult("Completed")
        },
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
