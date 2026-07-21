package app.naviamp.android

import app.naviamp.domain.Playlist
import app.naviamp.ui.SharedMediaItemUi
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidPlaylistActionSourceTest {
    @Test
    fun detailActionUsesSelectedPlaylistBeforeTheHomeList() {
        val selected = Playlist(
            id = "smart-1",
            name = "Selected smart playlist",
            trackCount = 12,
            isSmart = true,
        )
        val item = SharedMediaItemUi(
            id = selected.id,
            title = selected.name,
            subtitle = "12 tracks",
            isSmartPlaylist = true,
        )

        assertEquals(selected, androidPlaylistActionSource(selected, emptyList(), item))
    }

    @Test
    fun listActionFallsBackToTheHomePlaylistCollection() {
        val homePlaylist = Playlist(
            id = "smart-2",
            name = "Home smart playlist",
            trackCount = 8,
            isSmart = true,
        )
        val item = SharedMediaItemUi(
            id = homePlaylist.id,
            title = homePlaylist.name,
            subtitle = "8 tracks",
            isSmartPlaylist = true,
        )

        assertEquals(homePlaylist, androidPlaylistActionSource(null, listOf(homePlaylist), item))
    }
}
