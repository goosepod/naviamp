package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
    @Test fun homeCardsPreserveAllFourArtworkCornersAcrossTheSquareToggle() = runComposeUiTest {
        val square = mutableStateOf(false)
        val bytes = requireNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=A&from=0044CC&to=001133"))
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            setContent {
                CompositionLocalProvider(LocalSquareAlbumArtwork provides square.value) {
                    Row {
                        for ((tag, kind, url) in listOf(
                            Triple("home-album", SharedMediaItemKind.Album, "test://home"),
                            Triple("home-placeholder", SharedMediaItemKind.Album, null),
                            Triple("home-artist", SharedMediaItemKind.Artist, "test://home"),
                        )) {
                            Box(Modifier.background(Color.Magenta).testTag(tag)) {
                                HomeCollectionGridCard(
                                    SharedHomeCollectionItemUi(
                                        SharedMediaItemUi(tag, "Title", "Subtitle", coverArtUrl = url), kind,
                                        action = SharedHomeCollectionItemAction.OpenAlbum),
                                    NaviampColors.Dark, 80.dp, {},
                                )
                            }
                        }
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("cover-art-image", useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
            fun corners(tag: String): List<Color> {
                val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
                val end = pixels.width - 2
                return listOf(pixels[1, 1], pixels[end, 1], pixels[1, end], pixels[end, end])
            }
            for (tag in listOf("home-album", "home-placeholder", "home-artist")) {
                assertTrue(corners(tag).all { it == Color.Magenta }, "Rounded: $tag")
            }
            runOnIdle { square.value = true }
            for (tag in listOf("home-album", "home-placeholder")) {
                assertTrue(corners(tag).none { it == Color.Magenta }, "Square: $tag")
            }
            assertTrue(corners("home-artist").all { it == Color.Magenta })
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }

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
