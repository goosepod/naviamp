package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampArtworkSettingsUiTest {
    @Test fun togglingSquareCornersClipsImagesAndPlaceholdersWithoutReloadingOrSquaringArtists() = runComposeUiTest {
        val square = mutableStateOf(false)
        val loads = AtomicInteger()
        val bytes = requireNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=A&from=0044CC&to=001133"))
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { loads.incrementAndGet(); bytes }
        try {
            setContent {
                CompositionLocalProvider(LocalSquareAlbumArtwork provides square.value) {
                    Row(Modifier.background(Color.Magenta)) {
                        NaviampCoverArt("test://art", NaviampColors.Dark, 80.dp, 16.dp,
                            Modifier.testTag("album"), mediaKind = SharedMediaItemKind.Album)
                        NaviampCoverArt(null, NaviampColors.Dark, 80.dp, 16.dp,
                            Modifier.testTag("placeholder"), mediaKind = SharedMediaItemKind.Album)
                        NaviampCoverArt("test://art", NaviampColors.Dark, 80.dp, 16.dp,
                            Modifier.testTag("artist"), mediaKind = SharedMediaItemKind.Artist)
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("cover-art-image", useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
            fun corner(tag: String) = onNodeWithTag(tag).captureToImage().toPixelMap()[1, 1]
            assertEquals(Color.Magenta, corner("album"))
            assertEquals(Color.Magenta, corner("placeholder"))
            val before = loads.get()
            runOnIdle { square.value = true }
            assertNotEquals(Color.Magenta, corner("album"))
            assertNotEquals(Color.Magenta, corner("placeholder"))
            assertEquals(Color.Magenta, corner("artist"))
            assertEquals(before, loads.get())
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }
}
