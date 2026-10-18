package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampArtistTopTracksUiTest {
    @Test fun artistSettingsAlignAtNarrowWidth() = verifyArtistSettingsLayout(320)
    @Test fun artistSettingsAlignAtWideWidth() = verifyArtistSettingsLayout(960)

    private fun verifyArtistSettingsLayout(width: Int) = runComposeUiTest {
        val settings = mutableStateOf(InterfaceSettings(artistTopTracksLimit = 50))
        setContent {
            Column(Modifier.width(width.dp).height(600.dp).verticalScroll(rememberScrollState()).testTag("artist-settings-viewport")) {
                NaviampExperienceSettingsSection(NaviampColors(), installedVersion = "v2.10.0",
                    interfaceSettings = settings.value, playbackSettings = PlaybackSettings(), cacheSettings = CacheSettings(),
                    showQueueBehavior = true, showLrclibLyrics = true, showSoftwareVolumePreference = false,
                    supportsSonicSimilarity = false, onInterfaceSettingsChanged = { settings.value = it },
                    onPlaybackSettingsChanged = {}, onCacheSettingsChanged = {})
            }
        }
        onNodeWithText("Show artist information").assertDoesNotExist()
        onNodeWithText("Show playlists on artist pages").assertDoesNotExist()
        onNodeWithText("Artist details").performScrollTo().performClick()
        onNodeWithText("Show artist information").performScrollTo().performClick()
        val slider = onNodeWithContentDescription("Maximum Top Tracks").performScrollTo().fetchSemanticsNode().boundsInRoot
        val control = onNodeWithTag("artist_top_tracks_limit").fetchSemanticsNode().boundsInRoot
        val viewport = onNodeWithTag("artist-settings-viewport").fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Maximum Top Tracks", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = onNodeWithText("50 tracks", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val help = onNodeWithText("Show up to this many matching tracks from your library.", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val toggle = onNodeWithText("Show Top Tracks on artist pages", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun aligned(expected: Float, actual: Float) = assertTrue(kotlin.math.abs(expected - actual) <= 1f,
            "Expected aligned edges: $expected and $actual at width $width")
        aligned(toggle.left, title.left)
        aligned(toggle.left, control.left)
        aligned(toggle.left, help.left)
        aligned(control.right, value.right)
        // Slider semantics include extra space for endpoint accessibility. Keep that
        // hit area inside the page, while its visible control follows the row gutters.
        assertTrue(slider.left >= viewport.left && slider.right <= viewport.right)
        onNodeWithText("Show playlists on artist pages").performScrollTo().performClick()
        onNodeWithContentDescription("Back").performScrollTo().performClick()
        onNodeWithText("Show Top Tracks on artist pages").assertDoesNotExist()
        onNodeWithText("Artist details").performScrollTo().performClick()
        onNodeWithContentDescription("Maximum Top Tracks").assertIsEnabled()
        runOnIdle {
            assertFalse(settings.value.showArtistInformation)
            assertFalse(settings.value.showArtistPlaylists)
            assertTrue(settings.value.showArtistTopTracks)
            assertEquals(50, settings.value.artistTopTracksLimit)
        }
    }

    @Test fun experienceAllowsEveryFiveTrackStepAndDisablesTheLimitWhenHidden() = runComposeUiTest {
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
        onNodeWithText("Show Top Tracks on artist pages").assertDoesNotExist()
        onNodeWithText("Artist details").performScrollTo().performClick()
        val slider = onNodeWithContentDescription("Maximum Top Tracks")
        for ((index, limit) in ArtistTopTracksLimits.withIndex()) {
            slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
            runOnIdle { assertEquals(limit, settings.value.artistTopTracksLimit) }
        }
        onNodeWithText("Show Top Tracks on artist pages").performScrollTo().performClick()
        slider.assertIsNotEnabled()
        runOnIdle { assertFalse(settings.value.showArtistTopTracks); assertFalse(settings.value.showArtistInformation) }
        onNodeWithText("Show Top Tracks on artist pages").performClick()
        slider.assertIsEnabled()
        runOnIdle { assertEquals(50, settings.value.artistTopTracksLimit) }
    }

    @Test fun hiddenSectionSuppressesItsTracksAndErrorWithoutHidingDiscography() = runComposeUiTest {
        val visible = mutableStateOf(false)
        setContent {
            Box(Modifier.width(480.dp).height(640.dp)) {
                NaviampArtistDetailContent(NaviampColors(), NaviampArtistDetailScreenUi(detail = SharedArtistDetailUi(
                    artist = SharedMediaItemUi("artist", "Artist", ""), albums = emptyList(),
                    popularTracksVisible = visible.value,
                    popularTracks = listOf(SharedTrackRowUi("top", "Top track", "Artist")),
                    popularTracksStatus = "Discovery failed",
                )), albumCollectionLayout = AlbumCollectionLayout.List, albumSortOrder = AlbumSortOrder.Title,
                    groupAlbumsByReleaseType = true, actions = NaviampArtistDetailActions({}, {}, {}, {}))
            }
        }
        onNodeWithText("Top track").assertDoesNotExist()
        onNodeWithText("Discovery failed").assertDoesNotExist()
        onNodeWithText("DISCOGRAPHY").assertExists()
        runOnIdle { visible.value = true }
        onNodeWithText("Top track").performScrollTo().assertIsDisplayed()
        onNodeWithText("Discovery failed").assertExists()
    }

    @Test fun televisionAlsoHidesTopTracks() = runComposeUiTest {
        val visible = mutableStateOf(false)
        setContent {
            Box(Modifier.width(960.dp).height(720.dp)) {
                TelevisionArtistDetail(
                    screen = NaviampArtistDetailScreenUi(detail = SharedArtistDetailUi(
                        artist = SharedMediaItemUi("artist", "Artist", ""), albums = emptyList(),
                        popularTracksVisible = visible.value,
                        popularTracks = listOf(SharedTrackRowUi("top", "Top track", "Artist")),
                    )), colors = NaviampColors(), actions = NaviampArtistDetailActions({}, {}, {}, {}),
                    topNavigationFocusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() },
                )
            }
        }
        onNodeWithText("Top track").assertDoesNotExist()
        runOnIdle { visible.value = true }
        onNodeWithText("Top track").performScrollTo().assertIsDisplayed()
    }

    @Test fun sharedMapperAppliesTheMaximumAndVisibilityForEveryHost() {
        val artist = app.naviamp.domain.Artist(app.naviamp.domain.ArtistId("artist"), "Artist")
        val tracks = (1..60).map { index -> app.naviamp.domain.Track(
            id = app.naviamp.domain.TrackId("$index"), title = "Track $index", artistName = "Artist",
            albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null) }
        val detail = app.naviamp.domain.ArtistDetails(artist, emptyList(), null)
        for (limit in ArtistTopTracksLimits) {
            assertEquals(limit, detail.toSharedArtistDetailUi(coverArtUrl = { null }, popularTracks = tracks, artistTopTracksLimit = limit).popularTracks.size)
        }
        val hidden = detail.toSharedArtistDetailUi(coverArtUrl = { null }, popularTracks = tracks, popularTracksStatus = "Failure", showArtistTopTracks = false)
        assertFalse(hidden.popularTracksVisible)
        assertTrue(hidden.popularTracks.isEmpty())
        assertNull(hidden.popularTracksStatus)
    }
}
