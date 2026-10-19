package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.asSkiaBitmap
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.smartplaylist.*
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistValidationUiTest {
    private fun invalidRating() = SmartPlaylistConditionDraft(
        field = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Rating },
        operator = SmartPlaylistOperator.Is,
        value = "four",
    )

    private fun track(id: String) = Track(
        id = TrackId(id), title = "Example $id", artistName = "Sample Artist", albumTitle = "Album",
        durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
    )

    @Test
    fun incompleteRuleBlocksSaveAndPreviewAndCorrectionRestoresSamples() = runComposeUiTest {
        val invalid = invalidRating()
        val draft = mutableStateOf(SmartPlaylistDraft(conditions = listOf(
            SmartPlaylistConditionDraft(value = "Sample Artist"), invalid,
        )))
        var requests = 0
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft.value,
                onPreview = {
                    requests++
                    SmartPlaylistPreview(matchingTrackCount = 2, resultTrackCount = 2,
                        exampleTracks = listOf(track("one"), track("two")))
                },
                onDismissRequest = {}, onSave = {},
            )
        }
        onNodeWithTag(SmartPlaylistSaveTestTag).assertIsNotEnabled()
        onNodeWithTag("smart-playlist-rule-error-${invalid.id}").assertExists()
        mainClock.advanceTimeBy(500)
        runOnIdle { assertEquals(0, requests) }
        onNodeWithText("four").performScrollTo().performTextReplacement("4")
        waitUntil(timeoutMillis = 5_000) { requests == 1 }
        onNodeWithTag(SmartPlaylistSaveTestTag).assertIsEnabled()
        onNodeWithTag("smart-playlist-rule-error-${invalid.id}").assertDoesNotExist()
        onNodeWithTag("smart-playlist-example-one").assertExists()
        onNodeWithTag("smart-playlist-example-two").performScrollTo()
        val output = File("build/reports/smart-playlist-preview.png")
        output.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()).use {
            output.writeBytes(requireNotNull(it.encodeToData()).bytes)
        }
    }

    @Test
    fun emptyAndUnsupportedPreviewsAreDistinct() = runComposeUiTest {
        val draft = mutableStateOf(SmartPlaylistDraft(conditions = listOf(SmartPlaylistConditionDraft(value = "Empty"))))
        var requests = 0
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft.value,
                onPreview = {
                    requests++
                    if (requests == 1) SmartPlaylistPreview(matchingTrackCount = 0, resultTrackCount = 0)
                    else SmartPlaylistPreview(unsupportedFields = listOf("lyrics"))
                },
                onDismissRequest = {}, onSave = {},
            )
        }
        waitUntil(timeoutMillis = 5_000) { requests == 1 }
        onNodeWithText("No example matches in the synced library.").assertExists()
        runOnIdle { draft.value = draft.value.copy(conditions = listOf(SmartPlaylistConditionDraft(value = "Unsupported"))) }
        waitUntil(timeoutMillis = 5_000) { requests == 2 }
        onNodeWithText("This preview needs information that is not in the local cache: Lyrics. The saved playlist still runs on the server.").assertExists()
        onNodeWithText("No example matches in the synced library.").assertDoesNotExist()
    }

    @Test fun missingLibraryMetadataExplainsSyncAndSyncRestoresExamples() = runComposeUiTest {
        var synced = false
        val initial = SmartPlaylistDefinition("Favorites", rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
        ), limit = 500, libraryIds = listOf("2"))
        setContent {
            SmartPlaylistBuilderDialog(NaviampColors(), SmartPlaylistDraft.fromDefinition(initial),
                availableLibraries = listOf(app.naviamp.domain.settings.ConnectionFormMusicFolder("2", "Music")),
                onPreview = {
                    if (synced) SmartPlaylistPreview(matchingTrackCount = 1, resultTrackCount = 1,
                        exampleTracks = listOf(track("favorite")), indexedTrackCount = 24943)
                    else SmartPlaylistPreview(unsupportedFields = listOf(SmartPlaylistFields.LibraryId), indexedTrackCount = 24943)
                }, onSyncLibrary = { synced = true }, onDismissRequest = {}, onSave = {})
        }
        val explanation = "The local cache is missing library membership for some tracks"
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasText(explanation, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(explanation, substring = true).performScrollTo().assertExists()
        onNodeWithText("library_id", substring = true).assertDoesNotExist()
        val output = File("build/reports/smart-playlist-library-preview.png")
        output.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()).use {
            output.writeBytes(requireNotNull(it.encodeToData()).bytes)
        }
        onNodeWithTag("smart-playlist-sync-library").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasTestTag("smart-playlist-example-favorite")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("smart-playlist-example-favorite").performScrollTo().assertExists()
        onNodeWithText(explanation, substring = true).assertDoesNotExist()
    }

    @Test
    fun cancelledOlderRequestCannotReplaceNewExamples() = runComposeUiTest {
        val draft = mutableStateOf(SmartPlaylistDraft(conditions = listOf(SmartPlaylistConditionDraft(value = "Old"))))
        var oldRequest: Continuation<SmartPlaylistPreview>? = null
        var newRequested = false
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(), initialDraft = draft.value,
                onPreview = { definition ->
                    if ((definition.rules.single() as SmartPlaylistCondition).value == SmartPlaylistValue.Text("Old")) {
                        suspendCoroutine { oldRequest = it }
                    } else {
                        newRequested = true
                        SmartPlaylistPreview(matchingTrackCount = 1, exampleTracks = listOf(track("new")))
                    }
                },
                onDismissRequest = {}, onSave = {},
            )
        }
        waitUntil(timeoutMillis = 5_000) { oldRequest != null }
        runOnIdle { draft.value = draft.value.copy(conditions = listOf(SmartPlaylistConditionDraft(value = "New"))) }
        waitUntil(timeoutMillis = 5_000) { newRequested }
        onNodeWithTag("smart-playlist-example-new").assertExists()
        runOnIdle { oldRequest!!.resume(SmartPlaylistPreview(matchingTrackCount = 1, exampleTracks = listOf(track("old")))) }
        waitForIdle()
        onNodeWithTag("smart-playlist-example-new").assertExists()
        onNodeWithTag("smart-playlist-example-old").assertDoesNotExist()
    }
}
