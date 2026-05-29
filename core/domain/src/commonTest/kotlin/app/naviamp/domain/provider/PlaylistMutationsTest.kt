package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.smartplaylist.SmartPlaylistTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaylistMutationsTest {
    @Test
    fun homePlaylistsPrioritizesRecentThenName() {
        val alpha = playlist("alpha", "Alpha")
        val beta = playlist("beta", "Beta")
        val recent = playlist("recent", "Recent")

        assertEquals(
            listOf(recent, alpha, beta),
            homePlaylists(
                playlists = listOf(beta, recent, alpha),
                recentPlaylistIds = listOf("recent"),
            ),
        )
    }

    @Test
    fun queueAppendPlanReportsEmptyAddedAndAlreadyQueuedTracks() {
        val currentTrack = track("one")
        val nextTrack = track("two")

        assertEquals(
            QueueAppendPlan(tracks = emptyList(), status = "No tracks found."),
            queueAppendPlan(emptyList()),
        )
        assertEquals(
            QueueAppendPlan(tracks = listOf(nextTrack), status = "Added 1 track to queue."),
            queueAppendPlan(listOf(nextTrack), label = "track"),
        )
        assertEquals(
            QueueAppendPlan(tracks = emptyList(), status = "Popular tracks are already in the queue."),
            queueAppendPlan(
                tracks = listOf(currentTrack),
                label = "popular tracks",
                existingTracks = listOf(currentTrack),
                deduplicateExisting = true,
            ),
        )
    }

    @Test
    fun refreshPlaylistDetailsUsesFreshTrackCountAndPlaylistList() = kotlinx.coroutines.test.runTest {
        val stalePlaylist = playlist("one", "Old Name")
        val refreshedPlaylist = playlist("one", "New Name")
        val otherPlaylist = playlist("two", "Other")
        val tracks = listOf(track("one"), track("two"))
        val provider = FakePlaylistProvider(
            playlists = listOf(refreshedPlaylist, otherPlaylist),
            playlistTracks = tracks,
        )

        assertEquals(
            PlaylistDetailsRefresh(
                playlists = listOf(refreshedPlaylist.copy(trackCount = 2), otherPlaylist),
                displayPlaylist = refreshedPlaylist.copy(trackCount = 2),
                tracks = tracks,
            ),
            provider.refreshPlaylistDetails(stalePlaylist),
        )
    }

    @Test
    fun playlistDetailsStateUpdateOnlyReplacesMatchingSelection() {
        val current = playlist("one", "Old")
        val other = playlist("two", "Other")
        val refreshed = playlist("one", "New")
        val tracks = listOf(track("one"))
        val refresh = PlaylistDetailsRefresh(
            playlists = listOf(refreshed, other),
            displayPlaylist = refreshed,
            tracks = tracks,
        )

        assertEquals(
            PlaylistDetailsStateUpdate(
                playlists = listOf(refreshed, other),
                selectedPlaylist = refreshed,
                selectedPlaylistTracks = tracks,
                playlistTracksById = mapOf("one" to tracks),
            ),
            playlistDetailsStateUpdate(
                currentSelectedPlaylist = current,
                currentSelectedPlaylistTracks = emptyList(),
                currentPlaylistTracksById = emptyMap(),
                refresh = refresh,
                requestedPlaylistId = "one",
            ),
        )

        assertEquals(
            other,
            playlistDetailsStateUpdate(
                currentSelectedPlaylist = other,
                currentSelectedPlaylistTracks = emptyList(),
                currentPlaylistTracksById = emptyMap(),
                refresh = refresh,
                requestedPlaylistId = "one",
            ).selectedPlaylist,
        )
    }

    @Test
    fun playlistListHelpersPlanRecentAndPreloadUpdates() {
        val one = playlist("one", "One")
        val two = playlist("two", "Two")

        assertEquals(
            listOf("one", "two"),
            recentPlaylistIdsAfterPlayed(listOf("two", "one", "three"), "one", limit = 2),
        )
        assertEquals(
            listOf(two),
            playlistsNeedingTrackPreload(
                playlists = listOf(one, two),
                playlistTracksById = mapOf("one" to listOf(track("one"))),
            ),
        )
    }

    @Test
    fun playlistDetailAutoRefreshTargetRequiresProviderPlaylistAndEnabledState() {
        val provider = Any()
        val playlist = playlist("one", "One")

        assertEquals(
            PlaylistDetailAutoRefreshTarget(provider, playlist),
            playlistDetailAutoRefreshTarget(provider, playlist),
        )
        assertEquals(null, playlistDetailAutoRefreshTarget(null, playlist))
        assertEquals(null, playlistDetailAutoRefreshTarget(provider, null))
        assertEquals(null, playlistDetailAutoRefreshTarget(provider, playlist, enabled = false))
    }

    @Test
    fun playlistDetailAutoRefreshRunsAfterWaitAndContinuesAfterRefreshFailure() = kotlinx.coroutines.test.runTest {
        val provider = Any()
        val playlist = playlist("one", "One")
        val target = PlaylistDetailAutoRefreshTarget(provider, playlist)
        var waits = 0
        var refreshes = 0

        assertFailsWith<StopAutoRefresh> {
            runPlaylistDetailAutoRefresh(
                target = target,
                waitForNextRefresh = {
                    waits += 1
                    if (waits > 2) throw StopAutoRefresh()
                },
                refresh = { activeProvider, activePlaylist ->
                    assertEquals(provider, activeProvider)
                    assertEquals(playlist, activePlaylist)
                    refreshes += 1
                    if (refreshes == 1) error("Transient refresh failure")
                },
            )
        }
        assertEquals(3, waits)
        assertEquals(2, refreshes)
    }

    @Test
    fun playlistPlaybackHelpersReuseLoadedSelectionAndShuffleOnRequest() {
        val selected = playlist("one", "One")
        val selectedTracks = listOf(track("one"))
        val loadedTracks = listOf(track("two"))

        assertEquals(
            selectedTracks,
            selectedPlaylistTracksForPlayback(
                selectedPlaylist = selected,
                selectedPlaylistTracks = selectedTracks,
                playlist = selected,
                loadedTracks = loadedTracks,
            ),
        )
        assertEquals(loadedTracks, playlistPlaybackTracks(loadedTracks, shuffle = false))
        assertEquals(loadedTracks.toSet(), playlistPlaybackTracks(loadedTracks, shuffle = true).toSet())
    }

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
        assertEquals(
            PlaylistDeleteStateUpdate(
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
                playlistTracksById = emptyMap(),
                recentPlaylistIds = listOf("two"),
                deletedSelectedPlaylist = true,
            ),
            playlistDeleteStateUpdate(
                currentSelectedPlaylist = current,
                currentSelectedPlaylistTracks = listOf(track("one")),
                currentPlaylistTracksById = mapOf("one" to listOf(track("one"))),
                currentRecentPlaylistIds = listOf("one", "two"),
                deletedPlaylistId = "one",
            ),
        )
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

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )

    private class StopAutoRefresh : RuntimeException()

    private class FakePlaylistProvider(
        private val playlists: List<Playlist>,
        private val playlistTracks: List<Track>,
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            emptyList()

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            error("Not used")

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            error("Not used")

        override suspend fun artists(limit: Int): List<Artist> =
            emptyList()

        override suspend fun tracks(limit: Int): List<Track> =
            emptyList()

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            MediaSearchResults()

        override suspend fun playlists(limit: Int): List<Playlist> =
            playlists

        override suspend fun playlistTracks(playlistId: String): List<Track> =
            playlistTracks

        override suspend fun streamUrl(request: StreamRequest): String =
            ""

        override fun coverArtUrl(coverArtId: String): String =
            coverArtId
    }
}
