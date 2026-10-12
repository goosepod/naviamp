package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampPlaylistTrackMenuTest {
    @Test
    fun editableActionsFollowPlaylistPriorityAndRespectBoundaries() {
        val actions = playlistTrackEditActions(true, 0, 3)
        assertEquals(PlaylistTrackEditAction.entries, actions.map { it.action })
        assertFalse(actions.first().enabled)
        assertTrue(actions.drop(1).all { it.enabled })
        assertFalse(playlistTrackEditActions(true, 2, 3)[1].enabled)
        assertFalse(playlistTrackEditActions(true, 0, 1)[2].enabled)
        assertTrue(playlistTrackEditActions(true, 1, 3, busy = true).none { it.enabled })
    }

    @Test
    fun readOnlyAndMissingOccurrencesHaveNoEditingActions() {
        assertTrue(playlistTrackEditActions(false, 0, 3).isEmpty())
        assertTrue(playlistTrackEditActions(true, -1, 3).isEmpty())
        assertTrue(playlistTrackEditActions(true, 3, 3).isEmpty())
    }

    @Test
    fun copyPrecedesPlaybackAndSongCapabilitiesAreRespected() {
        val track = SharedTrackRowUi("song", "Song", "Artist", hasAlbum = true, hasArtist = true,
            detailSections = listOf(NaviampDetailSectionUi("Metadata", emptyList())))
        val actions = playlistTrackSongActions(track, PlaylistTrackCapabilities(true, true, true, true)).map { it.action }
        assertEquals(listOf(NaviampAction.AddToPlaylist, NaviampAction.PlayNextTrack, NaviampAction.PlayNext,
            NaviampAction.AddToQueue, NaviampAction.StartTrackRadio, NaviampAction.ToggleFavorite,
            NaviampAction.DownloadTrack, NaviampAction.GoToAlbum, NaviampAction.GoToArtist,
            NaviampAction.TrackDetails), actions)
        assertEquals(listOf(NaviampAction.ToggleFavorite, NaviampAction.GoToAlbum, NaviampAction.GoToArtist,
            NaviampAction.TrackDetails), playlistTrackSongActions(track, PlaylistTrackCapabilities()).map { it.action })
        assertTrue(playlistTrackSongActions(SharedTrackRowUi("song", "Song", "", canToggleFavorite = false),
            PlaylistTrackCapabilities()).isEmpty())
    }

    @Test
    fun positionsAreValidatedAndDuplicateOccurrencesRemainDistinct() {
        val tracks = listOf("duplicate-first", "other", "duplicate-second")
        assertEquals(listOf("duplicate-second", "duplicate-first", "other"), movePlaylistTrackToPosition(tracks, 2, 1))
        assertEquals(listOf("other", "duplicate-second", "duplicate-first"), movePlaylistTrackToPosition(tracks, 0, 3))
        for (position in listOf(0, -1, 4, Int.MAX_VALUE)) assertEquals(tracks, movePlaylistTrackToPosition(tracks, 1, position))
        assertEquals(tracks, movePlaylistTrackToPosition(tracks, -1, 2))
        val duplicates = listOf("same", "other", "same")
        assertEquals(listOf("same", "other"), applyPlaylistEditTrackAction(duplicates, 2, app.naviamp.domain.settings.TrackSwipeAction.Remove))
    }
}
