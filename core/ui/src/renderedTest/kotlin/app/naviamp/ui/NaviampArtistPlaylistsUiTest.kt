package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampArtistPlaylistsUiTest {
    @Test fun loadingPartialFailureRetryAndPlaylistNavigationAreReadable() = runComposeUiTest {
        val state = mutableStateOf(SharedArtistPlaylistsUi(loading = true))
        var selected: String? = null
        var retries = 0
        setContent {
            Column(Modifier.width(400.dp).height(600.dp)) {
                ArtistPlaylistsSection(state.value, NaviampColors(), { selected = it.id }, { retries++ })
            }
        }
        onNodeWithText("Finding your playlists…").assertExists()
        onNodeWithText("Refresh").assertIsNotEnabled()
        onNodeWithText("None of your playlists contain this artist.").assertDoesNotExist()
        runOnIdle { state.value = SharedArtistPlaylistsUi(items = listOf(SharedMediaItemUi("mine", "Road trip", "3 tracks")), failed = true) }
        onNodeWithText("Some playlists could not be checked. Retry to check them again.").assertExists()
        onNodeWithText("Road trip").performClick()
        onNodeWithText("Retry").performClick()
        runOnIdle { assertEquals("mine", selected); assertEquals(1, retries) }
        runOnIdle { state.value = SharedArtistPlaylistsUi() }
        onNodeWithText("None of your playlists contain this artist.").assertExists()
        onNodeWithText("Retry").assertDoesNotExist()
        runOnIdle { state.value = SharedArtistPlaylistsUi(truncated = true) }
        onNodeWithText("None of your playlists contain this artist.").assertDoesNotExist()
        onNodeWithText("Only the first 500 playlists were checked. More playlists may contain this artist.").assertExists()
    }

    @Test fun artistPageHonorsVisibilityAndRoutesSelectionAndRefresh() = runComposeUiTest {
        val visible = mutableStateOf(true)
        var selected: String? = null
        var refreshed: String? = null
        setContent {
            Box(Modifier.width(480.dp).height(640.dp)) {
                NaviampArtistDetailContent(NaviampColors(), NaviampArtistDetailScreenUi(detail = SharedArtistDetailUi(
                    artist = SharedMediaItemUi("artist", "Artist", ""), albums = emptyList(),
                    playlistsVisible = visible.value,
                    playlists = SharedArtistPlaylistsUi(items = listOf(SharedMediaItemUi("mine", "Road trip", "3 tracks"))),
                )), albumCollectionLayout = AlbumCollectionLayout.List, albumSortOrder = AlbumSortOrder.Title,
                    groupAlbumsByReleaseType = true, actions = NaviampArtistDetailActions({}, {}, {}, {},
                        onPlaylistSelected = { selected = it.id }, onPlaylistsRefresh = { refreshed = it }))
            }
        }
        onNodeWithText("Road trip").performScrollTo().performClick()
        onNodeWithText("Refresh").performScrollTo().performClick()
        runOnIdle { assertEquals("mine", selected); assertEquals("artist", refreshed); visible.value = false }
        onNodeWithTag("artist_playlists").assertDoesNotExist()
        runOnIdle { visible.value = true }
        onNodeWithTag("artist_playlists").assertExists()
    }

    @Test fun experienceSettingCanBeSwitchedOffAndOnWithoutChangingOtherPreferences() = runComposeUiTest {
        val settings = mutableStateOf(InterfaceSettings(showArtistInformation = false))
        setContent {
            Column(Modifier.width(480.dp).height(600.dp).verticalScroll(rememberScrollState())) {
                NaviampExperienceSettingsSection(NaviampColors(), installedVersion = "v2.10.0",
                    interfaceSettings = settings.value, playbackSettings = PlaybackSettings(), cacheSettings = CacheSettings(),
                    showQueueBehavior = true, showLrclibLyrics = true, showSoftwareVolumePreference = false,
                    supportsSonicSimilarity = false, onInterfaceSettingsChanged = { settings.value = it },
                    onPlaybackSettingsChanged = {}, onCacheSettingsChanged = {})
            }
        }
        onNodeWithText("Show playlists on artist pages").performScrollTo().performClick()
        runOnIdle { assertFalse(settings.value.showArtistPlaylists); assertFalse(settings.value.showArtistInformation) }
        onNodeWithText("Show playlists on artist pages").performClick()
        runOnIdle { assertTrue(settings.value.showArtistPlaylists) }
    }
}
