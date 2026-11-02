package app.naviamp.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampMediaArtworkShapeTest {
    @Test fun squareAlbumPreferencePreservesArtistAndCollectionShapes() {
        for (square in listOf(false, true)) {
            for (kind in SharedMediaItemKind.entries) {
                val expected = when {
                    kind == SharedMediaItemKind.Artist -> 40.dp
                    square && (kind == SharedMediaItemKind.Album || kind == SharedMediaItemKind.Track) -> 0.dp
                    else -> 8.dp
                }
                assertEquals(expected, mediaArtworkCornerRadius(kind, 80.dp, 8.dp, square), "$kind / $square")
            }
        }
    }
}
