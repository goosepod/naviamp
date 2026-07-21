package app.naviamp.presentation

import app.naviamp.app.NaviampCacheSettingsController
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.CoroutineScope
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
        var currentPlayback = PlaybackSettings()
        val playback = PlaybackSettingsMaintenanceController(
            playbackEngine = SettingsTestPlaybackEngine,
            playbackSettings = { currentPlayback },
            setPlaybackSettings = { currentPlayback = it },
            savePlaybackSettings = savedPlayback::add,
            reloadLyricsSidecars = {},
        )
        val cache = NaviampCacheSettingsController(
            setSettings = {},
            saveSettings = savedCache::add,
        )
        return SettingsFixture(
            store = store,
            savedInterface = savedInterface,
            savedPlayback = savedPlayback,
            savedCache = savedCache,
            controller = NaviampCoreSettingsController(
                stateStore = store,
                interfaceStore = NaviampCoreInterfaceSettingsStore(savedInterface::add),
                playbackController = playback,
                cacheController = cache,
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

private object SettingsTestPlaybackEngine : PlaybackEngine {
    override val name = "Settings test"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsReplayGain = false
    override val supportsGapless = true
    override val supportsCrossfade = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = false

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) = Unit

    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
}
