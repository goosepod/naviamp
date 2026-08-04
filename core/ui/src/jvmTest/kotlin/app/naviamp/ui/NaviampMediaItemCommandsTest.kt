package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampMediaItemCommandsTest {
    @Test
    fun actionCatalogBuildsOnlyTypedArtistAndAlbumCommands() {
        assertEquals(
            NaviampArtistAlbumCommand.StartRadio,
            NaviampAction.StartAlbumRadio.albumMediaCommandOrNull(),
        )
        assertEquals(
            NaviampArtistMediaCommand.StartRadio,
            NaviampAction.StartArtistRadio.artistMediaCommandOrNull(),
        )
        assertEquals(
            NaviampArtistAlbumCommand.ToggleFavorite,
            NaviampAction.ToggleFavorite.albumMediaCommandOrNull(),
        )
        assertNull(NaviampAction.DownloadAlbum.artistMediaCommandOrNull())
        assertNull(NaviampAction.StartArtistRadio.albumMediaCommandOrNull())
        assertNull(NaviampAction.AddToPlaylist.artistMediaCommandOrNull())
    }
}
