package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaviampSmartPlaylistActionSourceTest {
    private val item = SharedMediaItemUi(id = "smart-1", title = "Work Ambient", subtitle = "")

    @Test
    fun returnsResolvedHostSource() {
        assertEquals("domain-playlist", requireSmartPlaylistActionSource(item) { "domain-playlist" })
    }

    @Test
    fun reportsMissingSourceWithCommonMessage() {
        val error = assertFailsWith<NaviampSmartPlaylistSourceUnavailableException> {
            requireSmartPlaylistActionSource<String>(item) { null }
        }

        assertEquals("Playlist Work Ambient is no longer available.", error.message)
    }
}
