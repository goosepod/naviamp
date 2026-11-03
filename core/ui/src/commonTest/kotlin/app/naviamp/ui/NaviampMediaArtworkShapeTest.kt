package app.naviamp.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampMediaArtworkShapeTest {
    @Test fun filledArtworkHonorsLyricsVisualizerAndDefaultPresentation() {
        assertEquals(false, useFilledNowPlayingArtwork(false, false, false, true))
        assertEquals(false, useFilledNowPlayingArtwork(true, true, false, true))
        assertEquals(false, useFilledNowPlayingArtwork(true, false, true, false))
        assertEquals(false, useFilledNowPlayingArtwork(true, false, true, true))
        assertEquals(false, useFilledNowPlayingArtwork(true, false, false, true))
        assertEquals(false, useFilledNowPlayingArtwork(true, false, false, false, NaviampPlayerPanelLayout.Standalone))
        assertEquals(false, useFilledNowPlayingArtwork(true, false, false, false, NaviampPlayerPanelLayout.Full))
        assertEquals(true, useFilledNowPlayingArtwork(true, false, false, false))
    }
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
