package app.naviamp.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

/** Shared Library viewport state retained while detail routes temporarily replace Library. */
@Stable
class NaviampLibraryViewportState internal constructor(
    private val listStates: Map<NaviampLibraryView, LazyListState>,
) {
    private val focusedTargets = mutableStateMapOf<NaviampLibraryView, String>()

    fun listState(view: NaviampLibraryView): LazyListState = listStates.getValue(view)

    internal fun focusedTarget(view: NaviampLibraryView): String? = focusedTargets[view]

    internal fun recordFocusedTarget(view: NaviampLibraryView, target: String) {
        focusedTargets[view] = target
    }
}

@Composable
fun rememberNaviampLibraryViewportState(
    artists: LazyListState = rememberLazyListState(),
    albums: LazyListState = rememberLazyListState(),
    songs: LazyListState = rememberLazyListState(),
): NaviampLibraryViewportState = remember(artists, albums, songs) {
    NaviampLibraryViewportState(
        mapOf(
            NaviampLibraryView.Artists to artists,
            NaviampLibraryView.Albums to albums,
            NaviampLibraryView.Songs to songs,
        ),
    )
}

internal const val LibrarySearchFocusTarget = "search"

internal fun libraryItemFocusTarget(id: String): String = "item:$id"
