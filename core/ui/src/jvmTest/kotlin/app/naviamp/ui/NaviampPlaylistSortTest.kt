package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampPlaylistSortTest {
    private val playlists = listOf(
        SharedMediaItemUi("z", "Zulu", ""),
        SharedMediaItemUi("a", "Alpha", ""),
        SharedMediaItemUi("m", "Middle", ""),
    )

    @Test
    fun playlistSortModesProduceTheirAdvertisedOrder() {
        assertEquals(
            listOf("a", "m", "z"),
            playlists.sortedForPlaylistScreen(SharedPlaylistSortMode.Alphabetical, listOf("z", "m"))
                .map(SharedMediaItemUi::id),
        )
        assertEquals(
            listOf("z", "m", "a"),
            playlists.sortedForPlaylistScreen(SharedPlaylistSortMode.RecentlyPlayed, listOf("z", "m"))
                .map(SharedMediaItemUi::id),
        )
    }
}
