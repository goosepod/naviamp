package app.naviamp.domain.provider

import app.naviamp.domain.TrackId
import app.naviamp.domain.Playlist
import app.naviamp.domain.smartplaylist.SmartPlaylistTemplates
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistMutationsTest {
    @Test
    fun addToPlaylistMutationUpdateReportsNoTracks() {
        assertEquals(
            AddToPlaylistMutationUpdate(
                closeDialog = false,
                addToPlaylistStatus = "No tracks found.",
                connectionStatus = null,
                refreshPlaylists = false,
            ),
            addToPlaylistMutationUpdate(
                result = PlaylistTrackMutationResult(
                    requestedTrackCount = 0,
                    addedTrackIds = emptyList(),
                    createdPlaylist = false,
                ),
                playlistName = null,
            ),
        )
    }

    @Test
    fun addToPlaylistMutationUpdateReportsExistingTracks() {
        assertEquals(
            AddToPlaylistMutationUpdate(
                closeDialog = false,
                addToPlaylistStatus = "Everything is already in Playlist.",
                connectionStatus = null,
                refreshPlaylists = false,
            ),
            addToPlaylistMutationUpdate(
                result = PlaylistTrackMutationResult(
                    requestedTrackCount = 2,
                    addedTrackIds = emptyList(),
                    createdPlaylist = false,
                ),
                playlistName = "Playlist",
            ),
        )
    }

    @Test
    fun addToPlaylistMutationUpdateClosesDialogWhenTracksAdded() {
        assertEquals(
            AddToPlaylistMutationUpdate(
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 2 tracks to playlist.",
                refreshPlaylists = true,
            ),
            addToPlaylistMutationUpdate(
                result = PlaylistTrackMutationResult(
                    requestedTrackCount = 2,
                    addedTrackIds = listOf(TrackId("one"), TrackId("two")),
                    createdPlaylist = false,
                ),
                playlistName = "Playlist",
            ),
        )
    }

    @Test
    fun playlistRenameDeleteHelpersNormalizeStatusAndSelection() {
        val current = playlist("one", "Old")
        val renamed = playlist("one", "New")

        assertEquals("New", normalizedPlaylistName("  New  "))
        assertEquals("Renaming Old...", playlistRenameLoadingStatus(current))
        assertEquals("Deleting Old...", playlistDeleteLoadingStatus(current))
        assertEquals("Renamed playlist.", playlistRenamedStatus())
        assertEquals("Deleted playlist.", playlistDeletedStatus())
        assertEquals(renamed, renamedSelectedPlaylist(current, "one", "Fallback", listOf(renamed)))
        assertEquals(
            playlist("one", "Fallback"),
            renamedSelectedPlaylist(current, "one", "Fallback", emptyList()),
        )
        assertEquals(null, selectedPlaylistAfterDelete(current, "one"))
        assertEquals(listOf("two"), recentPlaylistIdsAfterDelete(listOf("one", "two"), "one"))
    }

    @Test
    fun smartPlaylistStatusMessagesIncludeTrackCount() {
        val playlist = playlist("smart", "Smart")
        val definition = SmartPlaylistTemplates.favorites().copy(name = "Smart")

        assertEquals("Saving Smart...", smartPlaylistSavingStatus(definition))
        assertEquals("Updating Smart...", smartPlaylistUpdatingStatus(definition))
        assertEquals("Loading Smart rules...", smartPlaylistLoadingRulesStatus(playlist))
        assertEquals(
            "Saved smart playlist Smart with 3 tracks.",
            smartPlaylistSavedStatus(playlist, trackCount = 3),
        )
        assertEquals(
            "Updated smart playlist Smart with 4 tracks.",
            smartPlaylistUpdatedStatus(playlist, trackCount = 4),
        )
    }

    @Test
    fun smartPlaylistSaveErrorExplainsMissingPasswordRecovery() {
        assertEquals(
            "Edit this saved connection, enter your Navidrome password, then Save and connect before saving smart playlists.",
            smartPlaylistSaveErrorMessage(
                IllegalStateException("Reconnect to Navidrome with your password before saving smart playlists."),
            ),
        )
    }

    private fun playlist(id: String, name: String): Playlist =
        Playlist(
            id = id,
            name = name,
            trackCount = 0,
        )
}
