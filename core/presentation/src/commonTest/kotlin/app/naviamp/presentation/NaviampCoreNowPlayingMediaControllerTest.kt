package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingItemTarget
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreNowPlayingMediaControllerTest {
    @Test
    fun collapseClosesNowPlayingSynchronously() = runTest {
        val fixture = mediaFixture(this)
        fixture.store.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = true))
        }

        val result = fixture.controller.dispatch(displayCommand(NowPlayingDisplayAction.Collapse))

        assertTrue(result is NaviampCoreImmediateCommandResult.Handled)
        assertFalse(fixture.store.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun libraryAndAppearanceMenusOpenMembershipWithoutPlayback() = runTest {
        val fixture = mediaFixture(this)
        fixture.store.updateShell { it.copy(nowPlaying = null) }
        val track = nowPlayingTrack("current")
        val registry = NaviampCoreMediaRegistry()
        registry.updateLibraryTracks(listOf(track), true)
        registry.updateArtist(ArtistDetails(Artist(ArtistId("artist"), "Artist", null), emptyList()),
            appearanceTracks = listOf(track))
        val coordinator = NaviampCorePlaylistMembershipCoordinator(
            { fixture.provider }, { fixture.store.state.value.shell.playlistMembership },
            { editor -> fixture.store.updateShell { it.copy(playlistMembership = editor) } },
        )
        val controller = NaviampCoreTrackActionController(registry, fixture.transactions, coordinator::open)
        val request = app.naviamp.ui.SharedTrackRowActionRequest(
            app.naviamp.ui.SharedTrackRowUi("current", "Current", "Artist"),
            app.naviamp.ui.SharedTrackRowAction.AddToPlaylist,
        )
        for (command in listOf(NaviampCoreCommand.Library.TrackAction(request),
            NaviampCoreCommand.Detail.ArtistPopularTrack(request))) {
            controller.execute(command)
            assertEquals("current", fixture.store.state.value.shell.playlistMembership?.trackId)
            assertNull(fixture.store.state.value.shell.nowPlaying)
            coordinator.dismiss()
            assertNull(fixture.store.state.value.shell.playlistMembership)
        }
    }

    @Test
    fun dismissingLoadingEditorRejectsItsLateResponse() = runTest {
        val base = NowPlayingTestProvider()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val provider = object : MediaProvider by base {
            override suspend fun playlists(limit: Int): List<Playlist> { gate.await(); return base.playlists(limit) }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it })
        val opening = launch { coordinator.open(nowPlayingTrack("current")) }
        runCurrent()
        assertTrue(editor?.loading == true)
        coordinator.dismiss()
        gate.complete(Unit)
        opening.join()
        assertNull(editor)
    }

    @Test
    fun favoriteUpdatesReachNewLibraryAndAppearanceRegistrySurfaces() {
        val registry = NaviampCoreMediaRegistry()
        val album = Album(AlbumId("album"), "Album", "Artist", null, null)
        val artist = Artist(ArtistId("artist"), "Artist", "before")
        val track = nowPlayingTrack("appearance")
        registry.updateLibraryAlbums(listOf(album), true)
        registry.updateHome(app.naviamp.domain.home.HomeContent(favoriteArtists = listOf(artist)), app.naviamp.domain.sonichome.SonicHomeDiscoveryRows())
        registry.updateArtist(ArtistDetails(artist, emptyList()), appearanceAlbums = listOf(album), appearanceTracks = listOf(track))
        registry.updateAlbum(album.copy(favoritedAtIso8601 = "now"))
        registry.updateTrack(track.copy(favoritedAtIso8601 = "now"))
        registry.updateArtist(artist.copy(favoritedAtIso8601 = null))
        assertEquals("now", registry.libraryAlbums.single().favoritedAtIso8601)
        assertEquals("now", registry.artistAppearanceAlbums.single().favoritedAtIso8601)
        assertEquals("now", registry.artistAppearanceTracks.single().favoritedAtIso8601)
        assertNull(registry.home.favoriteArtists.single().favoritedAtIso8601)
    }

    @Test
    fun membershipCreationUsesTheEditorsTrackAndPreservesPendingSelections() = runTest {
        val base = NowPlayingTestProvider()
        var createdTrack: TrackId? = null
        val provider = object : MediaProvider by base {
            override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
                createdTrack = trackIds.single()
                return Playlist("new", name, 1)
            }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        var published: Playlist? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it },
            onPlaylistCreated = { published = it })
        coordinator.open(nowPlayingTrack("next"))
        coordinator.toggle("playlist")
        coordinator.create("New playlist")
        assertEquals("new", published?.id)
        assertEquals(TrackId("next"), createdTrack)
        assertFalse(editor!!.rows.first().selected)
        assertTrue(editor!!.rows.last().selected)
        assertFalse(editor!!.saving)
    }

    @Test
    fun cancellingMembershipReadsReleasesLoadingAndDoesNotSwallowCancellation() = runTest {
        val base = NowPlayingTestProvider()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val provider = object : MediaProvider by base {
            override suspend fun playlists(limit: Int): List<Playlist> {
                entered.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it })
        val job = launch { coordinator.open(nowPlayingTrack("current")) }
        entered.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(editor!!.loading)
        assertTrue(editor!!.loadingFailed)
        coordinator.dismiss()
        assertNull(editor)
    }

    @Test
    fun membershipDiscoveryFailureIsRetryableAndSmartPlaylistsAreReadOnly() = runTest {
        val base = NowPlayingTestProvider()
        var failing = true
        var mutations = 0
        val provider = object : MediaProvider by base {
            override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: TrackId) { mutations++ }
            override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) { mutations++ }
            override suspend fun playlists(limit: Int): List<Playlist> {
                if (failing) error("offline")
                return listOf(Playlist("smart", "Smart", 0, isSmart = true), Playlist("playlist", "Playlist", 4), Playlist("deleted", "Deleted", 0))
            }
            override suspend fun playlistTracks(playlistId: String): List<Track> {
                if (playlistId == "deleted") throw NoSuchElementException("deleted")
                return base.playlistTracks(if (playlistId == "smart") "playlist" else playlistId)
            }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it })
        coordinator.open(nowPlayingTrack("current"))
        assertTrue(editor!!.loadingFailed)
        assertFalse(editor!!.loading)
        failing = false
        coordinator.retry()
        assertFalse(editor!!.loadingFailed)
        assertEquals(listOf("smart", "playlist", "deleted"), editor!!.rows.map { it.playlist.id })
        assertTrue(editor!!.rows.first().selected)
        assertTrue(editor!!.rows.last().failed)
        assertTrue(editor!!.rows.first().ruleBased)
        coordinator.toggle("smart")
        assertTrue(editor!!.rows.first().selected)
        // Guard the transaction as well as the UI, even if a caller supplies an invalid edit.
        editor = editor!!.copy(rows = editor!!.rows.map { if (it.ruleBased) it.copy(selected = false) else it })
        coordinator.apply()
        assertEquals(0, mutations)
    }

    @Test
    fun membershipPartialFailureRetainsSuccessfulChangesAndAllowsRetry() = runTest {
        val base = NowPlayingTestProvider()
        val provider = object : MediaProvider by base {
            override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
                error("write rejected")
            }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val reconciled = mutableMapOf<String, List<Track>>()
        val coordinator = NaviampCorePlaylistMembershipCoordinator(
            { provider }, { editor }, { editor = it },
            onContentsReconciled = { id, tracks -> reconciled[id] = tracks },
        )
        coordinator.open(nowPlayingTrack("current"))
        coordinator.toggle("playlist")
        coordinator.toggle("playlist-2")
        coordinator.apply()
        assertFalse(editor!!.rows.first().selected)
        assertFalse(editor!!.rows.first().failed)
        assertTrue(editor!!.rows.last().failed)
        assertFalse(editor!!.saving)
        assertFalse(editor!!.saved)
        assertEquals(setOf("playlist", "playlist-2"), reconciled.keys)
        assertFalse(reconciled.getValue("playlist").any { it.id.value == "current" })
        coordinator.retry()
        assertFalse(editor!!.rows.last().failed)
        assertFalse(editor!!.rows.first().selected)
    }

    @Test
    fun membershipSourceChangeStopsBeforeTheNextMutationAndSaveCannotBeDismissed() = runTest {
        val base = NowPlayingTestProvider()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        var saving = false
        val provider = object : MediaProvider by base {
            override suspend fun playlistTracks(playlistId: String): List<Track> {
                if (saving) { entered.complete(Unit); gate.await() }
                return base.playlistTracks(playlistId)
            }
        }
        var active: MediaProvider = provider
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ active }, { editor }, { editor = it })
        coordinator.open(nowPlayingTrack("current"))
        coordinator.toggle("playlist-2")
        saving = true
        val job = launch { coordinator.apply() }
        entered.await()
        coordinator.dismiss()
        assertTrue(editor!!.saving)
        active = object : MediaProvider by base { override val cacheNamespace = "new-source" }
        editor = null // The shared source lifecycle clears the playback display.
        gate.complete(Unit)
        job.join()
        assertTrue(base.added.isEmpty())
        assertNull(editor)
    }

    @Test
    fun membershipLostWriteResponseAndOfflineReconciliationRetryWithoutDuplicates() = runTest {
        for (committed in listOf(false, true)) {
            val base = NowPlayingTestProvider()
            var offline = false
            var fail = true
            val provider = object : MediaProvider by base {
                override suspend fun playlistTracks(playlistId: String): List<Track> {
                    if (offline) error("Offline")
                    return base.playlistTracks(playlistId)
                }
                override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
                    if (!fail || committed) base.addTracksToPlaylist(playlistId, trackIds)
                    if (fail) { offline = true; error("Connection lost") }
                }
            }
            var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
            val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it })
            coordinator.open(nowPlayingTrack("current"))
            coordinator.toggle("playlist-2")
            coordinator.apply()
            assertFalse(editor!!.saved)
            assertFalse(editor!!.saving)
            assertTrue(editor!!.rows.last().failed)
            offline = false
            fail = false
            coordinator.retry()
            if (!editor!!.rows.last().selected) coordinator.toggle("playlist-2")
            coordinator.apply()
            assertTrue(editor!!.saved)
            assertEquals(1, base.playlistTracks("playlist-2").count { it.id.value == "current" })
            assertEquals(1, base.added.size)
        }
    }

    @Test
    fun membershipCompletionAfterSwitchDoesNotTouchNewEditorOrProvider() = runTest {
        for (fails in listOf(false, true)) {
            val base = NowPlayingTestProvider()
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val old = object : MediaProvider by base {
                override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
                    base.addTracksToPlaylist(playlistId, trackIds)
                    entered.complete(Unit)
                    release.await()
                    if (fails) error("Old response lost")
                }
            }
            val replacement = NowPlayingTestProvider()
            var active: MediaProvider = old
            var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
            var callbacks = 0
            val coordinator = NaviampCorePlaylistMembershipCoordinator(
                { active }, { editor }, { editor = it }, onPlaylistChanged = { callbacks++ },
            )
            coordinator.open(nowPlayingTrack("current"))
            coordinator.toggle("playlist-2")
            val job = launch { coordinator.apply() }
            entered.await()
            coordinator.reset()
            active = object : MediaProvider by replacement { override val cacheNamespace = "new" }
            coordinator.open(nowPlayingTrack("current"))
            val expected = editor
            release.complete(Unit)
            job.join()
            assertEquals(expected, editor)
            assertEquals(0, callbacks)
            assertTrue(replacement.added.isEmpty())
            assertEquals(1, base.added.size)
        }
    }

    @Test
    fun freshMembershipOwnerRecoversCommittedStateWithoutReplayingLostDraft() = runTest {
        for (committed in listOf(false, true)) {
            val base = NowPlayingTestProvider()
            var oldEditor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
            val old = NaviampCorePlaylistMembershipCoordinator({ base }, { oldEditor }, { oldEditor = it })
            old.open(nowPlayingTrack("current"))
            old.toggle("playlist-2")
            if (committed) base.addTracksToPlaylist("playlist-2", listOf(TrackId("current")))
            // A fresh owner has no access to the old draft or in-flight work after process death.
            var reopened: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
            val fresh = NaviampCorePlaylistMembershipCoordinator({ base }, { reopened }, { reopened = it })
            fresh.open(nowPlayingTrack("current"))
            assertEquals(committed, reopened!!.rows.last().selected)
            assertFalse(reopened!!.saved)
            assertFalse(reopened!!.saving)
            if (!committed) fresh.toggle("playlist-2")
            fresh.apply()
            assertEquals(1, base.playlistTracks("playlist-2").count { it.id.value == "current" })
            assertEquals(1, base.added.size)
        }
    }

    @Test
    fun membershipLoadingStopsAtOneHundredPlaylists() = runTest {
        val base = NowPlayingTestProvider()
        var reads = 0
        val provider = object : MediaProvider by base {
            override suspend fun playlists(limit: Int) = (0 until 101).map { Playlist("p-$it", "Playlist $it", 0) }
            override suspend fun playlistTracks(playlistId: String): List<Track> { reads++; return emptyList() }
        }
        var editor: app.naviamp.ui.NaviampTrackPlaylistMembershipUi? = null
        val coordinator = NaviampCorePlaylistMembershipCoordinator({ provider }, { editor }, { editor = it })
        coordinator.open(nowPlayingTrack("current"))
        assertEquals(100, reads)
        assertEquals(100, editor!!.rows.size)
        assertTrue(editor!!.truncated)
    }

    @Test
    fun successfulGeneratedRadioRecordsOnlyEligibleArtistActivity() = runTest {
        val trackFixture = mediaFixture(this)
        trackFixture.artistActivity.favoriteArtistIds += ArtistId("artist")

        trackFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))

        assertEquals(listOf("source:artist:Artist:now"), trackFixture.artistActivity.trackRadioRecords)

        val unfavoritedFixture = mediaFixture(this)
        unfavoritedFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        assertTrue(unfavoritedFixture.artistActivity.trackRadioRecords.isEmpty())

        val failedFixture = mediaFixture(this)
        failedFixture.artistActivity.favoriteArtistIds += ArtistId("artist")
        failedFixture.provider.trackRadioFailure = IllegalStateException("radio failed")
        failedFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        assertTrue(failedFixture.artistActivity.trackRadioRecords.isEmpty())

        val unidentifiedFixture = mediaFixture(this)
        unidentifiedFixture.artistActivity.favoriteArtistIds += ArtistId("artist")
        unidentifiedFixture.live.replace(
            unidentifiedFixture.live.state.value.copy(
                currentTrack = nowPlayingTrack("current").copy(artistId = null),
                queue = PlaybackQueue(listOf(nowPlayingTrack("current").copy(artistId = null)), 0),
            ),
        )
        unidentifiedFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        assertTrue(unidentifiedFixture.artistActivity.trackRadioRecords.isEmpty())

        val cancelledFixture = mediaFixture(this)
        cancelledFixture.artistActivity.favoriteArtistIds += ArtistId("artist")
        cancelledFixture.provider.trackRadioFailure = CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            cancelledFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        }
        assertTrue(cancelledFixture.artistActivity.trackRadioRecords.isEmpty())

        val artistFixture = mediaFixture(this)
        artistFixture.transactions.startArtistRadio(Artist(ArtistId("direct"), "Direct"))
        assertEquals(listOf("source:direct:Direct:now"), artistFixture.artistActivity.artistRadioRecords)
    }

    @Test
    fun presenterPublishesCompleteQueueRelatedAndCapabilityState() = runTest {
        val fixture = mediaFixture(this)
        fixture.presenter.publish()

        val ui = fixture.store.state.value.shell.nowPlaying
        assertEquals("current", ui?.id)
        assertEquals(listOf("queue:0"), ui?.backTo?.map { it.id })
        assertEquals(listOf("queue:2"), ui?.upNext?.map { it.id })
        assertEquals(listOf("related:0"), ui?.related?.map { it.id })
        assertEquals(listOf(0.25f, 0.75f), ui?.visualizerFrame?.bands)
        assertTrue(ui?.canStartRadio == true)
        assertTrue(ui.canFavorite)
        assertTrue(ui.canRate)
    }

    @Test
    fun presenterPublishesCompleteRadioCatalogWithActiveStation() = runTest {
        val fixture = mediaFixture(this)
        val stations = listOf(
            InternetRadioStation("one", "One", "https://one.example"),
            InternetRadioStation("two", "Two", "https://two.example"),
        )
        fixture.live.updateCurrentStation(stations[1])
        val presenter = NaviampCoreNowPlayingPresenter(
            fixture.store,
            { fixture.provider },
            fixture.live,
            NaviampPlaybackQueueCoordinator(fixture.live),
            fixture.effects,
            fixture.sidecars,
            internetRadioStations = { stations },
        )

        presenter.publish()

        val ui = fixture.store.state.value.shell.nowPlaying
        assertEquals(listOf("one", "station", "two"), ui?.radioStations?.map { it.id })
        assertTrue(ui?.isLive == true)
    }

    @Test
    fun currentTrackMembershipEditorAddsAndRemovesAllOccurrencesThenReconciles() = runTest {
        val fixture = mediaFixture(this)

        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.AddToPlaylist))
        val opened = assertNotNull(fixture.store.state.value.shell.playlistMembership)
        assertEquals(listOf(true, false), opened.rows.map { it.selected })

        fixture.controller.execute(NaviampCoreCommand.NowPlaying.TogglePlaylistMembership("playlist"))
        fixture.controller.execute(NaviampCoreCommand.NowPlaying.TogglePlaylistMembership("playlist-2"))
        fixture.controller.execute(NaviampCoreCommand.NowPlaying.ApplyPlaylistMembership)

        val saved = assertNotNull(fixture.store.state.value.shell.playlistMembership)
        assertTrue(saved.saved)
        assertEquals(listOf(false, true), saved.rows.map { it.selected })
        assertEquals(listOf("past", "next"), fixture.provider.playlistContents.getValue("playlist").map { it.id.value })
        assertEquals(listOf("past", "current"), fixture.provider.playlistContents.getValue("playlist-2").map { it.id.value })
    }

    @Test
    fun queueTrackOpensTheSameMembershipEditor() = runTest {
        val fixture = mediaFixture(this)
        val next = fixture.store.state.value.shell.nowPlaying!!.upNext.single()

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.QueueItem(
                NowPlayingItemActionRequest(
                    item = next,
                    target = NowPlayingItemTarget.QueueIndex(2),
                    action = NowPlayingItemAction.AddToPlaylist,
                ),
            ),
        )

        val editor = assertNotNull(fixture.store.state.value.shell.playlistMembership)
        assertEquals("next", editor.trackId)
        assertEquals(listOf(true, false), editor.rows.map { it.selected })
    }

    @Test
    fun displayPlaylistMetadataAndDownloadActionsAreOwnedByCore() = runTest {
        val fixture = mediaFixture(this)

        fixture.controller.execute(displayCommand(NowPlayingDisplayAction.ToggleLyrics))
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Display(
                NowPlayingDisplayActionRequest(
                    NowPlayingDisplayAction.SelectVisualizer,
                    visualizer = NaviampVisualizer.LyricMirrorTunnel,
                ),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Display(
                NowPlayingDisplayActionRequest(
                    NowPlayingDisplayAction.SelectLyricsDisplayTiming,
                    lyricsDisplayPreference = LyricsDisplayPreference.LineSynced,
                ),
            ),
        )
        fixture.controller.execute(
            currentCommand(
                NowPlayingCurrentTrackAction.AddToPlaylist,
                playlistChoice = NaviampPlaylistChoiceUi("playlist", "Playlist"),
            ),
        )
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.ToggleFavorite))
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.SetRating, rating = 4))
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.Download))
        advanceUntilIdle()

        assertEquals(listOf("current", "current", "current"), fixture.sidecars.lyricsLoads)
        assertEquals(
            LyricsDisplayPreference.LineSynced,
            fixture.store.state.value.shell.playback.settings.lyricsDisplayPreference,
        )
        assertEquals(listOf(NaviampVisualizer.LyricMirrorTunnel), fixture.visualizers)
        assertEquals(listOf("playlist:current"), fixture.provider.added)
        assertEquals(listOf("current:true"), fixture.provider.favorites)
        assertEquals(listOf("current:4"), fixture.provider.ratings)
        assertTrue(fixture.store.state.value.shell.nowPlaying?.favoriteActive == true)
        assertEquals(4, fixture.store.state.value.shell.nowPlaying?.userRating)
        assertEquals(listOf("current"), fixture.downloads)
    }

    @Test
    fun relatedQueueAndRadioSelectionsUseOneCoreQueuePolicy() = runTest {
        val fixture = mediaFixture(this)
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        assertEquals("current", fixture.live.state.value.queue.current?.id?.value)
        assertTrue(fixture.live.state.value.queue.tracks.any { it.id.value == "radio" })
        assertTrue(fixture.effects.selections.isEmpty())
        assertEquals("Playing current radio.", fixture.store.state.value.overlays.status)
        val recentSection = fixture.store.state.value.shell.home.content.collectionSections
            .single { it.id == app.naviamp.domain.settings.HomeSectionIds.RecentRadio }
        assertTrue(recentSection.items.single().mediaItem.id.startsWith("track:current:session:"))
        assertEquals("2 tracks", recentSection.items.single().mediaItem.subtitle)

        fixture.live.replace(
            fixture.live.state.value.copy(
                currentTrack = nowPlayingTrack("current"),
                queue = PlaybackQueue(
                    listOf(nowPlayingTrack("past"), nowPlayingTrack("current"), nowPlayingTrack("next")),
                    1,
                ),
            ),
        )
        fixture.presenter.publish()
        val related = fixture.store.state.value.shell.nowPlaying!!.related.single()

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Selection(
                NowPlayingSelectionActionRequest(related, NowPlayingSelectionAction.SelectRelatedItem),
            ),
        )
        assertEquals("related", fixture.live.state.value.currentTrack?.id?.value)
        assertEquals(listOf("related:0"), fixture.effects.selections)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.QueueItem(
                NowPlayingItemActionRequest(
                    item = related,
                    target = NowPlayingItemTarget.RelatedIndex(0),
                    action = NowPlayingItemAction.PlayNext,
                ),
            ),
        )
        assertTrue(fixture.live.state.value.queue.upNext().any { it.id.value == "related" })

    }

    @Test
    fun trackRadioKeepsTheExistingQueueUntilTheProviderFinishes() = runTest {
        val fixture = mediaFixture(this)
        val buildGate = CompletableDeferred<Unit>()
        fixture.provider.trackRadioGate = buildGate

        val request = async { fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio)) }
        runCurrent()

        assertEquals(listOf("past", "current", "next"), fixture.live.state.value.queue.tracks.map { it.id.value })
        assertTrue(fixture.effects.selections.isEmpty())
        assertEquals("Playing current radio while the queue builds.", fixture.store.state.value.overlays.status)

        buildGate.complete(Unit)
        request.await()
        assertEquals(listOf("past", "current", "radio"), fixture.live.state.value.queue.tracks.map { it.id.value })
    }

    @Test
    fun refreshingRadioPreservesPlayingAndPausedSessionsAndPreviousHistory() = runTest {
        for (state in listOf(PlaybackState.Playing, PlaybackState.Paused)) {
            val fixture = mediaFixture(this)
            val tracks = listOf("first", "second", "third", "current", "old-upcoming").map(::nowPlayingTrack)
            val before = fixture.live.state.value.copy(
                queue = PlaybackQueue(tracks, 3),
                playbackState = state,
                progress = PlaybackProgress(positionSeconds = 73.0, durationSeconds = 180.0),
            )
            fixture.live.replace(before)
            fixture.provider.trackRadioTracks = listOf(nowPlayingTrack("current"), nowPlayingTrack("radio"), nowPlayingTrack("radio"))

            fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))

            val after = fixture.live.state.value
            assertEquals(listOf("first", "second", "third", "current", "radio"), after.queue.tracks.map { it.id.value })
            assertEquals(3, after.queue.currentIndex)
            assertEquals("third", after.queue.previous().current?.id?.value)
            assertEquals(before.copy(queue = after.queue), after)
            assertTrue(fixture.effects.selections.isEmpty())
            assertEquals(after.queue, fixture.effects.appliedQueues.single())
        }
    }

    @Test
    fun failedRadioRefreshLeavesTheEntireSessionUntouched() = runTest {
        val fixture = mediaFixture(this)
        fixture.provider.trackRadioFailure = IllegalStateException("radio failed")
        val before = fixture.live.state.value

        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))

        assertEquals(before, fixture.live.state.value)
        assertTrue(fixture.effects.selections.isEmpty())
        assertTrue(fixture.effects.appliedQueues.isEmpty())
    }

    @Test
    fun delayedRadioRefreshDoesNotReplaceANewerTrackQueue() = runTest {
        val fixture = mediaFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.provider.trackRadioGate = gate
        val request = async { fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio)) }
        runCurrent()
        fixture.transactions.play(listOf(nowPlayingTrack("different")))
        val newer = fixture.live.state.value

        gate.complete(Unit)
        request.await()

        assertEquals(newer, fixture.live.state.value)
        assertEquals(listOf("different:0"), fixture.effects.selections)
    }

    @Test
    fun radioForADifferentSeedStillStartsPlaybackImmediately() = runTest {
        val fixture = mediaFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.provider.trackRadioGate = gate
        val request = async { fixture.transactions.startTrackRadio(nowPlayingTrack("different")) }
        runCurrent()

        assertEquals(listOf("different:0"), fixture.effects.selections)
        assertEquals(listOf("different"), fixture.live.state.value.queue.tracks.map { it.id.value })
        gate.complete(Unit)
        request.await()
        assertEquals(listOf("different", "radio"), fixture.live.state.value.queue.tracks.map { it.id.value })
    }

    @Test
    fun trackRadioPlaybackSurvivesOptionalFavoriteArtistActivityFailure() = runTest {
        val fixture = mediaFixture(this)
        fixture.artistActivity.failure = IllegalStateException("favorite artist activity unavailable")

        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))

        assertEquals("current", fixture.live.state.value.queue.current?.id?.value)
        assertTrue(fixture.live.state.value.queue.tracks.any { it.id.value == "radio" })
        assertEquals("Playing current radio.", fixture.store.state.value.overlays.status)
    }

    @Test
    fun currentTrackArtistAndAlbumLinksCloseNowPlayingAndOpenSharedDetails() = runTest {
        val albumFixture = mediaFixture(this)
        albumFixture.store.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = true))
        }
        albumFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.GoToAlbum))
        assertEquals(SharedRoute.Home, albumFixture.store.state.value.shell.shellChrome.selectedRoute)
        assertFalse(albumFixture.store.state.value.shell.shellChrome.nowPlayingOpen)

        val artistFixture = mediaFixture(this)
        artistFixture.store.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = true))
        }
        artistFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.GoToArtist))
        assertEquals(SharedRoute.Home, artistFixture.store.state.value.shell.shellChrome.selectedRoute)
        assertFalse(artistFixture.store.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun individualArtistNameResolvesBeforeOpeningSharedArtistDetails() = runTest {
        val fixture = mediaFixture(this)
        fixture.live.updateCurrentTrack(
            nowPlayingTrack("current").copy(
                artistId = ArtistId("combined"),
                artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
            ),
        )
        fixture.presenter.publish()

        fixture.controller.execute(
            currentCommand(
                action = NowPlayingCurrentTrackAction.GoToArtist,
                artistName = "David Guetta",
            ),
        )

        assertEquals("david-guetta", fixture.store.state.value.shell.artistDetail.selectedArtist?.id)
        assertEquals("David Guetta", fixture.store.state.value.shell.artistDetail.selectedArtist?.title)
    }

    @Test
    fun nameOnlyCreditOpensVirtualArtistCatalogInsteadOfDoingNothing() = runTest {
        val fixture = mediaFixture(this)
        fixture.live.updateCurrentTrack(
            nowPlayingTrack("current").copy(
                artistId = ArtistId("combined"),
                artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
            ),
        )
        fixture.presenter.publish()

        fixture.controller.execute(
            currentCommand(
                action = NowPlayingCurrentTrackAction.GoToArtist,
                artistName = "HUGEL",
            ),
        )

        val detail = assertNotNull(fixture.store.state.value.shell.artistDetail.detail)
        assertEquals("HUGEL", detail.artist.title)
        assertTrue(detail.albums.isNotEmpty())
        assertFalse(detail.artist.canFavorite)
    }

    @Test
    fun staleTargetsAndMissingPayloadsNeverBecomeSilentActions() = runTest {
        val fixture = mediaFixture(this)
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Selection(
                NowPlayingSelectionActionRequest(
                    app.naviamp.ui.NaviampNowPlayingItemUi("queue:99", "Missing", ""),
                    NowPlayingSelectionAction.SelectQueueItem,
                ),
            ),
        )
        assertEquals("Queue item is no longer available.", fixture.store.state.value.overlays.status)

        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.CreatePlaylistAndAdd))
        assertEquals("Playlist name cannot be blank.", fixture.store.state.value.overlays.status)
    }
}

private data class MediaFixture(
    val store: NaviampCoreStateStore,
    val provider: NowPlayingTestProvider,
    val live: NaviampLivePlaybackController,
    val effects: NowPlayingTestEffects,
    val sidecars: NowPlayingTestSidecars,
    val presenter: NaviampCoreNowPlayingPresenter,
    val controller: NaviampCoreNowPlayingMediaController,
    val visualizers: List<NaviampVisualizer>,
    val downloads: List<String>,
    val transactions: NaviampCoreMediaTransactions,
    val artistActivity: RecordingFavoriteArtistActivity,
)

private fun mediaFixture(scope: kotlinx.coroutines.CoroutineScope): MediaFixture {
    val provider = NowPlayingTestProvider()
    val store = NaviampCoreStateStore()
    store.updateShell { shell ->
        shell.copy(
            connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
            playlistChoices = listOf(
                NaviampPlaylistChoiceUi("playlist", "Playlist"),
                NaviampPlaylistChoiceUi("playlist-2", "Second Playlist"),
            ),
        )
    }
    val tracks = listOf(nowPlayingTrack("past"), nowPlayingTrack("current"), nowPlayingTrack("next"))
    val live = NaviampLivePlaybackController(
        NaviampLivePlaybackState(
            currentTrack = tracks[1],
            queue = PlaybackQueue(tracks, 1),
            playbackState = PlaybackState.Playing,
        ),
    )
    val queue = NaviampPlaybackQueueCoordinator(live)
    val effects = NowPlayingTestEffects()
    val sidecars = NowPlayingTestSidecars()
    val presenter = NaviampCoreNowPlayingPresenter(store, { provider }, live, queue, effects, sidecars)
    val settings = NaviampCorePlaybackSettingsPort { updated, _ ->
        store.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
        updated
    }
    val transport = NaviampCorePlaybackController(
        scope,
        store,
        { provider },
        live,
        queue,
        effects,
        settings,
        sidecars,
        emptyPlaybackSessions(),
        presenter,
        nowEpochMillis = { 1_000L },
    )
    val visualizers = mutableListOf<NaviampVisualizer>()
    val navigation = NaviampCoreNavigationController(
        app.naviamp.app.NaviampNavigationController(),
        store,
        NaviampCoreArtistNavigator { error("Not expected") },
    )
    val mediaDetails = NaviampCoreMediaDetailController(store, { provider }, navigation, scope)
    val playedStations = mutableListOf<String>()
    val radio = NaviampCoreInternetRadioController(
        store,
        { provider },
        NaviampCoreInternetRadioPlaybackPort { station -> playedStations += station.id },
        object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
    )
    val downloaded = mutableListOf<String>()
    val downloads = NaviampCoreDownloadsController(
        scope,
        store,
        { provider },
        object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
            override suspend fun pruneMissing(sourceId: String) = 0
            override suspend fun remove(sourceId: String, track: Track) = Unit
            override suspend fun deleteAll(sourceId: String) = 0
        },
        NaviampCoreDownloadTransferPort { request, _, update ->
            downloaded += request.tracks.map { it.id.value }
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(false)
        },
        object : NaviampCoreKeepDownloadedPort {
            override fun policies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun toggle(policy: KeepDownloadedCollectionPolicy) = NaviampKeepDownloadedToggleResult.Enable
            override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
                NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
        },
        NaviampCoreDownloadedPlaybackPort { _, _ -> },
    )
    var recentRadioStreams = emptyList<app.naviamp.domain.settings.RecentRadioStream>()
    val artistActivity = RecordingFavoriteArtistActivity()
    val generatedRadio = NaviampCoreMediaTransactions(
        stateStore = store,
        busyIndicator = NaviampCoreBusyIndicator(store),
        providerSource = { provider },
        registry = NaviampCoreMediaRegistry(),
        playback = live,
        queue = queue,
        effects = effects,
        queuePlayback = NaviampCoreQueuePlaybackController(live, queue, effects, { presenter.publish() }, {}),
        downloads = downloads,
        mediaDetails = mediaDetails,
        recentRadioStreams = NaviampRecentRadioStreamController(
            load = { recentRadioStreams },
            save = { recentRadioStreams = it },
        ),
        externalUri = NaviampCoreExternalUriPort {},
        favoritedAtIso8601 = { "now" },
        publishNowPlaying = { presenter.publish() },
        openNowPlaying = {},
        favoriteArtistActivity = artistActivity,
    )
    val controller = NaviampCoreNowPlayingMediaController(
        stateStore = store,
        providerSource = { provider },
        playback = live,
        queue = queue,
        effects = effects,
        presenter = presenter,
        playbackController = transport,
        settings = settings,
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) { visualizers += visualizer }
        },
        sidecars = sidecars,
        downloads = downloads,
        mediaDetails = mediaDetails,
        navigation = navigation,
        radio = radio,
        generatedRadio = generatedRadio,
        favoritedAtIso8601 = { "now" },
    )
    presenter.publish()
    return MediaFixture(
        store,
        provider,
        live,
        effects,
        sidecars,
        presenter,
        controller,
        visualizers,
        downloaded,
        generatedRadio,
        artistActivity,
    )
}

private class RecordingFavoriteArtistActivity : HomeLibraryRepository {
    val favoriteArtistIds = mutableSetOf<ArtistId>()
    val artistRadioRecords = mutableListOf<String>()
    val trackRadioRecords = mutableListOf<String>()
    var failure: Throwable? = null

    override fun albumYears(sourceId: String): List<HomeAlbumYear> = emptyList()

    override fun recordArtistRadioPlayed(sourceId: String, artist: Artist, playedAtIso8601: String) {
        failure?.let { throw it }
        artistRadioRecords += "$sourceId:${artist.id.value}:${artist.name}:$playedAtIso8601"
    }

    override fun recordTrackArtistRadioPlayedIfFavorite(
        sourceId: String,
        artistId: ArtistId,
        artistName: String,
        playedAtIso8601: String,
    ): Boolean {
        failure?.let { throw it }
        if (artistId !in favoriteArtistIds) return false
        trackRadioRecords += "$sourceId:${artistId.value}:$artistName:$playedAtIso8601"
        return true
    }
}

private class NowPlayingTestEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities(supportsVisualizer = true)
    override val playbackSource = PlaybackSource.ProviderStream
    val selections = mutableListOf<String>()
    val appliedQueues = mutableListOf<PlaybackQueue>()
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = true
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) { appliedQueues += queue }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        selections += "${queue.tracks[index].id.value}:$index"
    }
}

private class NowPlayingTestSidecars : NaviampCoreNowPlayingSidecarPort {
    val lyricsLoads = mutableListOf<String>()
    override fun snapshot() = NaviampCoreNowPlayingSidecars(
        visualizerFrame = PlaybackVisualizerFrame(listOf(0.25f, 0.75f), 1L),
        relatedTracks = listOf(nowPlayingTrack("related")),
        relatedTracksSource = RelatedTracksSource.ProviderRadio,
        internetRadioStations = listOf(InternetRadioStation("station", "Station", "https://radio")),
    )
    override suspend fun loadForTrack(track: Track) = Unit
    override suspend fun loadLyrics(track: Track) { lyricsLoads += track.id.value }
    override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
}

private class NowPlayingTestProvider : MediaProvider {
    override val id = ProviderId("now-playing")
    override val displayName = "Now Playing"
    override val capabilities = ProviderCapabilities(
        false, false, true, true, true,
        supportsTrackFavorites = true,
        supportsTrackRatings = true,
    )
    val added = mutableListOf<String>()
    val favorites = mutableListOf<String>()
    val ratings = mutableListOf<String>()
    var trackRadioFailure: Throwable? = null
    var artistRadioFailure: Throwable? = null
    val playlistContents = mutableMapOf(
        "playlist" to mutableListOf(
            nowPlayingTrack("past"),
            nowPlayingTrack("current"),
            nowPlayingTrack("current"),
            nowPlayingTrack("next"),
        ),
        "playlist-2" to mutableListOf(nowPlayingTrack("past")),
    )
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId) = AlbumDetails(
        Album(
            id = albumId,
            title = "Album",
            artistName = "Artist",
            coverArtId = albumId.value,
            recentlyAddedAtIso8601 = null,
            releaseYear = 2026,
        ),
        listOf(nowPlayingTrack("album-track")),
    )
    override suspend fun artist(artistId: ArtistId) = ArtistDetails(
        Artist(artistId, if (artistId.value == "david-guetta") "David Guetta" else "Artist"),
        listOf(album(AlbumId("artist-album")).album),
    )
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = when {
        query.equals("David Guetta", ignoreCase = true) -> MediaSearchResults(
            artists = listOf(Artist(ArtistId("david-guetta"), "David Guetta")),
        )
        query.equals("HUGEL", ignoreCase = true) -> MediaSearchResults(
            tracks = listOf(
                nowPlayingTrack("hugel-credit").copy(
                    artistId = ArtistId("combined"),
                    artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
                ),
            ),
        )
        else -> MediaSearchResults()
    }
    var trackRadioTracks = listOf(nowPlayingTrack("radio"))
    var trackRadioGate: CompletableDeferred<Unit>? = null
    override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> {
        trackRadioGate?.await()
        trackRadioFailure?.let { throw it }
        return trackRadioTracks
    }
    override suspend fun artistRadio(artistId: ArtistId, count: Int): List<Track> {
        artistRadioFailure?.let { throw it }
        return listOf(nowPlayingTrack("artist-radio"))
    }
    override suspend fun internetRadioStations() = listOf(InternetRadioStation("station", "Station", "https://radio"))
    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        added += "$playlistId:${trackIds.joinToString(",") { it.value }}"
        playlistContents.getOrPut(playlistId) { mutableListOf() }
            .addAll(trackIds.map { nowPlayingTrack(it.value) })
    }
    override suspend fun playlists(limit: Int) = playlistContents.map { (id, tracks) -> Playlist(id, id, tracks.size) }.take(limit)
    override suspend fun playlistTracks(playlistId: String) = playlistContents[playlistId].orEmpty()
    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: TrackId) {
        playlistContents[playlistId]?.removeAll { it.id == trackId }
    }
    override suspend fun replacePlaylistTracks(
        playlistId: String,
        currentTrackIds: List<TrackId>,
        trackIds: List<TrackId>,
    ) {
        playlistContents[playlistId] = trackIds.map { nowPlayingTrack(it.value) }.toMutableList()
    }
    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>) = Playlist("created", name, trackIds.size)
    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) { favorites += "${trackId.value}:$favorite" }
    override suspend fun setTrackRating(trackId: TrackId, rating: Int?) { ratings += "${trackId.value}:$rating" }
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun displayCommand(action: NowPlayingDisplayAction) =
    NaviampCoreCommand.NowPlaying.Display(NowPlayingDisplayActionRequest(action))

private fun currentCommand(
    action: NowPlayingCurrentTrackAction,
    playlistChoice: NaviampPlaylistChoiceUi? = null,
    playlistName: String? = null,
    rating: Int? = null,
    artistId: String? = null,
    artistName: String? = null,
) = NaviampCoreCommand.NowPlaying.CurrentTrack(
    NowPlayingCurrentTrackUiActionRequest(action, playlistChoice, playlistName, rating, artistId, artistName),
)

private fun nowPlayingTrack(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistId = ArtistId("artist"),
    artistName = "Artist",
    albumId = AlbumId("album"),
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
