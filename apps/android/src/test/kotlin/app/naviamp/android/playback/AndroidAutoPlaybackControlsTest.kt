package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidAutoPlaybackControlsTest {
    @Test
    fun detailContainersRemainNonPlayable() {
        val containerIds = listOf(
            "${AndroidAutoPlaybackControls.MediaIdArtistPrefix}artist",
            "${AndroidAutoPlaybackControls.MediaIdAlbumPrefix}album",
            "${AndroidAutoPlaybackControls.MediaIdPlaylistPrefix}playlist",
        )

        containerIds.forEach { mediaId ->
            assertTrue(
                AndroidAutoPlaybackControls.isNonPlayableMediaId(mediaId),
                "Expected non-playable detail container media ID: $mediaId",
            )
        }
    }
}
