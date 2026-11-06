package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampRatingRowTest {
    @Test fun starsAndHeartFollowIndependentCapabilitiesEvenWithStaleMetadata() = runComposeUiTest {
        val rate = mutableStateOf(false); val favorite = mutableStateOf(true)
        var favorites = 0; val ratings = mutableListOf<Int?>()
        setContent { MaterialTheme { RatingRow(true, favorite.value, 4, rate.value, NaviampColors.Dark,
            { favorites++ }, { ratings += it }) } }
        onNodeWithContentDescription("Remove favorite").assertIsDisplayed().performClick()
        assertEquals(1, favorites)
        (1..5).forEach { onNodeWithContentDescription("$it star rating").assertDoesNotExist() }
        runOnIdle { favorite.value = false; rate.value = true }
        onNodeWithContentDescription("Remove favorite").assertDoesNotExist()
        onNodeWithContentDescription("3 star rating").performClick()
        assertEquals(listOf<Int?>(3), ratings)
        runOnIdle { rate.value = false }
        onNodeWithContentDescription("Remove favorite").assertDoesNotExist()
        (1..5).forEach { onNodeWithContentDescription("$it star rating").assertDoesNotExist() }
    }
}
