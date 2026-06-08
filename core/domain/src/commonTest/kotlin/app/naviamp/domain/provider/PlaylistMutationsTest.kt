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
import app.naviamp.domain.home.HomeContent
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
        assertEquals(
            listOf(recent, alpha),
            HomeContent().withPlaylists(listOf(beta, recent, alpha), recentPlaylistIds = listOf("recent"))
                .playlists
                .take(2),
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
        assertEquals(
            PlaylistDetailsOpenPlan(
                recentPlaylistIds = listOf("one", "two"),
                loadingStatus = "Loading Old...",
            ),
            playlistDetailsOpenPlan(current, recentPlaylistIds = listOf("two"), recentPlaylistLimit = 2),
        )
        assertEquals("Connected.", playlistDetailsLoadedStatus())
        assertEquals("Playlist failed to load.", playlistDetailsErrorMessage(Exception()))
        assertEquals(
            PlaylistDetailsApplicationUpdate(
                playlists = listOf(refreshed, other),
                selectedPlaylist = refreshed,
                selectedPlaylistTracks = tracks,
                playlistTracksById = mapOf("one" to tracks),
                selectedPlaylistChanged = true,
                status = "Connected.",
            ),
            playlistDetailsApplicationUpdate(
                currentSelectedPlaylist = current,
                currentSelectedPlaylistTracks = emptyList(),
                currentPlaylistTracksById = emptyMap(),
                refresh = refresh,
                requestedPlaylistId = "one",
                status = playlistDetailsLoadedStatus(),
            ),
        )
    }

    @Test
    fun refreshPlaylistDetailsApplicationLoadsAndPlansState() = kotlinx.coroutines.test.runTest {
        val stalePlaylist = playlist("one", "Old")
        val refreshedPlaylist = playlist("one", "New")
        val tracks = listOf(track("one"))
        val provider = FakePlaylistProvider(
            playlists = listOf(refreshedPlaylist),
            playlistTracks = tracks,
        )

        assertEquals(
            PlaylistDetailsApplicationUpdate(
                playlists = listOf(refreshedPlaylist.copy(trackCount = 1)),
                selectedPlaylist = refreshedPlaylist.copy(trackCount = 1),
                selectedPlaylistTracks = tracks,
                playlistTracksById = mapOf("one" to tracks),
                selectedPlaylistChanged = true,
                status = "Connected.",
            ),
            provider.refreshPlaylistDetailsApplication(
                playlist = stalePlaylist,
                currentSelectedPlaylist = stalePlaylist,
                currentSelectedPlaylistTracks = emptyList(),
                currentPlaylistTracksById = emptyMap(),
                status = playlistDetailsLoadedStatus(),
            ),
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
    fun playlistListRefreshPlansTrackPreloadsFromKnownTracks() {
        val one = playlist("one", "One")
        val two = playlist("two", "Two")
        val knownTracks = mapOf("one" to listOf(track("one")))

        assertEquals(
            PlaylistListRefresh(
                playlists = listOf(one, two),
                playlistsToPreload = listOf(two),
            ),
            playlistListRefresh(
                playlists = listOf(one, two),
                playlistTracksById = knownTracks,
            ),
        )
        assertEquals(
            PlaylistListStateUpdate(
                playlists = listOf(one, two),
                playlistsToPreload = listOf(two),
                status = null,
            ),
            playlistListStateUpdate(playlistListRefresh(listOf(one, two), knownTracks)),
        )
        assertEquals(listOf(two), playlistPreloadTargets(listOf(one, two), knownTracks))
        assertEquals("Loading playlists...", playlistListLoadingStatus())
        assertEquals("Could not load playlists.", playlistListErrorMessage(Exception()))
    }

    @Test
    fun refreshPlaylistsAndPlanPreloadLoadsPlaylistsAndPreloadTargets() = kotlinx.coroutines.test.runTest {
        val one = playlist("one", "One")
        val two = playlist("two", "Two")
        val provider = FakePlaylistProvider(playlists = listOf(one, two), playlistTracks = listOf(track("two")))

        val refresh = provider.refreshPlaylistsAndPlanPreload(
            playlistTracksById = mapOf("one" to listOf(track("one"))),
        )

        assertEquals(listOf(one, two), refresh.playlists)
        assertEquals(listOf(two), refresh.playlistsToPreload)
        assertEquals(listOf(track("two")), provider.loadPlaylistTracksForPreload(two))
        assertEquals(
            PlaylistListStateUpdate(
                playlists = listOf(one, two),
                playlistsToPreload = listOf(two),
                status = null,
            ),
            provider.refreshPlaylistListState(
                playlistTracksById = mapOf("one" to listOf(track("one"))),
            ),
        )
        assertEquals(
            PlaylistTrackPreloadStateUpdate(
                playlistTracksById = mapOf("one" to listOf(track("one")), "two" to listOf(track("two"))),
            ),
            provider.loadPlaylistTrackPreloadState(
                playlist = two,
                currentPlaylistTracksById = mapOf("one" to listOf(track("one"))),
            ),
        )
        assertEquals(
            PlaylistTrackPreloadStateUpdate(
                playlistTracksById = mapOf("one" to listOf(track("one")), "two" to listOf(track("two"))),
            ),
            provider.preloadPlaylistTracksStateUpdate(
                playlists = listOf(one, two),
                currentPlaylistTracksById = mapOf("one" to listOf(track("one"))),
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
    fun playlistPlaybackActionReportsLoadingAndSuppressesOverlappingStarts() {
        val playlist = playlist("one", "Road Mix")
        val play = playlistPlaybackAction(playlist, shuffle = false)
        val shuffle = playlistPlaybackAction(playlist, shuffle = true)

        assertEquals(PendingPlaybackAction("playlist:one:play", "Loading Road Mix..."), play)
        assertEquals(PendingPlaybackAction("playlist:one:shuffle", "Starting Road Mix in random order..."), shuffle)
        assertEquals(true, shouldStartPlaybackAction(null))
        assertEquals(false, shouldStartPlaybackAction(play))
        assertEquals(null, clearPendingPlaybackAction(play, play))
        assertEquals(play, clearPendingPlaybackAction(play, shuffle))
    }

    @Test
    fun playlistPlaybackPlansStartTrackLoadingAndReadyQueue() {
        val playlist = playlist("one", "Road Mix")
        val selectedTracks = listOf(track("selected"))
        val loadedTracks = listOf(track("loaded"))
        val pending = PendingPlaybackAction("other", "Already starting...")

        assertEquals(
            PlaylistPlaybackStartPlan(
                action = PendingPlaybackAction("playlist:one:play", "Loading Road Mix..."),
                shouldStart = true,
                status = "Loading Road Mix...",
            ),
            playlistPlaybackStartPlan(playlist, shuffle = false, pending = null),
        )
        assertEquals(
            PlaylistPlaybackStartPlan(
                action = PendingPlaybackAction("playlist:one:play", "Loading Road Mix..."),
                shouldStart = false,
                status = "Already starting...",
            ),
            playlistPlaybackStartPlan(playlist, shuffle = false, pending = pending),
        )
        assertEquals(
            PlaylistPlaybackTrackLoadPlan(shouldLoadTracks = false),
            playlistPlaybackTrackLoadPlan(playlist, selectedTracks, playlist),
        )
        assertEquals(
            PlaylistPlaybackTrackLoadPlan(shouldLoadTracks = true),
            playlistPlaybackTrackLoadPlan(null, emptyList(), playlist),
        )

        val selectedPlan = playlistPlaybackReadyPlan(
            playlist = playlist,
            shuffle = false,
            selectedPlaylist = playlist,
            selectedPlaylistTracks = selectedTracks,
            loadedTracks = loadedTracks,
            recentPlaylistIds = listOf("two", "one"),
            recentPlaylistLimit = 2,
        )
        assertEquals(selectedTracks, selectedPlan.tracks)
        assertEquals(track("selected"), selectedPlan.firstTrack)
        assertEquals(listOf("one", "two"), selectedPlan.recentPlaylistIds)

        val emptyPlan = playlistPlaybackReadyPlan(
            playlist = playlist,
            shuffle = false,
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
            loadedTracks = emptyList(),
            recentPlaylistIds = emptyList(),
            recentPlaylistLimit = 2,
            emptyStatus = "Road Mix did not return any tracks.",
        )
        assertEquals(null, emptyPlan.firstTrack)
        assertEquals("Road Mix did not return any tracks.", emptyPlan.emptyStatus)
    }

    @Test
    fun preparePlaylistPlaybackLoadsOrReusesTracks() = kotlinx.coroutines.test.runTest {
        val playlist = playlist("one", "Road Mix")
        val selectedTracks = listOf(track("selected"))
        val provider = FakePlaylistProvider(
            playlists = listOf(playlist),
            playlistTracks = listOf(track("loaded")),
        )

        val loaded = provider.preparePlaylistPlayback(
            playlist = playlist,
            shuffle = false,
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
            recentPlaylistIds = listOf("two"),
            recentPlaylistLimit = 2,
        )
        assertEquals(true, loaded.shouldStoreLoadedTracks)
        assertEquals(listOf(track("loaded")), loaded.loadedTracks)
        assertEquals(listOf(track("loaded")), loaded.readyPlan.tracks)
        assertEquals(listOf("one", "two"), loaded.readyPlan.recentPlaylistIds)

        val reused = provider.preparePlaylistPlayback(
            playlist = playlist,
            shuffle = false,
            selectedPlaylist = playlist,
            selectedPlaylistTracks = selectedTracks,
            recentPlaylistIds = emptyList(),
            recentPlaylistLimit = 2,
        )
        assertEquals(false, reused.shouldStoreLoadedTracks)
        assertEquals(emptyList(), reused.loadedTracks)
        assertEquals(selectedTracks, reused.readyPlan.tracks)
    }

    @Test
    fun playlistPlaybackApplicationUpdateStoresLoadedTracksAndReportsEmptyState() {
        val playlist = playlist("one", "Road Mix")
        val loadedTrack = track("loaded")
        val currentTracks = mapOf("other" to listOf(track("other")))

        assertEquals(
            PlaylistPlaybackApplicationUpdate(
                playlistTracksById = currentTracks + ("one" to listOf(loadedTrack)),
                loadedTracksToStore = listOf(loadedTrack),
                firstTrack = loadedTrack,
                playbackTracks = listOf(loadedTrack),
                recentPlaylistIds = listOf("one", "two"),
                status = null,
            ),
            playlistPlaybackApplicationUpdate(
                playlist = playlist,
                preparation = PlaylistPlaybackPreparation(
                    loadedTracks = listOf(loadedTrack),
                    shouldStoreLoadedTracks = true,
                    readyPlan = PlaylistPlaybackReadyPlan(
                        tracks = listOf(loadedTrack),
                        firstTrack = loadedTrack,
                        recentPlaylistIds = listOf("one", "two"),
                        emptyStatus = "Road Mix did not return any tracks.",
                    ),
                ),
                currentPlaylistTracksById = currentTracks,
            ),
        )

        assertEquals(
            PlaylistPlaybackApplicationUpdate(
                playlistTracksById = currentTracks,
                loadedTracksToStore = null,
                firstTrack = null,
                playbackTracks = emptyList(),
                recentPlaylistIds = emptyList(),
                status = "Road Mix did not return any tracks.",
            ),
            playlistPlaybackApplicationUpdate(
                playlist = playlist,
                preparation = PlaylistPlaybackPreparation(
                    loadedTracks = emptyList(),
                    shouldStoreLoadedTracks = false,
                    readyPlan = PlaylistPlaybackReadyPlan(
                        tracks = emptyList(),
                        firstTrack = null,
                        recentPlaylistIds = emptyList(),
                        emptyStatus = "Road Mix did not return any tracks.",
                    ),
                ),
                currentPlaylistTracksById = currentTracks,
            ),
        )
        assertEquals("Could not play Road Mix.", playlistPlaybackErrorMessage(Exception(), playlist))
    }

    @Test
    fun preparePlaylistPlaybackApplicationLoadsAndAppliesTrackMap() = kotlinx.coroutines.test.runTest {
        val playlist = playlist("one", "Road Mix")
        val provider = FakePlaylistProvider(
            playlists = listOf(playlist),
            playlistTracks = listOf(track("loaded")),
        )

        assertEquals(
            PlaylistPlaybackApplicationUpdate(
                playlistTracksById = mapOf("one" to listOf(track("loaded"))),
                loadedTracksToStore = listOf(track("loaded")),
                firstTrack = track("loaded"),
                playbackTracks = listOf(track("loaded")),
                recentPlaylistIds = listOf("one", "two"),
                status = null,
            ),
            provider.preparePlaylistPlaybackApplication(
                playlist = playlist,
                shuffle = false,
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
                recentPlaylistIds = listOf("two"),
                recentPlaylistLimit = 2,
                currentPlaylistTracksById = emptyMap(),
            ),
        )
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
        assertEquals("Adding track to playlist...", addToPlaylistLoadingStatus("track"))
        assertEquals("Loading tracks...", addToPlaylistResolvingTracksStatus())
        assertEquals("Could not add track to playlist.", addToPlaylistErrorMessage(Exception(), "track"))
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
        val refresh = AddToPlaylistRefresh(
            update = AddToPlaylistMutationUpdate(
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 2 tracks to playlist.",
                refreshPlaylists = true,
            ),
            playlists = listOf(playlist("one", "One")),
        )

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
        assertEquals(
            AddToPlaylistStateUpdate(
                playlists = listOf(playlist("one", "One")),
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 2 tracks to playlist.",
            ),
            addToPlaylistStateUpdate(refresh),
        )
    }

    @Test
    fun playlistRenameDeleteHelpersNormalizeStatusAndSelection() {
        val current = playlist("one", "Old")
        val renamed = playlist("one", "New")
        val renameRefresh = PlaylistRenameRefresh(
            requestedName = "New",
            playlists = listOf(renamed),
        )
        val deleteRefresh = PlaylistDeleteRefresh(playlists = listOf(playlist("two", "Two")))

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
        assertEquals(
            PlaylistRenameStateUpdate(
                playlists = listOf(renamed),
                selectedPlaylist = renamed,
                selectedPlaylistChanged = true,
                status = "Renamed playlist.",
            ),
            playlistRenameStateUpdate(current, renameRefresh, playlistId = "one"),
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
        assertEquals(
            PlaylistDeleteApplicationUpdate(
                playlists = listOf(playlist("two", "Two")),
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
                playlistTracksById = emptyMap(),
                recentPlaylistIds = listOf("two"),
                deletedSelectedPlaylist = true,
                status = "Deleted playlist.",
            ),
            playlistDeleteApplicationUpdate(
                refresh = deleteRefresh,
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
        val tracks = listOf(track("one"), track("two"))
        val refresh = PlaylistDetailsRefresh(
            playlists = listOf(playlist.copy(trackCount = 2)),
            displayPlaylist = playlist.copy(trackCount = 2),
            tracks = tracks,
        )

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
        assertEquals(
            SmartPlaylistMutationStateUpdate(
                playlists = refresh.playlists,
                displayPlaylist = playlist.copy(trackCount = 2),
                tracks = tracks,
                playlistTracksById = mapOf("smart" to tracks),
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
                selectedPlaylistChanged = false,
                status = "Saved smart playlist Smart with 2 tracks.",
            ),
            smartPlaylistSaveStateUpdate(refresh, currentPlaylistTracksById = emptyMap()),
        )
        assertEquals(
            SmartPlaylistMutationStateUpdate(
                playlists = refresh.playlists,
                displayPlaylist = playlist.copy(trackCount = 2),
                tracks = tracks,
                playlistTracksById = mapOf("smart" to tracks),
                selectedPlaylist = playlist.copy(trackCount = 2),
                selectedPlaylistTracks = tracks,
                selectedPlaylistChanged = true,
                status = "Updated smart playlist Smart with 2 tracks.",
            ),
            smartPlaylistUpdateStateUpdate(
                refresh = refresh,
                currentSelectedPlaylist = playlist,
                currentSelectedPlaylistTracks = emptyList(),
                currentPlaylistTracksById = emptyMap(),
            ),
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

    @Test
    fun createQueuePlaylistPreservesQueueOrderAndDuplicates() = kotlinx.coroutines.test.runTest {
        val tracks = listOf(track("one"), track("two"), track("one"))
        val provider = FakePlaylistProvider(playlists = emptyList(), playlistTracks = emptyList())

        val result = provider.createQueuePlaylist("  Queue Mix  ", tracks)

        assertEquals(playlist("created", "Queue Mix").copy(trackCount = 3), result.playlist)
        assertEquals(3, result.trackCount)
        assertEquals("Queue Mix", provider.createdPlaylistName)
        assertEquals(listOf(TrackId("one"), TrackId("two"), TrackId("one")), provider.createdPlaylistTrackIds)
    }

    @Test
    fun createQueuePlaylistRequiresNameAndTracks() = kotlinx.coroutines.test.runTest {
        val provider = FakePlaylistProvider(playlists = emptyList(), playlistTracks = emptyList())

        assertFailsWith<IllegalArgumentException> {
            provider.createQueuePlaylist(" ", listOf(track("one")))
        }
        assertFailsWith<IllegalArgumentException> {
            provider.createQueuePlaylist("Queue Mix", emptyList())
        }
    }

    @Test
    fun saveQueueAsPlaylistAndRefreshReturnsUpdatedPlaylists() = kotlinx.coroutines.test.runTest {
        val existingPlaylist = playlist("existing", "Existing")
        val provider = FakePlaylistProvider(playlists = listOf(existingPlaylist), playlistTracks = emptyList())

        val refresh = provider.saveQueueAsPlaylistAndRefresh("Queue Mix", listOf(track("one")))

        assertEquals(QueuePlaylistSaveResult(playlist("created", "Queue Mix").copy(trackCount = 1), 1), refresh.result)
        assertEquals(listOf(existingPlaylist, playlist("created", "Queue Mix").copy(trackCount = 1)), refresh.playlists)
        assertEquals(
            QueuePlaylistSaveStateUpdate(
                playlists = refresh.playlists,
                status = "Saved Queue Mix with 1 tracks.",
            ),
            queuePlaylistSaveStateUpdate(refresh),
        )
    }

    @Test
    fun renamePlaylistAndRefreshNormalizesNameAndReturnsUpdatedPlaylists() = kotlinx.coroutines.test.runTest {
        val existingPlaylist = playlist("existing", "Existing")
        val provider = FakePlaylistProvider(playlists = listOf(existingPlaylist), playlistTracks = emptyList())

        val refresh = provider.renamePlaylistAndRefresh(existingPlaylist, "  Renamed  ")

        assertEquals("Renamed", refresh.requestedName)
        assertEquals("existing", provider.renamedPlaylistId)
        assertEquals("Renamed", provider.renamedPlaylistName)
        assertEquals(listOf(playlist("existing", "Renamed")), refresh.playlists)
    }

    @Test
    fun deletePlaylistAndRefreshReturnsRemainingPlaylists() = kotlinx.coroutines.test.runTest {
        val deleted = playlist("deleted", "Deleted")
        val kept = playlist("kept", "Kept")
        val provider = FakePlaylistProvider(playlists = listOf(deleted, kept), playlistTracks = emptyList())

        val refresh = provider.deletePlaylistAndRefresh(deleted)

        assertEquals("deleted", provider.deletedPlaylistId)
        assertEquals(listOf(kept), refresh.playlists)
    }

    @Test
    fun addTracksToPlaylistAndRefreshCreatesPlaylistAndReturnsSharedUpdate() = kotlinx.coroutines.test.runTest {
        val existingPlaylist = playlist("existing", "Existing")
        val provider = FakePlaylistProvider(playlists = listOf(existingPlaylist), playlistTracks = emptyList())

        val refresh = provider.addTracksToPlaylistAndRefresh(
            playlistId = null,
            playlistName = null,
            newPlaylistName = "New Mix",
            tracks = listOf(track("one"), track("one")),
        )

        assertEquals("New Mix", provider.createdPlaylistName)
        assertEquals(listOf(TrackId("one")), provider.createdPlaylistTrackIds)
        assertEquals(
            AddToPlaylistMutationUpdate(
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 1 track to playlist.",
                refreshPlaylists = true,
            ),
            refresh.update,
        )
        assertEquals(listOf(existingPlaylist, playlist("created", "New Mix").copy(trackCount = 1)), refresh.playlists)

        assertEquals(
            AddToPlaylistStateUpdate(
                playlists = null,
                closeDialog = false,
                addToPlaylistStatus = "No tracks found.",
                connectionStatus = null,
            ),
            provider.addTracksToPlaylistStateUpdate(
                playlistId = null,
                playlistName = null,
                newPlaylistName = "Empty",
                tracks = emptyList(),
            ),
        )
    }

    @Test
    fun addTracksToPlaylistAndRefreshAddsMissingTracksToExistingPlaylist() = kotlinx.coroutines.test.runTest {
        val existingPlaylist = playlist("existing", "Existing")
        val provider = FakePlaylistProvider(
            playlists = listOf(existingPlaylist),
            playlistTracks = listOf(track("one")),
        )

        val refresh = provider.addTracksToPlaylistAndRefresh(
            playlistId = existingPlaylist.id,
            playlistName = existingPlaylist.name,
            newPlaylistName = null,
            tracks = listOf(track("one"), track("two")),
        )

        assertEquals(existingPlaylist.id, provider.addedPlaylistId)
        assertEquals(listOf(TrackId("two")), provider.addedTrackIds)
        assertEquals(
            AddToPlaylistMutationUpdate(
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 1 track to playlist.",
                refreshPlaylists = true,
            ),
            refresh.update,
        )
        assertEquals(listOf(existingPlaylist), refresh.playlists)
        assertEquals(
            AddToPlaylistStateUpdate(
                playlists = listOf(existingPlaylist),
                closeDialog = true,
                addToPlaylistStatus = null,
                connectionStatus = "Added 1 track to playlist.",
            ),
            provider.addTracksToPlaylistStateUpdate(
                playlistId = existingPlaylist.id,
                playlistName = existingPlaylist.name,
                newPlaylistName = null,
                tracks = listOf(track("one"), track("two"), track("two")),
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

        override suspend fun playlistTracks(playlistId: String): List<Track> =
            playlistTracks

        var createdPlaylistName: String? = null
            private set
        var createdPlaylistTrackIds: List<TrackId> = emptyList()
            private set
        var addedPlaylistId: String? = null
            private set
        var addedTrackIds: List<TrackId> = emptyList()
            private set
        var renamedPlaylistId: String? = null
            private set
        var renamedPlaylistName: String? = null
            private set
        var deletedPlaylistId: String? = null
            private set

        override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
            createdPlaylistName = name
            createdPlaylistTrackIds = trackIds
            return Playlist(id = "created", name = name, trackCount = trackIds.size)
        }

        override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
            addedPlaylistId = playlistId
            addedTrackIds = trackIds
        }

        override suspend fun renamePlaylist(playlistId: String, name: String) {
            renamedPlaylistId = playlistId
            renamedPlaylistName = name
        }

        override suspend fun deletePlaylist(playlistId: String) {
            deletedPlaylistId = playlistId
        }

        override suspend fun playlists(limit: Int): List<Playlist> =
            when {
                createdPlaylistName != null -> playlists + Playlist("created", createdPlaylistName.orEmpty(), createdPlaylistTrackIds.size)
                renamedPlaylistId != null -> playlists.map { playlist ->
                    if (playlist.id == renamedPlaylistId) playlist.copy(name = renamedPlaylistName.orEmpty()) else playlist
                }
                deletedPlaylistId != null -> playlists.filterNot { it.id == deletedPlaylistId }
                else -> playlists
            }

        override suspend fun streamUrl(request: StreamRequest): String =
            ""

        override fun coverArtUrl(coverArtId: String): String =
            coverArtId
    }
}
