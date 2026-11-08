package app.naviamp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.smartplaylist.*
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistLibrarySyncUiTest {
    private fun draft() = SmartPlaylistDraft(conditions = listOf(SmartPlaylistConditionDraft(value = "Original")))
    private fun sample() = Track(
        id = TrackId("synced"), title = "Synced example", artistName = "Artist", albumTitle = "Album",
        durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
    )

    @Test
    fun syncIsSingleFlightAndRecomputesUsingRulesEditedWhileSyncing() = runComposeUiTest {
        val completion = CompletableDeferred<Unit>()
        var syncCalls = 0
        var synced = false
        var previewValue = ""
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft(),
                onSyncLibrary = { syncCalls++; completion.await(); synced = true },
                onPreview = {
                    previewValue = ((it.rules.single() as SmartPlaylistCondition).value as SmartPlaylistValue.Text).value
                    SmartPlaylistPreview(matchingTrackCount = if (synced) 1 else 0,
                        exampleTracks = if (synced) listOf(sample()) else emptyList())
                },
                onDismissRequest = {}, onSave = {},
            )
        }
        onNodeWithTag("smart-playlist-sync-library").performScrollTo().performClick()
        waitUntil { syncCalls == 1 }
        onNodeWithTag("smart-playlist-sync-library").assertIsNotEnabled().performClick()
        runOnIdle { assertEquals(1, syncCalls) }
        onNodeWithText("Original").performScrollTo().performTextReplacement("Changed")
        runOnIdle { completion.complete(Unit) }
        waitUntil(timeoutMillis = 5_000) { previewValue == "Changed" }
        onNodeWithTag("smart-playlist-example-synced").performScrollTo().assertExists()
        onNodeWithTag("smart-playlist-sync-library").assertIsEnabled()
        val output = File("build/reports/smart-playlist-library-sync.png")
        output.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()).use {
            output.writeBytes(requireNotNull(it.encodeToData()).bytes)
        }
    }

    @Test
    fun failedSyncShowsRetryAndDoesNotBlockSaving() = runComposeUiTest {
        var syncCalls = 0
        var previewCalls = 0
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft(),
                onSyncLibrary = { syncCalls++; if (syncCalls == 1) error("Private provider detail") },
                onPreview = { previewCalls++; SmartPlaylistPreview(matchingTrackCount = 0) },
                onDismissRequest = {}, onSave = {},
            )
        }
        onNodeWithTag("smart-playlist-sync-library").performScrollTo().performClick()
        waitUntil { syncCalls == 1 }
        onNodeWithText("Library sync failed. Try syncing again.").assertExists()
        onNodeWithText("Private provider detail").assertDoesNotExist()
        onNodeWithTag(SmartPlaylistSaveTestTag).assertIsEnabled()
        onNodeWithTag("smart-playlist-sync-library").performScrollTo().performClick()
        waitUntil { syncCalls == 2 }
        onNodeWithText("Library sync failed. Try syncing again.").assertDoesNotExist()
        waitUntil(timeoutMillis = 5_000) { previewCalls > 0 }
    }

    @Test
    fun closingBuilderCancelsItsSyncWithoutStartingAnotherPreview() = runComposeUiTest {
        val visible = androidx.compose.runtime.mutableStateOf(true)
        var started = false
        var cancelled = false
        val completion = CompletableDeferred<Unit>()
        setContent {
            if (visible.value) SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft(),
                onSyncLibrary = {
                    started = true
                    try { completion.await() } finally { cancelled = true }
                },
                onDismissRequest = {}, onSave = {},
            )
        }
        onNodeWithTag("smart-playlist-sync-library").performScrollTo().performClick()
        waitUntil { started }
        runOnIdle { visible.value = false }
        waitUntil { cancelled }
        runOnIdle { assertTrue(cancelled) }
    }
}
