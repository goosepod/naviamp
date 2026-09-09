package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampDesktopFeedbackUiTest {
    @Test fun playlistCreationFitsShortWindow() = checkPlaylistCreation(640, 360)
    @Test fun playlistCreationFitsNarrowWindow() = checkPlaylistCreation(360, 480)

    private fun checkPlaylistCreation(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val editor = mutableStateOf(NaviampTrackPlaylistMembershipUi("song", "Underwater", rows = List(70) {
            NaviampPlaylistMembershipRowUi(NaviampPlaylistChoiceUi("$it", "Playlist $it"), false, false)
        }))
        var requestedName: String? = null
        setContent {
            TrackPlaylistMembershipDialog(editor.value, NaviampColors(), onToggle = { id ->
                editor.value = editor.value.copy(rows = editor.value.rows.map {
                    if (it.playlist.id == id) it.copy(selected = !it.selected) else it
                })
            }, onApply = {}, onCreate = { name ->
                requestedName = name
                editor.value = editor.value.copy(saving = true, creationFailed = false)
            }, onDismissRequest = { error("Cancelling creation must preserve the membership editor") })
        }
        onNodeWithText("Playlist 0").performClick()
        onNodeWithText("New playlist").assertIsDisplayed().performClick()
        onNodeWithText("Playlist name").assertIsDisplayed().assertIsFocused().performTextInput("My new playlist")
        onNodeWithText("Create playlist and add song").assertIsDisplayed().assertIsEnabled()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Playlist 0").assertIsOn()
        onNodeWithText("New playlist").performClick()
        onNodeWithText("Playlist name").assertTextContains("My new playlist")
        onNodeWithText("Create playlist and add song").performClick()
        onNodeWithText("Cancel").assertIsNotEnabled()
        runOnIdle {
            assertEquals("My new playlist", requestedName)
            editor.value = editor.value.copy(saving = false, creationFailed = true)
        }
        onNodeWithText("Could not create the playlist. Try again.").assertIsDisplayed()
        onNodeWithText("Playlist name").assertTextContains("My new playlist")
        onNodeWithText("Create playlist and add song").performClick()
        runOnIdle {
            editor.value = editor.value.copy(saving = false, creationFailed = false, saved = true,
                rows = editor.value.rows + NaviampPlaylistMembershipRowUi(
                    NaviampPlaylistChoiceUi("new", "My new playlist"), true, true))
        }
        onNodeWithText("Add to playlists").assertIsDisplayed()
        onNodeWithText("My new playlist").assertIsDisplayed().assertIsOn()
        runOnIdle { assertTrue(editor.value.rows.first { it.playlist.id == "0" }.selected) }
        onNodeWithText("Save changes").assertIsEnabled()
    }

    @Test
    fun smartPlaylistMembershipIsVisibleCheckedAndReadOnly() = runComposeUiTest {
        setContent {
            TrackPlaylistMembershipDialog(
                NaviampTrackPlaylistMembershipUi("underwater", "Underwater", rows = listOf(
                    NaviampPlaylistMembershipRowUi(NaviampPlaylistChoiceUi("favorites", "Favorites"),
                        selected = true, originallySelected = true, ruleBased = true),
                )), NaviampColors(), onToggle = { error("Smart playlist cannot be edited") },
                onApply = {}, onDismissRequest = {},
            )
        }
        onNodeWithText("Favorites").assertIsDisplayed().assertIsOn().assertIsNotEnabled()
        onNodeWithText("Smart playlist: membership is controlled by its rules.").assertIsDisplayed()
        onNodeWithText("Save changes").assertIsNotEnabled()
    }

    @Test
    fun artistFavoriteReflectsTheUpdatedDetailState() = runComposeUiTest {
        val artist = mutableStateOf(SharedMediaItemUi("artist", "Artist", "", canFavorite = true))
        setContent {
            Box(Modifier.width(600.dp).height(640.dp)) {
                NaviampArtistDetailContent(
                    NaviampColors(), NaviampArtistDetailScreenUi(detail = SharedArtistDetailUi(artist.value, emptyList())),
                    AlbumCollectionLayout.List, AlbumSortOrder.Title, true,
                    NaviampArtistDetailActions({}, {
                        if (it.command == NaviampArtistDetailCommand.ToggleFavorite)
                            artist.value = artist.value.copy(favoriteActive = !artist.value.favoriteActive)
                    }, {}, {}),
                )
            }
        }
        onNodeWithContentDescription("Favorite artist").assertIsNotSelected().performClick()
        val favorite = onNodeWithContentDescription("Remove artist favorite").assertIsSelected()
        val pixels = favorite.captureToImage().toPixelMap()
        assertTrue((0 until pixels.width).any { x -> (0 until pixels.height).any { y ->
            val color = pixels[x, y]
            color.red > 0.8f && color.green < 0.5f && color.blue < 0.5f
        } }, "The selected heart should have a red fill")
        favorite.performClick()
        onNodeWithContentDescription("Favorite artist").assertIsNotSelected()
    }

    @Test
    fun membershipExplainsSelectionAndKeepsCreationSecondary() = runComposeUiTest {
        val editor = mutableStateOf(NaviampTrackPlaylistMembershipUi("song", "A song", rows = listOf(
            NaviampPlaylistMembershipRowUi(NaviampPlaylistChoiceUi("two", "Playlist two"), false, false),
            NaviampPlaylistMembershipRowUi(NaviampPlaylistChoiceUi("one", "Playlist one"), true, true),
        )))
        var saves = 0
        setContent {
            TrackPlaylistMembershipDialog(editor.value, NaviampColors(), onToggle = { id ->
                editor.value = editor.value.copy(rows = editor.value.rows.map {
                    if (it.playlist.id == id) it.copy(selected = !it.selected) else it
                })
            }, onApply = {
                saves++
                editor.value = editor.value.copy(saved = true, rows = editor.value.rows.map {
                    it.copy(originallySelected = it.selected)
                })
            }, onDismissRequest = {})
        }
        onNodeWithText("Add to playlists").assertIsDisplayed()
        onNodeWithText("Create playlist and add song").assertDoesNotExist()
        onNodeWithText("Save changes").assertIsNotEnabled()
        val first = onNodeWithText("Playlist one").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Playlist two").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < second.top, "Existing membership must appear first")
        assertTrue(second.top - first.top <= 44f, "Playlist rows should be compact")
        onNodeWithText("Playlist two").performClick()
        assertEquals(first.top, onNodeWithText("Playlist one").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithText("Save changes").assertIsEnabled().performClick()
        onNodeWithText("Done").assertIsDisplayed()
        runOnIdle { assertEquals(1, saves) }
        onNodeWithText("New playlist").performClick()
        onNodeWithText("Create playlist and add song").assertIsDisplayed()
    }
}
