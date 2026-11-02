package app.naviamp.ui

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
                        val audio = onNodeWithText("MP3 320 kbps").fetchSemanticsNode().boundsInRoot
                        val transport = onNodeWithTag("now-playing-transport-row").fetchSemanticsNode().boundsInRoot
                        assertTrue(artist.top - title.bottom >= 3.5f * density, "Title/artist spacing")
                        assertTrue(album.top - artist.bottom >= 3.5f * density, "Artist/album spacing")
                        assertTrue(transport.top - audio.bottom >= 5.5f * density, "Audio/transport spacing")
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
