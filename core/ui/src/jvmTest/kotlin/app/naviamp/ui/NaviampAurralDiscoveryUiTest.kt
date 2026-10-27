package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampAurralDiscoveryUiTest {
    @Test fun homeRouteUpdatesSampleIconsFromLivePlaybackAndResetsPausedStoppedAndOtherTracks() = runComposeUiTest {
        val artist = ProviderDiscoveryArtist("artist", "Artist", detailsAvailable = true)
        val detail = SharedProviderDiscoveryArtistUi(artist, loading = false,
            details = ProviderDiscoveryArtistDetails(artist), samplesStatus = ProviderDiscoveryStatus.Ready,
            samples = listOf(ProviderDiscoverySample("1", "First sample"), ProviderDiscoverySample("2", "Second sample")))
        val nowPlaying = mutableStateOf<NowPlayingUi?>(null)
        val homeActions = NaviampHomeActions(onRefresh = {}, onRecentRadioSelected = {}, onInternetRadioStationSelected = {},
            onMixBuilderSelected = {}, onStationSelected = {}, onSonicDiscoveryTrackAction = {}, onRecentlyPlayedTrackAction = {},
            onCollectionSelected = {}, onCollectionBack = {}, onCollectionPageLayoutChanged = { _, _ -> })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(900.dp).height(750.dp)) {
                    SharedHomeRoute(NaviampColors.Dark,
                        NaviampHomeScreenUi(providerDiscovery = SharedProviderDiscoveryUi(ProviderIdAurral, artistDetail = detail)),
                        homeActions, NaviampMediaActions({}, {}), discoveryPlayback = nowPlaying.value.discoverySamplePlayback())
                }
            }
        }
        onAllNodesWithContentDescription("Play sample").assertCountEquals(2)
        val first = NowPlayingUi(id = DiscoveryPreviewTrackPrefix + "1", title = "First sample", subtitle = "Artist",
            stateLabel = "Playing", isPlaying = true)
        runOnIdle { nowPlaying.value = first }
        onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        onAllNodesWithContentDescription("Play sample").assertCountEquals(1)
        runOnIdle { nowPlaying.value = first.copy(isPlaying = false, isPaused = true, stateLabel = "Paused") }
        onAllNodesWithContentDescription("Pause").assertCountEquals(0)
        onAllNodesWithContentDescription("Play sample").assertCountEquals(2)
        runOnIdle { nowPlaying.value = first.copy(isPlaying = false, stateLabel = "Stopped") }
        onAllNodesWithContentDescription("Pause").assertCountEquals(0)
        onAllNodesWithContentDescription("Play sample").assertCountEquals(2)
        runOnIdle { nowPlaying.value = first.copy(id = DiscoveryPreviewTrackPrefix + "2", title = "Second sample") }
        onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        runOnIdle { nowPlaying.value = first.copy(id = "library-track") }
        onAllNodesWithContentDescription("Pause").assertCountEquals(0)
        onAllNodesWithContentDescription("Play sample").assertCountEquals(2)
    }

    @Test fun inlinePlaybackProjectionIgnoresProgressAndLibraryTracks() {
        val current = NowPlayingUi(id = DiscoveryPreviewTrackPrefix + "42", title = "Sample", subtitle = "Artist",
            stateLabel = "Playing", isPlaying = true, positionSeconds = 1.0, durationSeconds = 30.0)
        assertEquals(current.discoverySamplePlayback(), current.copy(positionSeconds = 15.0).discoverySamplePlayback())
        assertNull(current.copy(id = "library-track").discoverySamplePlayback().trackId)
        assertNull((null as NowPlayingUi?).discoverySamplePlayback().trackId)
    }

    @Test fun artistPanelUsesInlineColumnsAndACoverGridAtDesktopAndPhoneWidths() {
        val palette = listOf("2D658E", "7840A4", "A45135", "387D62", "A58935", "8E3D62", "4F657E", "486D95")
        val art = palette.mapIndexed { index, color -> checkNotNull(jvmGeneratedCoverArtBytes(
            "naviamp-radio-tile://cover?label=${index + 1}&from=$color&to=182332")) }
        listOf(1100 to 900, 390 to 844).forEach { (width, height) ->
            resetNaviampCoverArtCache()
            setJvmPlatformCoverArtByteLoader { url -> art[url.substringAfterLast('/').toIntOrNull()?.rem(art.size) ?: 0] }
            try {
                runDesktopComposeUiTest(width, height) {
                    val artist = ProviderDiscoveryArtist("artist", "Discovery artist", imageUrl = "fixture://artist")
                    val screen = SharedProviderDiscoveryArtistUi(artist, loading = false,
                        details = ProviderDiscoveryArtistDetails(artist,
                            releases = (1..8).map { ProviderDiscoveryRelease("release-$it", "Release $it", "2025-02-01",
                                if (it % 2 == 0) "EP" else "Album", imageUrl = "fixture://cover/$it") },
                            canRequestAlbums = true, canRequestTracks = true, canChangeMonitoring = true, monitored = false),
                        samplesStatus = ProviderDiscoveryStatus.Ready,
                        samples = (1..6).map { ProviderDiscoverySample("$it", "Sample $it", "Sample album $it") })
                    val playback = mutableStateOf(DiscoverySamplePlaybackUi(DiscoveryPreviewTrackPrefix + "4", playing = true))
                    val actions = mutableListOf<ProviderDiscoveryAction>()
                    setContent {
                        MaterialTheme(colorScheme = darkColorScheme()) {
                            Box(Modifier.fillMaxSize().background(NaviampColors.Dark.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                NaviampDiscoveryArtistPanel(screen, "Aurral", NaviampColors.Dark, {}, {}, {}, {}, {},
                                    { actions += it }, playback.value)
                            }
                        }
                    }
                    onNodeWithText("Load samples").assertDoesNotExist()
                    val first = onNodeWithText("Sample 1").fetchSemanticsNode().boundsInRoot
                    if (width > 680) {
                        val fourth = onNodeWithText("Sample 4").fetchSemanticsNode().boundsInRoot
                        assertTrue(fourth.left > first.right)
                        assertEquals(first.top, fourth.top)
                        onNodeWithContentDescription("Pause").performClick()
                        runOnIdle { assertEquals(ProviderDiscoveryAction.PlaySample("4"), actions.last()); playback.value = playback.value.copy(playing = false) }
                    } else {
                        val second = onNodeWithText("Sample 2").fetchSemanticsNode().boundsInRoot
                        assertEquals(first.left, second.left)
                        assertTrue(second.top > first.top)
                    }
                    fun capture(suffix: String) {
                        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
                            java.io.File(directory).mkdirs()
                            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "artist-panel-$width-$suffix.png"))
                        }
                    }
                    capture("samples")
                    onNodeWithText("All releases").performScrollTo()
                    onNodeWithText("Release 1").performScrollTo()
                    val filter = onNodeWithText("All releases").fetchSemanticsNode().boundsInRoot
                    fun covers() = onAllNodesWithContentDescription("Album art", useUnmergedTree = true).fetchSemanticsNodes()
                        .map { it.boundsInRoot }.filter { it.top >= filter.bottom }
                    waitUntil(timeoutMillis = 5_000) { covers().size >= 2 }
                    val covers = covers().sortedWith(compareBy({ it.top }, { it.left }))
                    assertEquals(covers[0].width, covers[0].height)
                    assertEquals(covers[0].top, covers[1].top)
                    assertTrue(covers[1].left > covers[0].right)
                    assertTrue(onNodeWithText("Release 1").fetchSemanticsNode().boundsInRoot.top >= covers[0].bottom)
                    capture("releases")
                    onAllNodesWithText("EPs")[0].performClick()
                    onNodeWithText("Release 1").assertDoesNotExist()
                    onNodeWithText("Release 2").assertExists()
                }
            } finally { resetJvmPlatformCoverArtByteLoader() }
        }
    }

    @Test fun previewControlsAllowPlaybackButNeverLibraryMutationOrPlaylistActions() {
        val capabilities = nowPlayingTrackCapabilities(isLiveStream = false,
            playbackState = app.naviamp.domain.playback.PlaybackState.Playing, isPreview = true,
            supportsPause = true, supportsSeek = true, supportsTrackFavorites = true, supportsTrackRatings = true,
            supportsTrackRadio = true, canRepeatQueue = true, canSaveQueueAsPlaylist = true, canAddToPlaylist = true)
        assertTrue(capabilities.canPlayPause && capabilities.canSeek)
        assertFalse(capabilities.canFavorite || capabilities.canRate || capabilities.canStartRadio ||
            capabilities.canSaveQueueAsPlaylist || capabilities.canAddToPlaylist || capabilities.lyricsAvailable)
    }

    @Test fun releaseFiltersKeepSquareCoversAndNativeActionsExposeTheirRealStates() {
        val bytes = checkNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=R&from=CC2244&to=551122"))
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            runComposeUiTest {
                val artist = ProviderDiscoveryArtist("artist", "Artist", detailsAvailable = true)
                val detail = mutableStateOf(SharedProviderDiscoveryArtistUi(artist, loading = false,
                    details = ProviderDiscoveryArtistDetails(artist, releases = listOf(
                        ProviderDiscoveryRelease("album", "Album release", type = "Album", imageUrl = "test://album"),
                        ProviderDiscoveryRelease("ep", "EP release", type = "EP", imageUrl = "test://ep")),
                        canRequestAlbums = true, canRequestTracks = true, canChangeMonitoring = true, monitored = false)))
                val actions = mutableListOf<ProviderDiscoveryAction>()
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral, artistDetail = detail.value),
                            NaviampColors.Dark, {}, onAction = { _, action -> actions += action })
                    }
                }
                onAllNodesWithText("EPs")[0].performClick()
                onNodeWithText("Album release").assertDoesNotExist()
                onNodeWithText("EP release").assertExists()
                onNodeWithContentDescription("Request album").performClick()
                runOnIdle { assertEquals(ProviderDiscoveryAction.RequestAlbum("ep"), actions.last()) }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Album art", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
                val cover = onNodeWithContentDescription("Album art", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(cover.width, cover.height)
                assertEquals(4.dp, mediaArtworkCornerRadius(SharedMediaItemKind.Album, 64.dp, 4.dp))
                onNodeWithText("All releases").performClick()
                onNodeWithText("Album release").assertExists()
                onNodeWithText("Monitor artist").performClick()
                runOnIdle { assertEquals(ProviderDiscoveryAction.SetMonitoring(true), actions.last()) }
                onNodeWithText("Load samples").assertDoesNotExist()
                runOnIdle {
                    detail.value = detail.value.copy(samplesStatus = ProviderDiscoveryStatus.Ready,
                        samples = listOf(ProviderDiscoverySample("42", "Test sample")),
                        operationStatus = ProviderDiscoveryOperationStatus.MissingSource)
                }
                onNodeWithText("Saved, but no download source is configured.").assertExists()
                onNodeWithContentDescription("Play sample").performScrollTo().performClick()
                runOnIdle { assertEquals(ProviderDiscoveryAction.PlaySample("42"), actions.last()) }
                onNodeWithContentDescription("Request track").performClick()
                runOnIdle {
                    assertEquals(ProviderDiscoveryAction.RequestTrack("42"), actions.last())
                    detail.value = detail.value.copy(actionBusy = true)
                }
                onNodeWithContentDescription("Request track").assertIsNotEnabled()
                onNodeWithText("Monitor artist").performScrollTo().assertIsNotEnabled()
                System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
                    java.io.File(directory).mkdirs()
                    javax.imageio.ImageIO.write(onNode(isDialog()).captureToImage().toAwtImage(), "png", java.io.File(directory, "release-actions.png"))
                }
            }
        } finally { resetJvmPlatformCoverArtByteLoader() }
    }

    @Test fun portraitNameAndCardBackgroundOpenTheSameArtistWithoutASeparateViewButton() {
        val bytes = checkNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=A&from=CC2244&to=551122"))
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            runComposeUiTest {
                val opened = mutableListOf<String>()
                var external: String? = null
                setContent {
                    Column(Modifier.width(620.dp).height(400.dp)) {
                        NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral,
                            ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, recommendations = listOf(
                                ProviderDiscoveryArtist("native", "Native artist", imageUrl = "test://clickable-portrait",
                                    detailsAvailable = true, externalUrl = "https://music.test/artist/native"),
                                ProviderDiscoveryArtist("external", "External artist", externalUrl = "https://music.test/external"),
                                ProviderDiscoveryArtist("unresolved", "Unresolved artist")))), NaviampColors.Dark,
                            onArtistSelected = { external = it }, onArtistDetails = { opened += it })
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithContentDescription("Album art", useUnmergedTree = true).fetchSemanticsNodes().size == 1
                }
                onNodeWithContentDescription("Album art", useUnmergedTree = true).performTouchInput { click() }
                onNodeWithText("Native artist", useUnmergedTree = true).performTouchInput { click() }
                onNodeWithText("Native artist").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, height - 2f)) }
                runOnIdle { assertEquals(listOf("native", "native", "native"), opened); assertNull(external) }
                onNodeWithText("View artist").assertDoesNotExist()
                onNodeWithText("External artist").performClick()
                runOnIdle { assertEquals("external", external) }
                onNodeWithText("Unresolved artist").assertHasNoClickAction()
            }
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    @Test fun discoveryArtworkLoadsAndPaintsACircularPortrait() {
        val buffer = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = buffer.createGraphics()
        graphics.color = java.awt.Color.RED
        graphics.fillRect(0, 0, 64, 64)
        graphics.dispose()
        val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(buffer, "png", it) }.toByteArray()
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            runComposeUiTest {
                setContent {
                    Column(Modifier.width(400.dp).height(400.dp).background(NaviampColors.Dark.background)) {
                        NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral,
                            ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, trending = listOf(
                                ProviderDiscoveryArtist("portrait", "Portrait", imageUrl = "test://discovery-portrait")))),
                            NaviampColors.Dark, {})
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().size == 1
                }
                val image = onNodeWithContentDescription("Album art", useUnmergedTree = true).captureToImage().toAwtImage()
                assertEquals(java.awt.Color.RED.rgb, image.getRGB(image.width / 2, image.height / 2))
                assertNotEquals(java.awt.Color.RED.rgb, image.getRGB(0, 0), "Portrait corners must be clipped")
            }
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    @Test fun discoveryRailsScrollIndependentlyInBothDirectionsAndKeepArtistImagesRound() = runComposeUiTest {
        val artists = (0..9).map { ProviderDiscoveryArtist("artist-$it", "Artist $it") }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(400.dp).height(700.dp)) {
                    NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral,
                        ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, recommendations = artists, trending = artists)),
                        NaviampColors.Dark, {})
                }
            }
        }
        onAllNodesWithContentDescription("Scroll left")[0].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll left")[1].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll right")[0].assertIsEnabled().performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Scroll left")[0].assertIsEnabled()
        onAllNodesWithContentDescription("Scroll left")[1].assertIsNotEnabled()
        repeat(5) { onAllNodesWithContentDescription("Scroll right")[0].performClick(); waitForIdle() }
        onAllNodesWithContentDescription("Scroll right")[0].assertIsNotEnabled()
        repeat(6) { onAllNodesWithContentDescription("Scroll left")[0].performClick(); waitForIdle() }
        onAllNodesWithContentDescription("Scroll left")[0].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll right")[0].assertIsEnabled()
    }

    @Test fun nativeArtistDialogShowsLibraryLinkReleasesAndTruthfulFeedbackFailure() = runComposeUiTest {
        val artist = ProviderDiscoveryArtist("mbid", "Discovery artist", detailsAvailable = true,
            externalUrl = "https://music.test/artist/mbid")
        val selected = mutableStateOf(SharedProviderDiscoveryArtistUi(artist))
        var feedback: ProviderDiscoveryFeedbackAction? = null
        var libraryOpened = false
        var dismissed = false
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral, artistDetail = selected.value),
                    NaviampColors.Dark, {}, onArtistDismiss = { dismissed = true },
                    onFeedback = { _, action -> feedback = action }, onLibraryArtist = { libraryOpened = true })
            }
        }
        onAllNodesWithText("Loading discovery…").assertCountEquals(1)
        runOnIdle { selected.value = selected.value.copy(loading = false, details = ProviderDiscoveryArtistDetails(artist,
            biography = "An artist biography.", libraryArtist = app.naviamp.domain.Artist(app.naviamp.domain.ArtistId("owned-id"), "Owned"),
            feedbackAvailable = true, releases = listOf(ProviderDiscoveryRelease("release", "Album release", "2020", "Album")))) }
        onNodeWithText("Open in Library").performClick()
        onNodeWithText("More like this").performClick()
        onNodeWithText("Album release").assertExists()
        runOnIdle { assertTrue(libraryOpened); assertEquals(ProviderDiscoveryFeedbackAction.MoreLikeThis, feedback)
            selected.value = selected.value.copy(savingFeedback = true) }
        onNodeWithText("Saving feedback…").assertExists()
        onNodeWithText("Block artist").assertIsNotEnabled()
        runOnIdle { selected.value = selected.value.copy(savingFeedback = false, feedbackFailed = true,
            details = selected.value.details!!.copy(feedbackAvailable = false)) }
        onNodeWithText("Could not save feedback. Refresh to check its current state.").assertExists()
        onNodeWithText("More like this").assertDoesNotExist()
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onNode(isDialog()).captureToImage().toAwtImage(), "png", java.io.File(directory, "artist-details.png"))
        }
        onNodeWithText("Close").performClick()
        runOnIdle { assertTrue(dismissed) }
    }

    @Test fun connectionScreenShowsTheOfficialIconAfterNavidromeAndPreservesSubsonicAccountOnSelection() = runComposeUiTest {
        val form = mutableStateOf(ConnectionFormState(providerId = ProviderIdSubsonic,
            serverUrl = "https://music.test", username = "listener", password = "saved",
            selectedMusicFolderIds = listOf("1"), skipTlsVerification = true))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(760.dp).height(780.dp).background(NaviampColors.Dark.background).padding(16.dp)) {
                    NaviampConnectionForm(form.value, NaviampColors.Dark, isReconnect = true,
                        onFormChanged = { form.value = it }, onConnect = {}, onCancel = {})
                }
            }
        }
        val navidromeBounds = onNodeWithText("Navidrome").fetchSemanticsNode().boundsInRoot
        val aurralBounds = onNodeWithText("Aurral").fetchSemanticsNode().boundsInRoot
        assertTrue(aurralBounds.left > navidromeBounds.left)
        onNodeWithText("Aurral").performClick()
        onNodeWithText("Connect to Aurral with your local account password.").assertExists()
        runOnIdle {
            assertEquals(ProviderIdAurral, form.value.providerId)
            assertEquals("saved", form.value.password)
            assertEquals(listOf("1"), form.value.selectedMusicFolderIds)
            assertTrue(form.value.skipTlsVerification)
        }
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "connection.png"))
        }
    }

    @Test fun loadingBuildingTrendingAndPermissionStatesRenderWithSeparateExternalActions() = runComposeUiTest {
        val state = mutableStateOf(SharedProviderDiscoveryUi(ProviderIdAurral, loading = true))
        var selected: String? = null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(640.dp).height(620.dp).background(NaviampColors.Dark.background).padding(16.dp)) {
                    NaviampServiceIcon(ProviderIdAurral, NaviampColors.Dark.primaryText)
                    NaviampProviderDiscoveryContent(state.value, NaviampColors.Dark, { selected = it })
                }
            }
        }
        onNodeWithText("Aurral discovery").assertExists()
        onNodeWithText("Loading discovery…").assertExists()
        runOnIdle {
            state.value = SharedProviderDiscoveryUi(ProviderIdAurral, ProviderDiscoveryFeed(ProviderDiscoveryStatus.Building,
                trending = listOf(ProviderDiscoveryArtist("mbid", "Radiohead", genres = listOf("alternative rock"),
                    externalUrl = "https://music.test/artist/mbid"))))
        }
        onNodeWithText("Your personal recommendations are being built.").assertExists()
        onNodeWithText("Trending artists").assertExists()
        onNodeWithText("Radiohead").assertExists()
        onNodeWithText("Open in Aurral").performClick()
        runOnIdle { assertEquals("mbid", selected) }
        assertEquals(64.dp, mediaArtworkCornerRadius(SharedMediaItemKind.Artist, 128.dp, 7.dp))
        assertEquals(7.dp, mediaArtworkCornerRadius(SharedMediaItemKind.Album, 128.dp, 7.dp))
        assertNotNull(serviceIconResource(ProviderIdAurral))
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "discovery.png"))
        }
        runOnIdle { state.value = SharedProviderDiscoveryUi(ProviderIdAurral, ProviderDiscoveryFeed(ProviderDiscoveryStatus.Forbidden)) }
        onNodeWithText("Your account cannot access discovery.").assertExists()
        onNodeWithText("Open in Aurral").assertDoesNotExist()
        onNodeWithText("Your personal recommendations will appear here.").assertDoesNotExist()
    }
}
