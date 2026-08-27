package app.naviamp.ui

import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampTelevisionDetailPolicyTest {
    @Test
    fun trackSecondaryActionsStaySmallAndExcludePlaylistManagement() {
        val actions = televisionTrackSecondaryActions()

        assertEquals(
            listOf(
                SharedTrackRowAction.PlayNext,
                SharedTrackRowAction.AddToQueue,
                SharedTrackRowAction.StartRadio,
            ),
            actions,
        )
        assertFalse(SharedTrackRowAction.AddToPlaylist in actions)
        assertFalse(SharedTrackRowAction.CreatePlaylistAndAdd in actions)
    }

    @Test
    fun trackSecondaryActionLabelsAreRemoteFriendly() {
        assertEquals("Play Next", televisionTrackSecondaryActionLabel(SharedTrackRowAction.PlayNext))
        assertEquals("Add to Queue", televisionTrackSecondaryActionLabel(SharedTrackRowAction.AddToQueue))
        assertEquals("Start Radio", televisionTrackSecondaryActionLabel(SharedTrackRowAction.StartRadio))
    }

    @Test
    fun detailCountsUseNaturalSingularAndPluralLabels() {
        assertEquals("1 track", televisionTrackCountLabel(1))
        assertEquals("2 tracks", televisionTrackCountLabel(2))
        assertEquals("1 release", televisionReleaseCountLabel(1))
        assertEquals("6 releases", televisionReleaseCountLabel(6))
    }

    @Test
    fun playlistShuffleRequiresMoreThanOneTrack() {
        assertFalse(televisionPlaylistSupportsShuffle(0))
        assertFalse(televisionPlaylistSupportsShuffle(1))
        assertTrue(televisionPlaylistSupportsShuffle(2))
    }

    @Test
    fun artistAlbumSubtitleHonorsAlbumYearSetting() {
        val album = SharedMediaItemUi(
            id = "album",
            title = "Signals",
            subtitle = "Rush",
            releaseYear = 1982,
        )

        assertEquals("Rush  •  1982", televisionAlbumSubtitle(album, showAlbumYear = true))
        assertEquals("Rush", televisionAlbumSubtitle(album, showAlbumYear = false))
    }

    @Test
    fun preparedNowPlayingAlbumLineHonorsAlbumYearSetting() {
        val nowPlaying = NowPlayingUi(
            title = "Subdivisions",
            subtitle = "Rush",
            stateLabel = "Playing",
            albumTitle = "Signals",
            albumYear = 1982,
            albumReleaseYear = 1982,
        )

        assertEquals(
            "Signals (1982)",
            nowPlaying.withDisplaySettings(NowPlayingDisplaySettings(showAlbumYear = true)).albumLine,
        )
        assertEquals(
            "Signals",
            nowPlaying.withDisplaySettings(NowPlayingDisplaySettings(showAlbumYear = false)).albumLine,
        )
    }

    @Test
    fun trackActionPanelShowsAvailableMediaContextInReadingOrder() {
        val track = SharedTrackRowUi(
            id = "track",
            title = "Subdivisions",
            subtitle = "Rush - Signals",
            artistCredits = listOf(SharedArtistCreditUi("rush", "Rush")),
            albumTitle = "Signals",
        )

        assertEquals(
            listOf("Artist" to "Rush", "Album" to "Signals", "Track" to "Subdivisions"),
            televisionTrackActionContextLines(track),
        )
        assertEquals(
            listOf("Artist" to "Fallback Artist", "Album" to "Fallback Album", "Track" to "Subdivisions"),
            televisionTrackActionContextLines(track, "Fallback Artist", "Fallback Album"),
        )
    }
}
