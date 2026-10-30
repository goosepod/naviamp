package app.naviamp.presentation

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampCacheSettingsUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreSettingsStorageTest {
    @Test
    fun settingsRefreshAudioAndDownloadsFromLiveStorageAndPreservePreferences() = runTest {
        var stats = StorageCacheStats(audioCount = 8, audioBytes = 247L * 1024 * 1024,
            downloadCount = 3, downloadBytes = 512L * 1024 * 1024)
        val location = NaviampStorageLocationUi("external", "External", "/external")
        val original = NaviampCacheSettingsUi(
            settings = CacheSettings(audioPrefetchDepth = 15),
            diagnostics = NaviampDiagnosticsUi(listOf(NaviampDiagnosticsSectionUi("Other", listOf("Fact" to "value")))),
            downloadLocations = listOf(location),
            audioCacheLocations = listOf(location),
            selectedDownloadLocationId = location.id,
            selectedAudioCacheLocationId = location.id,
            fileSelectionAvailable = true,
        )
        val core = NaviampCore.create(backgroundScope, fakeCoreServices().copy(
            diagnostics = NaviampCoreDiagnosticsPort { NaviampCoreDiagnosticsSnapshot(storage = stats) },
        ), initialState = NaviampCoreInitialState(product = NaviampCoreState(
            shell = NaviampAppShellUiState(cache = original),
        )))
        val refresh = backgroundScope.launch { core.maintainSettingsStorage() }
        try {
            runCurrent()
            assertStorage(core, "247 MB", "512 MB")
            assertEquals("8 (247.0 MB)", core.statsForNerdsDiagnostics().storageRows()["Audio cache"])
            assertEquals("3 (512.0 MB)", core.statsForNerdsDiagnostics().storageRows()["Downloads"])
            val refreshed = core.state.value.shell.cache
            assertEquals(original, refreshed.copy(audioCacheDiagnostics = original.audioCacheDiagnostics,
                downloadsDiagnostics = original.downloadsDiagnostics))
            assertEquals(stats.downloadBytes, core.state.value.shell.playback.downloadBytes)

            stats = stats.copy(audioBytes = 300L * 1024 * 1024, downloadBytes = 700L * 1024 * 1024)
            advanceTimeBy(2_000)
            runCurrent()
            assertStorage(core, "300 MB", "700 MB")

            stats = StorageCacheStats()
            advanceTimeBy(2_000)
            runCurrent()
            assertStorage(core, "0 B", "0 B")
            assertEquals(0L, core.state.value.shell.playback.downloadBytes)
        } finally {
            refresh.cancel()
            core.close()
        }
    }

    @Test
    fun unavailableAndFailedReadsRetainTotalsAndRefreshRecovers() = runTest {
        var snapshot: StorageCacheStats? = StorageCacheStats(audioBytes = 1024, downloadBytes = 2048)
        var fail = false
        var reads = 0
        val core = NaviampCore.create(backgroundScope, fakeCoreServices().copy(
            diagnostics = NaviampCoreDiagnosticsPort {
                reads++
                check(!fail)
                NaviampCoreDiagnosticsSnapshot(storage = snapshot)
            },
        ))
        val refresh = backgroundScope.launch { core.maintainSettingsStorage() }
        try {
            runCurrent()
            assertStorage(core, "1024 B", "2048 B")
            snapshot = null
            advanceTimeBy(2_000)
            runCurrent()
            assertStorage(core, "1024 B", "2048 B")
            fail = true
            advanceTimeBy(2_000)
            runCurrent()
            assertStorage(core, "1024 B", "2048 B")
            fail = false
            snapshot = StorageCacheStats(audioBytes = 4096, downloadBytes = 8192)
            advanceTimeBy(2_000)
            runCurrent()
            assertStorage(core, "4096 B", "8192 B")
            refresh.cancel()
            val stoppedAt = reads
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(stoppedAt, reads)
        } finally {
            refresh.cancel()
            core.close()
        }
    }
}

private fun assertStorage(core: NaviampCore, audio: String, downloads: String) {
    val cache = core.state.value.shell.cache
    assertEquals(audio, cache.audioCacheDiagnostics.storageRows()["Audio cache"])
    assertEquals(audio, cache.downloadsDiagnostics.storageRows()["Audio cache"])
    assertEquals(downloads, cache.downloadsDiagnostics.storageRows()["Downloads"])
}

private fun NaviampDiagnosticsUi.storageRows() = sections.single { it.title == "Storage" }.rows.toMap()
