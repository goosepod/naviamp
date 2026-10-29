package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampMediaAccessibilityTest {
    @Test fun loadedArtworkAndFavoriteStateRetainTheMediaRowsAccessibleIdentity() {
        val bitmap = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 16) bitmap.setRGB(x, y, Color.Magenta.toArgb())
        val bytes = ByteArrayOutputStream().also { ImageIO.write(bitmap, "png", it) }.toByteArray()
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            runComposeUiTest {
                val favorite = mutableStateOf(false)
                setContent {
                    SharedMediaRow(
                        item = SharedMediaItemUi("artist", "Named artist", "Artist", meta = "2026",
                            coverArtUrl = "test://accessible-portrait", favoriteActive = favorite.value),
                        colors = NaviampColors.Dark, onClick = {}, mediaKind = SharedMediaItemKind.Artist,
                        modifier = Modifier.testTag("media-row"),
                    )
                }
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("media-row").captureToImage().toPixelMap()[30, 29].toArgb() == Color.Magenta.toArgb()
                }
                for (active in listOf(false, true)) {
                    favorite.value = active
                    waitForIdle()
                    onNodeWithTag("media-row").assert(SemanticsMatcher("Retains title and metadata for native accessibility") {
                        (if (SemanticsProperties.ContentDescription in it.config)
                            it.config[SemanticsProperties.ContentDescription] else emptyList())
                            .any { label -> label.contains("Named artist") && label.contains("Artist") && label.contains("2026") }
                    })
                }
            }
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }
}
