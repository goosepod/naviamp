package app.naviamp.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.ui.NaviampAudioCacheSettingsSection
import app.naviamp.ui.NaviampColors
import app.naviamp.ui.NaviampDownloadsSettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCoreSettingsStoragePresentationTest {
    @Test
    fun bothSettingsPagesRenderLiveTotalsAndRefreshStopsWhenSettingsCloses() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var reads = 0
        var stats = StorageCacheStats(audioBytes = 247L * 1024 * 1024, downloadBytes = 613L * 1024 * 1024)
        val core = NaviampCore.create(scope, fakeCoreServices().copy(
            diagnostics = NaviampCoreDiagnosticsPort {
                reads++
                NaviampCoreDiagnosticsSnapshot(storage = stats)
            },
        ))
        val visible = mutableStateOf(false)
        val audioPage = mutableStateOf(true)
        try {
            setContent {
                NaviampCoreSettingsStorageRefresh(core, visible.value)
                if (visible.value) {
                    val shell = core.state.collectAsState().value.shell
                    Column {
                        if (audioPage.value) {
                            NaviampAudioCacheSettingsSection(
                                colors = NaviampColors(), cacheSettings = shell.cache.settings,
                                diagnostics = shell.cache.audioCacheDiagnostics,
                                onCacheSettingsChanged = {}, onLocationChanged = {},
                            )
                        } else {
                            NaviampDownloadsSettingsSection(
                                colors = NaviampColors(), playbackSettings = shell.playback.settings,
                                cacheSettings = shell.cache.settings, diagnostics = shell.cache.downloadsDiagnostics,
                                showMobileNetworkQuality = false, downloadBytes = shell.playback.downloadBytes,
                                onPlaybackSettingsChanged = {}, onPlaybackSettingsChangedAndRedownload = {},
                                onCacheSettingsChanged = {}, onLocationChanged = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(0, reads)
            runOnIdle { visible.value = true }
            waitForIdle()
            onNodeWithText("247 MB").assertIsDisplayed()
            runOnIdle { audioPage.value = false }
            waitForIdle()
            onNodeWithText("613 MB").assertIsDisplayed()

            runOnIdle { stats = stats.copy(downloadBytes = 700L * 1024 * 1024) }
            waitUntil(timeoutMillis = 5_000) { core.state.value.shell.playback.downloadBytes == stats.downloadBytes }
            waitForIdle()
            onNodeWithText("700 MB").assertIsDisplayed()

            runOnIdle { visible.value = false }
            waitForIdle()
            val stoppedAt = reads
            Thread.sleep(2_100)
            waitForIdle()
            assertEquals(stoppedAt, reads)

            runOnIdle {
                stats = StorageCacheStats()
                visible.value = true
            }
            waitForIdle()
            onNodeWithText("0 B").assertIsDisplayed()
        } finally {
            core.close()
            scope.cancel()
        }
    }
}
