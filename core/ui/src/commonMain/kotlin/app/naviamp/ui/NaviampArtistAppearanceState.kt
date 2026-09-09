package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Retained by the shared shell while album or player content covers an artist. */
@Stable
class NaviampArtistAppearanceState internal constructor() {
    var visibleCount by mutableStateOf(50)
        private set

    fun showMore() {
        visibleCount += 50
    }
}

@Composable
fun rememberNaviampArtistAppearanceState(artistId: String?): NaviampArtistAppearanceState =
    remember(artistId) { NaviampArtistAppearanceState() }
