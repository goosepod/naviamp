package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampArtistSectionLayoutsUiTest {
    @Test fun settingsKeepBothLayoutsIndependentAndRetainHiddenPlaylistChoice() = runComposeUiTest {
        val settings = mutableStateOf(InterfaceSettings())
        setContent {
            Column(Modifier.width(480.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                NaviampExperienceSettingsSection(NaviampColors(), installedVersion = "v2.10.0",
                    interfaceSettings = settings.value, playbackSettings = PlaybackSettings(), cacheSettings = CacheSettings(),
                    showQueueBehavior = true, showLrclibLyrics = true, showSoftwareVolumePreference = false,
                    supportsSonicSimilarity = false, onInterfaceSettingsChanged = { settings.value = it },
                    onPlaybackSettingsChanged = {}, onCacheSettingsChanged = {})
            }
        }
        onNodeWithText("Artist details").performScrollTo().performClick()
        fun option(tag: String, name: String) = onNode(hasText(name) and hasAnyAncestor(hasTestTag(tag)))
        option("artist_playlist_layout", "Grid").performScrollTo().performClick()
        runOnIdle {
            assertEquals(AlbumCollectionLayout.Grid, settings.value.artistPlaylistLayout)
            assertEquals(AlbumCollectionLayout.List, settings.value.artistAppearanceAlbumLayout)
            assertEquals(AlbumCollectionLayout.List, settings.value.albumCollectionLayout)
        }
        option("artist_appearance_album_layout", "Grid").performScrollTo().performClick()
        option("artist_playlist_layout", "List").performScrollTo().performClick()
        runOnIdle { assertEquals(AlbumCollectionLayout.Grid, settings.value.artistAppearanceAlbumLayout) }
        onNodeWithText("Show playlists on artist pages").performScrollTo().performClick()
        option("artist_playlist_layout", "Grid").assertIsNotEnabled()
        option("artist_playlist_layout", "List").assertIsSelected()
        onNodeWithText("Show playlists on artist pages").performClick()
        option("artist_playlist_layout", "List").assertIsEnabled().assertIsSelected()
        option("artist_appearance_album_layout", "Grid").assertIsSelected()
    }

    @Test fun playlistGridFitsNarrowWidthAndKeepsSelection() = verifyGrid(240)
    @Test fun playlistGridFitsWideWidthAndKeepsSelection() = verifyGrid(960)

    private fun verifyGrid(width: Int) = runComposeUiTest {
        val layout = mutableStateOf(AlbumCollectionLayout.List)
        var selected: String? = null
        setContent {
            Box(Modifier.width(width.dp).testTag("viewport")) {
                ArtistSectionMediaCollection((1..3).map { SharedMediaItemUi("$it", "Playlist $it", "3 tracks") },
                    layout.value, SharedMediaItemKind.Playlist, NaviampColors(), { selected = it.id })
            }
        }
        val firstList = onNodeWithTag("artist_section_item:1").fetchSemanticsNode().boundsInRoot
        val secondList = onNodeWithTag("artist_section_item:2").fetchSemanticsNode().boundsInRoot
        assertTrue(secondList.top > firstList.top)
        runOnIdle { layout.value = AlbumCollectionLayout.Grid }
        val first = onNodeWithTag("artist_section_item:1").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag("artist_section_item:2").fetchSemanticsNode().boundsInRoot
        val viewport = onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.left && second.right <= viewport.right)
        onNodeWithContentDescription("Playlist 1").performClick()
        runOnIdle { assertEquals("1", selected) }
    }

    @Test fun playlistGridPreservesRetryLoadingAndPartialResults() = runComposeUiTest {
        val state = mutableStateOf(SharedArtistPlaylistsUi(loading = true))
        var retries = 0
        var selected: String? = null
        setContent {
            Column(Modifier.width(480.dp).height(600.dp)) {
                ArtistPlaylistsSection(state.value, NaviampColors(), { selected = it.id }, { retries++ }, AlbumCollectionLayout.Grid)
            }
        }
        onNodeWithText("Refresh").assertIsNotEnabled()
        runOnIdle { state.value = SharedArtistPlaylistsUi(
            items = listOf(SharedMediaItemUi("mine", "Road trip", "3 tracks")), failed = true, truncated = true) }
        onNodeWithText("Some playlists could not be checked. Retry to check them again.").assertExists()
        onNodeWithText("Only the first 500 playlists were checked. More playlists may contain this artist.").assertExists()
        onNodeWithText("None of your playlists contain this artist.").assertDoesNotExist()
        onNodeWithContentDescription("Road trip").performClick()
        onNodeWithText("Retry").performClick()
        runOnIdle { assertEquals("mine", selected); assertEquals(1, retries) }
    }

    @Test fun appearanceAlbumsSwitchLayoutWithoutChangingTrackRowsOrPagination() = runComposeUiTest {
        val layout = mutableStateOf(AlbumCollectionLayout.Grid)
        val commands = mutableListOf<NaviampArtistAlbumCommand>()
        setContent {
            Box(Modifier.width(480.dp).height(640.dp)) {
                NaviampArtistDetailContent(NaviampColors(), NaviampArtistDetailScreenUi(detail = SharedArtistDetailUi(
                    artist = SharedMediaItemUi("artist", "Artist", ""), albums = emptyList(), playlistsVisible = false,
                    appearanceAlbums = (1..51).map { SharedMediaItemUi("a$it", "Appearance $it", "Various", canFavorite = true) },
                    appearanceTracks = listOf(SharedTrackRowUi("track", "Guest track", "Artist")),
                )), albumCollectionLayout = AlbumCollectionLayout.List, albumSortOrder = AlbumSortOrder.Title,
                    groupAlbumsByReleaseType = true, artistAppearanceAlbumLayout = layout.value,
                    actions = NaviampArtistDetailActions(onBack = {}, onArtistAction = {},
                        onAlbumAction = { commands += it.command }, onPopularTrackAction = {}))
            }
        }
        onNodeWithTag("artist_section_grid").assertExists()
        onNodeWithText("Appearance 51").assertDoesNotExist()
        onNodeWithContentDescription("Appearance 1").performScrollTo().performClick()
        runOnIdle { assertEquals(NaviampArtistAlbumCommand.Select, commands.last()) }
        onNode(hasContentDescription("More actions") and hasAnyAncestor(hasTestTag("artist_section_item:a1")))
            .performScrollTo().performClick()
        onNodeWithText("Favorite").performClick()
        runOnIdle { assertEquals(NaviampArtistAlbumCommand.ToggleFavorite, commands.last()) }
        onNodeWithText("Guest track").performScrollTo().assertIsDisplayed()
        onNode(hasText("Guest track") and hasAnyAncestor(hasTestTag("artist_section_grid"))).assertDoesNotExist()
        onNodeWithText("Show more appearances").performScrollTo().performClick()
        onNodeWithText("Appearance 51").assertExists()
        runOnIdle { layout.value = AlbumCollectionLayout.List }
        onNodeWithTag("artist_section_grid").assertDoesNotExist()
        onNodeWithTag("artist_section_list").assertExists()
        onNodeWithText("Appearance 51").assertExists()
        onNodeWithText("Guest track").assertExists()
        onNodeWithText("Show more appearances").assertDoesNotExist()
    }
}
