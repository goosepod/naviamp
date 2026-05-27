package app.naviamp.desktop

import app.naviamp.domain.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaylistMutationsTest {
    @Test
    fun homePlaylistsPrioritizesRecentThenName() {
        val alpha = playlist("alpha", "Alpha")
        val beta = playlist("beta", "Beta")
        val recent = playlist("recent", "Recent")

        assertEquals(
            listOf(recent, alpha, beta),
            homePlaylists(
                playlists = listOf(beta, recent, alpha),
                recentPlaylistIds = listOf("recent"),
            ),
        )
    }

    private fun playlist(id: String, name: String): Playlist =
        Playlist(
            id = id,
            name = name,
            trackCount = 0,
        )
}
