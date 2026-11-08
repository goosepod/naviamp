package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.smartplaylist.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistResultsUiTest {
    @Test fun ownedEditorKeepsItsBoundsWhileScrollingCompactExamplesAndReturningToFields() = runComposeUiTest {
        val tracks = (1..50).map { index -> Track(
            id = TrackId("$index"), title = "Track $index", artistName = "Artist", albumTitle = "Album",
            durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
        ) }
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true) {
                Box(Modifier.size(1180.dp, 790.dp)) {
                    SmartPlaylistBuilderDialog(NaviampColors(), SmartPlaylistDraft(name = "My mix",
                        conditions = listOf(SmartPlaylistConditionDraft(value = "Artist"))),
                        onPreview = { SmartPlaylistPreview(matchingTrackCount = 50,
                            resultTrackCount = 50, exampleTracks = tracks, indexedTrackCount = 50) },
                        onDismissRequest = {}, onSave = {})
                }
            }
        }
        mainClock.advanceTimeBy(600)
        waitForIdle()
        val dialog = onNode(isDialog())
        val initial = dialog.fetchSemanticsNode().boundsInRoot
        val nameBounds = onNodeWithText("My mix").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("smart-playlist-examples").performScrollTo()
        val list = onNodeWithTag("smart-playlist-examples")
        assertTrue(list.fetchSemanticsNode().boundsInRoot.height <= 221f * density.density)
        onNodeWithText("Track 1 - Artist - Album").assertExists()
        list.performScrollToIndex(49)
        onNodeWithText("Track 50 - Artist - Album").assertIsDisplayed()
        assertEquals(initial, dialog.fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("smart-playlist-editor-scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, -100_000f)
        }
        waitForIdle()
        assertEquals(nameBounds, onNodeWithText("My mix").fetchSemanticsNode().boundsInRoot)
        assertEquals(initial, dialog.fetchSemanticsNode().boundsInRoot)
    }
}
