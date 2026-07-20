package app.naviamp.android.playback

import app.naviamp.domain.playback.CatalogPlaybackIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidAutoMediaIdParserTest {
    private val decode: (String) -> String = { value ->
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    @Test
    fun parsesStableIdsIntoPortableSelections() {
        assertEquals(CatalogPlaybackIntent.LibraryRadio, AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdRadioLibrary, decode))
        assertEquals(
            CatalogPlaybackIntent.Playlist("road trip", shuffle = true),
            AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix + "road%20trip", decode),
        )
        assertEquals(
            CatalogPlaybackIntent.Artist("artist-1", "RÜFÜS DU SOL", shuffle = false),
            AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix + "artist-1|R%C3%9CF%C3%9CS%20DU%20SOL", decode),
        )
    }

    @Test
    fun rejectsIncompletePlayableIds() {
        assertNull(AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix + "playlist-only", decode))
        assertNull(AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdRadioStationPrefix + "station|Name||", decode))
        assertNull(AndroidAutoMediaIdParser.parse(AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix, decode))
    }
}
