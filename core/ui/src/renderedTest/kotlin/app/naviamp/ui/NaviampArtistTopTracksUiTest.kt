package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampArtistTopTracksUiTest {
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
