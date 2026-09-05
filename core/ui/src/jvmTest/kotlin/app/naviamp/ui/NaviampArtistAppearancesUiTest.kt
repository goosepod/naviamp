package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampArtistAppearancesUiTest {
    @Test
    fun largeAppearanceListsExpandWithoutResettingOnMetadataUpdates() = runComposeUiTest {
        lateinit var detail: MutableState<SharedArtistDetailUi>
        lateinit var showingArtist: MutableState<Boolean>
        var selectedAlbum: String? = null
        setContent {
            detail = remember { mutableStateOf(SharedArtistDetailUi(
                artist = SharedMediaItemUi("artist", "Artist", ""),
                albums = emptyList(),
                appearanceAlbums = List(125) { SharedMediaItemUi("album-$it", "Appearance album $it", "Artist") },
                appearanceTracks = List(125) { SharedTrackRowUi("track-$it", "Appearance track $it", "Artist") },
            )) }
            showingArtist = remember { mutableStateOf(true) }
            val appearanceState = rememberNaviampArtistAppearanceState(detail.value.artist.id)
            Box(Modifier.width(360.dp).height(640.dp)) {
                if (showingArtist.value) {
                    NaviampArtistDetailContent(
                        NaviampColors(), NaviampArtistDetailScreenUi(detail = detail.value),
                        AlbumCollectionLayout.List, AlbumSortOrder.Title, true,
                        NaviampArtistDetailActions({}, {}, { selectedAlbum = it.album.id }, {}),
                        appearanceState = appearanceState,
                    )
                }
            }
        }
        onNodeWithText("Appearance album 49").assertExists()
        onNodeWithText("Appearance track 49").assertExists()
        onNodeWithText("Appearance album 50").assertDoesNotExist()
        onNodeWithText("Appearance track 50").assertDoesNotExist()
        onNodeWithText("Show more appearances").performScrollTo().performClick()
        onNodeWithText("Appearance track 99").assertExists()
        onNodeWithText("Appearance track 100").assertDoesNotExist()
        runOnIdle {
            detail.value = detail.value.copy(appearanceTracks = detail.value.appearanceTracks.map {
                if (it.id == "track-0") it.copy(favoriteActive = true) else it
            })
        }
        onNodeWithText("Appearance track 99").assertExists()
        onNodeWithText("Show more appearances").performScrollTo().performClick()
        onNodeWithText("Appearance album 124").assertExists()
        onNodeWithText("Appearance track 124").assertExists()
        onNodeWithText("Show more appearances").assertDoesNotExist()
        onNodeWithText("Appearance album 124").performScrollTo().performClick()
        runOnIdle { assertEquals("album-124", selectedAlbum) }
        runOnIdle { showingArtist.value = false }
        onNodeWithText("Appearance album 124").assertDoesNotExist()
        runOnIdle { showingArtist.value = true }
        onNodeWithText("Appearance album 124").assertExists()
        onNodeWithText("Appearance track 124").assertExists()
        onNodeWithText("Show more appearances").assertDoesNotExist()
        runOnIdle { detail.value = detail.value.copy(artist = detail.value.artist.copy(id = "other")) }
        onNodeWithText("Appearance track 50").assertDoesNotExist()
        onNodeWithText("Show more appearances").assertExists()
        runOnIdle {
            detail.value = detail.value.copy(
                albums = listOf(SharedMediaItemUi("primary", "Primary release", "Artist")),
                albumSections = listOf(SharedAlbumSectionUi(
                    app.naviamp.domain.media.AlbumReleaseSection.Albums,
                    listOf(SharedMediaItemUi("primary", "Primary release", "Artist")),
                )),
                appearanceLoadFailed = true,
                appearancesTruncated = true,
            )
        }
        onNodeWithText("Primary release").assertExists()
        onNodeWithText("Could not load credited appearances. Reopen this artist to try again.").assertExists()
        onNodeWithText("Showing a limited set of credited appearances.").assertExists()
    }
}
