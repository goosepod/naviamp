package app.naviamp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampFilledArtworkLayoutTest {
    @Test fun filledArtworkUsesTheWholeWindowWhileControlsAvoidSystemBars() = runDesktopComposeUiTest(412, 818) {
        setContent {
            NaviampNowPlayingPanel(
                NowPlayingUi(id = "track", title = "Full window", subtitle = "Artist",
                    stateLabel = "Paused", isPaused = true, canPlayPause = true),
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                displaySettings = NowPlayingDisplaySettings(fillArtworkArea = true),
                contentInsets = WindowInsets(top = 24, bottom = 32),
            )
        }
        val window = onRoot().fetchSemanticsNode().boundsInRoot
        val art = onNodeWithTag("now-playing-filled-artwork").fetchSemanticsNode().boundsInRoot
        assertEquals(window, art)
        val controls = onNodeWithTag("now-playing-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.bottom <= window.bottom - 32)
        assertTrue(controls.top >= window.top + 24)
    }

    @Test fun televisionKeepsItsExistingPresentationWhenFillIsEnabled() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            TelevisionNowPlaying(
                NowPlayingUi(id = "track", title = "Artwork review", subtitle = "Artist",
                    stateLabel = "Playing", isPlaying = true, canPlayPause = true),
                null, NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                onClose = {}, onOpenSettings = {},
                displaySettings = NowPlayingDisplaySettings(fillArtworkArea = true),
            )
        }
        mainClock.advanceTimeBy(200)
        onNodeWithTag("now-playing-filled-artwork").assertDoesNotExist()
        onNodeWithContentDescription("Pause").assertIsFocused()
        onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test fun narrowFilledMetadataBalancesAudioAroundRatingsAndFallsBackForLargeText() {
        data class Case(val width: Int, val height: Int, val scale: Float, val audio: String,
            val rating: Boolean = true, val showAudio: Boolean = true, val split: Boolean = false)
        val cases = listOf(
            Case(320, 704, 1f, "MP3 320 kbps", split = true),
            Case(320, 704, 1.4f, "MP3 320 kbps", split = true),
            Case(320, 704, 2f, "FLAC 24-bit / 192 kHz"),
            Case(280, 500, 1f, "MP3 320 kbps", split = true),
            Case(412, 818, 1f, "MP3 320 kbps", split = true),
            Case(320, 704, 1f, "MP3 320 kbps", rating = false),
            Case(320, 704, 1f, "MP3 320 kbps", showAudio = false),
            Case(320, 704, 1f, "", rating = true),
        )
        for (case in cases) runDesktopComposeUiTest(width = case.width, height = case.height) {
            val trackActions = mutableListOf<NowPlayingCurrentTrackUiActionRequest>()
            val progress = MutableStateFlow(PlaybackProgress(2.0, 163.0))
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, case.scale)) {
                    NaviampNowPlayingPanel(
                        NowPlayingUi(id = "track", title = "Dip It Low", subtitle = "Ofenbach  •  Fabich",
                            albumTitle = "Dip It Low", albumYear = 2022, stateLabel = "Paused",
                            positionSeconds = 2.0, durationSeconds = 163.0, isPaused = true,
                            waveform = app.naviamp.domain.waveform.AudioWaveform(List(80) { index ->
                                (0.2f + kotlin.math.abs(kotlin.math.sin(index * 0.71f)) * 0.7f) }),
                            canFavorite = case.rating, canRate = case.rating, audioInfo = case.audio),
                        progress, NaviampColors.Dark,
                        NaviampNowPlayingActions({}, {}, { trackActions += it }, {}, {}, {}, {}),
                        displaySettings = NowPlayingDisplaySettings(fillArtworkArea = true, showAudioInfo = case.showAudio),
                    )
                }
            }
            val metadata = onNodeWithTag("now-playing-filled-metadata").fetchSemanticsNode().boundsInRoot
            val transport = onNodeWithTag("now-playing-transport-row")
            transport.assertIsDisplayed()
            val density = onRoot().fetchSemanticsNode().layoutInfo.density.density
            assertTrue(transport.fetchSemanticsNode().boundsInRoot.top - metadata.bottom >= 11.5f * density)
            onNodeWithTag("now-playing-bottom-actions").assertIsDisplayed()
            val durationLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            onNodeWithText("2:43").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                it(durationLayouts)
            }
            assertEquals(1, durationLayouts.single().lineCount, "Time labels must not wrap with larger text")
            if (case.split) {
                val format = onNodeWithText("MP3").fetchSemanticsNode().boundsInRoot
                val quality = onNodeWithText("320 kbps").fetchSemanticsNode().boundsInRoot
                val rating = onNodeWithTag("now-playing-filled-rating").fetchSemanticsNode().boundsInRoot
                assertTrue(format.right <= rating.left && quality.left >= rating.right)
                assertTrue(kotlin.math.abs(format.center.y - rating.center.y) < density)
                assertTrue(kotlin.math.abs(quality.center.y - rating.center.y) < density)
                assertTrue(kotlin.math.abs(rating.center.x - metadata.center.x) < density, "Rating bar stays centered")
            } else if (case.rating && case.showAudio && case.audio.isNotBlank()) {
                val audio = onNodeWithText(case.audio).fetchSemanticsNode().boundsInRoot
                val rating = onNodeWithTag("now-playing-filled-rating").fetchSemanticsNode().boundsInRoot
                assertTrue(audio.top - rating.bottom >= 7.5f * density, "Large text uses a readable second line")
            }
            if (case.rating) {
                onNodeWithContentDescription("Favorite").performClick()
                onNodeWithContentDescription("5 star rating").performClick()
                assertTrue(trackActions.any { it.action == NowPlayingCurrentTrackAction.ToggleFavorite })
                assertTrue(trackActions.any { it.action == NowPlayingCurrentTrackAction.SetRating && it.rating == 5 })
            }
            System.getenv("NAVIAMP_ARTWORK_CAPTURE")?.let { directory ->
                File(directory).mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory,
                    "spacing-${case.width}-${case.height}-${case.scale}-${case.rating}-${case.showAudio}-${case.audio.isBlank()}.png"))
            }
        }
    }

    @Test fun singlePaneFillAndLegacyWideLayoutsKeepControlsUsableAcrossPlaybackUpdates() {
        val cases = listOf(
            Triple(320, 720, NaviampPlayerPanelLayout.Adaptive),
            Triple(412, 818, NaviampPlayerPanelLayout.Adaptive),
            Triple(400, 760, NaviampPlayerPanelLayout.Standalone),
            Triple(876, 728, NaviampPlayerPanelLayout.Adaptive),
            Triple(640, 420, NaviampPlayerPanelLayout.Adaptive),
            Triple(1200, 760, NaviampPlayerPanelLayout.Adaptive),
            Triple(1200, 760, NaviampPlayerPanelLayout.Full),
        )
        val bytes = requireNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=ART&from=CC5511&to=1133AA"))
        for ((width, height, layout) in cases) {
            resetNaviampCoverArtCache()
            val loads = AtomicInteger()
            setJvmPlatformCoverArtByteLoader { loads.incrementAndGet(); bytes }
            try {
                runDesktopComposeUiTest(width = width, height = height) {
                    val progress = MutableStateFlow(PlaybackProgress(8.0, 180.0))
                    val actions = mutableListOf<NowPlayingPlaybackActionRequest>()
                    setContent {
                        CompositionLocalProvider(LocalSquareAlbumArtwork provides true,
                            LocalDensity provides Density(LocalDensity.current.density, if (width == 320) 1.4f else 1f)) {
                            NaviampNowPlayingPanel(
                                NowPlayingUi(id = "track", title = "Artwork review", subtitle = "Artist",
                                    albumTitle = "Album", coverArtUrl = "test://fill", stateLabel = "Paused",
                                    positionSeconds = 8.0, durationSeconds = 180.0,
                                    isPaused = true, canPlayPause = true, canSeek = true,
                                    canFavorite = true, canRate = true, audioInfo = "MP3 320 kbps"),
                                progress, NaviampColors.Dark,
                                NaviampNowPlayingActions({ actions += it }, {}, {}, {}, {}, {}, {}),
                                displaySettings = NowPlayingDisplaySettings(fillArtworkArea = true),
                                panelLayout = layout,
                            )
                        }
                    }
                    val shouldFill = width < 780 && layout == NaviampPlayerPanelLayout.Adaptive
                    if (shouldFill) onNodeWithTag("now-playing-filled-artwork").assertIsDisplayed()
                    else onNodeWithTag("now-playing-filled-artwork").assertDoesNotExist()
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithTag("cover-art-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                    }
                    val play = onNodeWithContentDescription("Play")
                    if (shouldFill) play.performScrollTo()
                    play.assertIsDisplayed().performClick()
                    assertTrue(actions.any { it.action == NowPlayingPlaybackAction.Resume })
                    if (shouldFill) {
                        val density = onRoot().fetchSemanticsNode().layoutInfo.density.density
                        val title = onNodeWithText("Artwork review").fetchSemanticsNode().boundsInRoot
                        val artist = onNodeWithText("Artist").fetchSemanticsNode().boundsInRoot
                        val album = onNodeWithText("Album").fetchSemanticsNode().boundsInRoot
                        val audio = onNodeWithTag("now-playing-filled-metadata").fetchSemanticsNode().boundsInRoot
                        val transport = onNodeWithTag("now-playing-transport-row").fetchSemanticsNode().boundsInRoot
                        assertTrue(artist.top - title.bottom >= 5.5f * density, "Title/artist spacing")
                        assertTrue(album.top - artist.bottom >= 5.5f * density, "Artist/album spacing")
                        assertTrue(transport.top - audio.bottom >= (if (height < 500) 7.5f else 11.5f) * density, "Metadata/transport spacing")
                        onNodeWithTag("now-playing-bottom-actions").assertIsDisplayed()
                    }
                    val before = loads.get()
                    repeat(60) { tick -> runOnIdle { progress.value = PlaybackProgress(8.0 + tick / 10.0, 180.0) } }
                    waitForIdle()
                    assertEquals(before, loads.get(), "Progress must not reload the filled background")
                    System.getenv("NAVIAMP_ARTWORK_CAPTURE")?.let { directory ->
                        File(directory).mkdirs()
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "filled-$width-$height-$layout.png"))
                    }
                }
            } finally {
                resetJvmPlatformCoverArtByteLoader()
                resetNaviampCoverArtCache()
            }
        }
    }
}
