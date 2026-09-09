package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampFavoriteArtistSettingsUiTest {
    @Test
    fun detailSortMenuShowsOnlyTheCurrentChoiceUntilOpened() = runDesktopComposeUiTest(300, 300) {
        val sort = mutableStateOf(FavoriteArtistSort.Name)
        setContent { FavoriteArtistSortMenu(sort.value, NaviampColors()) { sort.value = it } }
        onNodeWithText("Name").assertIsDisplayed()
        onNodeWithText("Date favorited").assertDoesNotExist()
        onNodeWithText("Last radio played").assertDoesNotExist()
        onNodeWithContentDescription("Sorting").performClick()
        onNodeWithText("Last radio played").performClick()
        runOnIdle { assertEquals(FavoriteArtistSort.LastRadioPlayed, sort.value) }
        onNodeWithText("Last radio played").assertIsDisplayed()
        onNodeWithText("Name").assertDoesNotExist()
    }

    @Test
    fun favoriteArtistsHasVisibilityAndLayoutControlsInHomeSettings() = runDesktopComposeUiTest(300, 640) {
        val settings = mutableStateOf(InterfaceSettings())
        setContent {
            Column(Modifier.height(600.dp).background(NaviampColors().controlSurface).verticalScroll(rememberScrollState())) {
                HomeScreenExperienceSettings(NaviampColors(), settings.value) { settings.value = it }
            }
        }
        onNodeWithText("Favorite Artists").assertIsDisplayed().performClick()
        onNodeWithText("Last radio played").performScrollTo().performClick()
        runOnIdle { assertEquals(FavoriteArtistSort.LastRadioPlayed, settings.value.favoriteArtistSort) }
        onNodeWithText("Date favorited").performScrollTo().performClick()
        runOnIdle { assertEquals(FavoriteArtistSort.DateFavorited, settings.value.favoriteArtistSort) }
        onNodeWithText("Name").performScrollTo().performClick()
        runOnIdle { assertEquals(FavoriteArtistSort.Name, settings.value.favoriteArtistSort) }
        onNodeWithText("Visible").performScrollTo()
        writeHomeSettingsImage(onRoot().captureToImage(), "section-details")
        onNodeWithText("Visible").performClick()
        runOnIdle { assertFalse(settings.value.homeSectionPresentation(HomeSectionIds.FavoriteArtists).visible) }
        onAllNodesWithText("Grid")[0].performScrollTo().performClick()
        runOnIdle { assertEquals(HomeSectionLayout.Grid, settings.value.homeSectionPresentation(HomeSectionIds.FavoriteArtists).homeLayout) }
        onNodeWithContentDescription("Back").performScrollTo().performClick()
        onNodeWithText("Hidden").assertIsDisplayed()
        onNodeWithText("Favorite Artists").performClick()
        onNodeWithText("Visible").performClick()
        runOnIdle { kotlin.test.assertTrue(settings.value.homeSectionPresentation(HomeSectionIds.FavoriteArtists).visible) }
    }

    @Test
    fun draggingOnTheMainSettingsListSavesOrderWithoutOpeningASection() = runComposeUiTest {
        val settings = mutableStateOf(InterfaceSettings())
        setContent {
            Column(Modifier.height(600.dp).background(NaviampColors().controlSurface).verticalScroll(rememberScrollState())) {
                HomeScreenExperienceSettings(NaviampColors(), settings.value) { settings.value = it }
            }
        }
        onNodeWithContentDescription("Drag Favorite Artists to reorder", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, 110f), 500)
        }
        runOnIdle { assertEquals(2, settings.value.resolvedHomeSectionOrder().indexOf(HomeSectionIds.FavoriteArtists)) }
        onNodeWithText("Visible").assertDoesNotExist()
        onNodeWithText("Section order").assertDoesNotExist()
    }

    @Test
    fun holdingADragAtEitherEdgeScrollsAndCommitsTheFullDistance() = runDesktopComposeUiTest(300, 400) {
        val settings = mutableStateOf(InterfaceSettings())
        lateinit var scroll: androidx.compose.foundation.ScrollState
        val viewport = mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
        setContent {
            scroll = rememberScrollState(400)
            Column(Modifier.height(300.dp)
                .onGloballyPositioned { viewport.value = it.boundsInRoot() }
                .verticalScroll(scroll)) {
                HomeScreenExperienceSettings(NaviampColors(), settings.value, scroll, viewport.value) { settings.value = it }
            }
        }
        val handle = onNodeWithContentDescription("Drag Recent Playlists to reorder", useUnmergedTree = true)
        handle.assertIsDisplayed()
        val bounds = handle.fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            moveTo(Offset(center.x, 5f - bounds.top), 100)
        }
        mainClock.advanceTimeBy(2000)
        runOnIdle { assertEquals(0, scroll.value) }
        handle.performTouchInput { up() }
        mainClock.advanceTimeBy(32)
        runOnIdle { assertEquals(HomeSectionIds.RecentPlaylists, settings.value.resolvedHomeSectionOrder().first()) }
        val top = handle.fetchSemanticsNode().boundsInRoot
        handle.performTouchInput {
            down(center)
            moveTo(Offset(center.x, 295f - top.top), 100)
        }
        mainClock.advanceTimeBy(3000)
        runOnIdle { assertEquals(scroll.maxValue, scroll.value) }
        handle.performTouchInput { up() }
        mainClock.advanceTimeBy(32)
        runOnIdle { assertEquals(HomeSectionIds.RecentPlaylists, settings.value.resolvedHomeSectionOrder().last()) }
        val stoppedAt = scroll.value
        mainClock.advanceTimeBy(500)
        runOnIdle { assertEquals(stoppedAt, scroll.value) }
    }

    @Test
    fun favoriteArtistsIsIncludedInSectionOrdering() = runDesktopComposeUiTest(300, 640) {
        setContent {
            Column(Modifier.height(600.dp).background(NaviampColors().controlSurface).verticalScroll(rememberScrollState())) {
                HomeScreenExperienceSettings(NaviampColors(), InterfaceSettings()) { }
            }
        }
        onNodeWithText("Section order").assertDoesNotExist()
        onNodeWithText("Favorite Artists").assertIsDisplayed()
        onNodeWithContentDescription("Drag Favorite Artists to reorder").assertExists()
        writeHomeSettingsImage(onRoot().captureToImage(), "section-list")
    }
}

private fun writeHomeSettingsImage(image: androidx.compose.ui.graphics.ImageBitmap, name: String) {
    val pixels = image.toPixelMap()
    val output = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) output.setRGB(x, y, pixels[x, y].toArgb())
    val file = java.io.File("build/reports/home-settings/$name.png")
    file.parentFile.mkdirs()
    javax.imageio.ImageIO.write(output, "png", file)
}
