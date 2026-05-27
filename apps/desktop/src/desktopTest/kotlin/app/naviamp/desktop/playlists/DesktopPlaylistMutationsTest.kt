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

    @Test
    fun smartPlaylistStatusMessagesIncludeTrackCount() {
        val playlist = playlist("smart", "Smart")

        assertEquals(
            "Saved smart playlist Smart with 3 tracks.",
            smartPlaylistSavedStatus(playlist, trackCount = 3),
        )
        assertEquals(
            "Updated smart playlist Smart with 4 tracks.",
            smartPlaylistUpdatedStatus(playlist, trackCount = 4),
        )
    }

    @Test
    fun smartPlaylistSaveErrorExplainsMissingPasswordRecovery() {
        assertEquals(
            "Edit this saved connection, enter your Navidrome password, then Save and connect before saving smart playlists.",
            smartPlaylistSaveErrorMessage(
                IllegalStateException("Reconnect to Navidrome with your password before saving smart playlists."),
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
