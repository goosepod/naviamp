package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.ProviderIdAurral
import app.naviamp.domain.settings.HomeSectionIds
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampAurralHomeSettingsUiTest {
    @Test fun standaloneAndCompanionEntriesRespectHomeVisibilityAndOrder() = runDesktopComposeUiTest(850, 800) {
        val companion = mutableStateOf(false)
        val home = mutableStateOf(SharedHomeUi(collectionSections = listOf(SharedHomeCollectionSectionUi(
            id = HomeSectionIds.RecentAlbums, title = "Albums", items = emptyList(),
            favoriteArtistSort = app.naviamp.domain.settings.FavoriteArtistSort.Name,
            favoriteArtistsStatus = app.naviamp.domain.home.FavoriteArtistsStatus.Ready))))
        val actions = NaviampHomeActions(onRefresh = {}, onRecentRadioSelected = {}, onInternetRadioStationSelected = {},
            onMixBuilderSelected = {}, onStationSelected = {}, onSonicDiscoveryTrackAction = {}, onRecentlyPlayedTrackAction = {},
            onCollectionSelected = {}, onCollectionBack = {}, onCollectionPageLayoutChanged = { _, _ -> })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize()) {
                    SharedHome(NaviampColors.Dark, home.value, actions, NaviampMediaActions({}, {})) {
                        NaviampAurralHomeEntry(SharedProviderDiscoveryUi(ProviderIdAurral, isCompanion = companion.value), NaviampColors.Dark) {}
                    }
                }
            }
        }
        for (isCompanion in listOf(false, true)) {
            runOnIdle { companion.value = isCompanion; home.value = home.value.copy(aurralHomeVisible = true,
                homeSectionOrder = listOf(HomeSectionIds.Aurral, HomeSectionIds.RecentAlbums)) }
            val label = if (isCompanion) "Aurral companion" else "Aurral"
            assertTrue(onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top < onNodeWithText("RECENT ALBUMS").fetchSemanticsNode().boundsInRoot.top)
            runOnIdle { home.value = home.value.copy(homeSectionOrder = listOf(HomeSectionIds.RecentAlbums, HomeSectionIds.Aurral)) }
            assertTrue(onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top > onNodeWithText("RECENT ALBUMS").fetchSemanticsNode().boundsInRoot.top)
            runOnIdle { home.value = home.value.copy(aurralHomeVisible = false) }
            onNodeWithText(label).assertDoesNotExist()
        }
    }
}
