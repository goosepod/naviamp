package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.settings.AlbumArtworkPreference
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampAlbumArtworkPreferenceUiTest {
    @Test
    fun albumDialogOffersExplicitOverrideAndInheritance() = runDesktopComposeUiTest(1200, 750) {
        val saved = mutableListOf<AlbumArtworkPreference>()
        val album = SharedMediaItemUi(id = "edition-1", title = "Album", subtitle = "Artist")
        setContent {
            val colors = NaviampColors.Dark
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampAlbumDetailContent(
                    colors = colors,
                    screen = NaviampAlbumDetailScreenUi(
                        selectedAlbum = album,
                        detail = SharedAlbumDetailUi(album = album, tracks = emptyList()),
                    ),
                    actions = NaviampAlbumDetailActions(
                        onBack = {},
                        onAlbumAction = { request ->
                            (request.command as? NaviampAlbumDetailCommand.SaveArtworkPreference)
                                ?.let { saved += it.preference }
                        },
                        onTrackAction = {},
                    ),
                )
            }
        }

        onNodeWithContentDescription("Album artwork preference").performClick()
        onNodeWithText("Track cover").performClick()
        assertEquals(listOf(AlbumArtworkPreference.Track), saved)

        onNodeWithContentDescription("Album artwork preference").performClick()
        onNodeWithText("Use global preference").performClick()
        assertEquals(listOf(AlbumArtworkPreference.Track, AlbumArtworkPreference.Inherit), saved)
    }
}
