package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.settings.AlbumArtworkPreference
import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampAlbumArtworkPreferenceUiTest {
    @Test
    fun albumDialogOffersExplicitOverrideAndInheritance() = runDesktopComposeUiTest(1200, 750) {
        val saved = mutableListOf<AlbumArtworkPreference>()
        val selected = mutableStateOf(AlbumArtworkPreference.Inherit)
        val album = SharedMediaItemUi(id = "edition-1", title = "Album", subtitle = "Artist")
        setContent {
            val colors = NaviampColors.Dark
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampAlbumDetailContent(
                    colors = colors,
                    screen = NaviampAlbumDetailScreenUi(
                        selectedAlbum = album,
                        detail = SharedAlbumDetailUi(
                            album = album,
                            tracks = listOf(SharedTrackRowUi(id = "track-1", title = "Song", subtitle = "Artist")),
                        ),
                    ),
                    albumArtworkPreference = selected.value,
                    actions = NaviampAlbumDetailActions(
                        onBack = {},
                        onAlbumAction = { request ->
                            (request.command as? NaviampAlbumDetailCommand.SaveArtworkPreference)?.let {
                                saved += it.preference
                                selected.value = it.preference
                            }
                        },
                        onTrackAction = {},
                    ),
                )
            }
        }

        onNodeWithContentDescription("Album artwork preference").assertDoesNotExist()
        onNodeWithContentDescription("Playback profile").performClick()
        onNodeWithText("Track cover").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(listOf(AlbumArtworkPreference.Track), saved)

        onNodeWithContentDescription("Playback profile").performClick()
        onNodeWithText("Use global settings").performClick()
        assertEquals(listOf(AlbumArtworkPreference.Track, AlbumArtworkPreference.Inherit), saved)
    }
}
