package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAutoPlaybackControlsTest {
    @Test
    fun detailPlaybackActionsAreNotClassifiedAsContainers() {
        val playableIds = listOf(
            "${AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix}artist",
            "${AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix}artist|track",
            "${AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix}artist",
            "${AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix}album",
            "${AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix}album|track",
            "${AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix}album",
            "${AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix}playlist",
            "${AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix}playlist|track",
            "${AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix}playlist",
        )

        playableIds.forEach { mediaId ->
            assertFalse(
                AndroidAutoPlaybackControls.isNonPlayableMediaId(mediaId),
                "Expected playable detail media ID: $mediaId",
            )
        }
    }

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
