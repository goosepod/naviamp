package app.naviamp.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistAlbumGridLayoutTest {
    @Test
    fun narrowGalleryRetainsTwoColumns() {
        val availableWidth = 280.dp
        val tileSize = artistAlbumGridTileSize(availableWidth)

        assertEquals(137.dp, tileSize)
        assertEquals(availableWidth, tileSize * 2 + 6.dp)
    }

    @Test
    fun wideGalleryKeepsPreferredTileSize() {
        assertEquals(144.dp, artistAlbumGridTileSize(600.dp))
    }
}
