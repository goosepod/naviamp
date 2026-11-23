package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileAssignment
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampAlbumDetailActionRequest
import app.naviamp.ui.NaviampAlbumDetailCommand
import app.naviamp.ui.NaviampArtistAlbumActionRequest
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailActionRequest
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampLibraryView
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest as coroutineRunTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreTest {
    private val createdCores = mutableListOf<NaviampCore>()
    private fun runTest(body: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = coroutineRunTest {
        try { body() } finally {
            createdCores.asReversed().forEach(NaviampCore::close)
            createdCores.clear()
        }
    }
    private fun createTrackedCore(
        scope: kotlinx.coroutines.CoroutineScope, services: NaviampCoreServices,
        initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
        onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
            throw IllegalStateException("Core command failed: $command", cause)
        },
    ) = NaviampCore.create(scope, services, initialState, onAsyncFailure = onAsyncFailure).also(createdCores::add)

    @Test
    fun accountSwitchClearsOldSearchPlaybackAndMixSelectionsBeforeReloading() = runTest {
        val defaults = fakeCoreServices()
        val records = listOf("alice", "bob").map { id ->
            NaviampCoreSavedConnectionRecord(id, id, "https://music.example", id)
        }
        val port = object : NaviampCoreProviderSessionPort by defaults.connection {
            override suspend fun connect(request: NaviampCoreConnectionRequest,
                plan: app.naviamp.app.NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
                val id = (request as NaviampCoreConnectionRequest.Saved).id
                return NaviampCoreConnectedSession(id, id,
                    inventory = NaviampCoreConnectionInventory(records, id))
            }
        }
        val core = createTrackedCore(this, defaults.copy(connection = port),
            initialState = NaviampCoreInitialState(
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "alice"),
                connectionInventory = NaviampCoreConnectionInventory(records, "alice"),
                product = NaviampCoreState(shell = NaviampAppShellUiState(
                    search = NaviampSearchScreenUi(query = "Alice's search"),
                    artistMixBuilder = app.naviamp.ui.SharedArtistMixBuilderUi(query = "Alice's artist"),
                    albumMixBuilder = app.naviamp.ui.SharedAlbumMixBuilderUi(query = "Alice's album"),
                    genreMixBuilder = app.naviamp.ui.SharedGenreMixBuilderUi(query = "Alice's genre"),
                    sonicPathBuilder = app.naviamp.ui.SharedSonicPathBuilderUi(startQuery = "Alice's start"),
                    sonicMixBuilder = app.naviamp.ui.SharedSonicMixBuilderUi(query = "Alice's seed"),
                )),
                playback = NaviampLivePlaybackState(currentTrack = coreTrack("alice-track")),
            ))
        core.execute(NaviampCoreCommand.Connection.SwitchAccount(app.naviamp.ui.NaviampSavedConnectionUi("bob", "bob", "https://music.example", "bob")))
        advanceUntilIdle()
        val shell = core.state.value.shell
        assertEquals("bob", shell.connectionSettings.currentSourceId)
        assertEquals("", shell.search.query)
        assertEquals("", shell.artistMixBuilder.query)
        assertEquals("", shell.albumMixBuilder.query)
        assertEquals("", shell.genreMixBuilder.query)
        assertEquals("", shell.sonicPathBuilder.startQuery)
        assertEquals("", shell.sonicMixBuilder.query)
        assertEquals("", shell.nowPlaying?.id)
        assertEquals(emptyList(), shell.nowPlaying?.upNext)
        assertEquals(SharedRoute.Home, shell.shellChrome.selectedRoute)
    }

    @Test
    fun globalShortcutsRouteThroughSharedPlaybackPolicy() = runTest {
        val effects = FakeCorePlaybackEffects()
        val core = createTrackedCore(this, fakeCoreServices(playbackEffects = effects))
        val tracks = listOf(coreTrack("one"), coreTrack("two"))
        core.updateLivePlayback { live ->
            live.copy(
                currentTrack = tracks.first(),
                queue = PlaybackQueue(tracks, 0),
            )
        }
        assertNotNull(effects.observer).onStateChanged(app.naviamp.domain.playback.PlaybackState.Paused)

        assertEquals(
            NaviampCoreHostShortcutEffect.BringToFront,
            core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.BringToFront),
        )
        assertEquals(null, core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.PlayPause))
        advanceUntilIdle()
        assertNotNull(effects.observer).onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.PlayPause)
        advanceUntilIdle()
        assertNotNull(effects.observer).onStateChanged(app.naviamp.domain.playback.PlaybackState.Paused)
        core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.PlayPause)
        core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.Previous)
        core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.NextTrack)
        core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.VolumeDown)
        advanceUntilIdle()

        assertEquals(2, effects.resumeCount)
        assertEquals(1, effects.pauseCount)
        assertTrue(effects.navigationCommands.contains(PlaybackQueueNavigationCommand.Next))
        assertEquals(listOf(95), effects.volumes)
    }

    @Test
    fun failedNowPlayingReportIsNotReplayedAsStalePresence() = runTest {
        val provider = FakeCoreMediaProvider(
            supportsPlayReporting = true,
            failNowPlayingReports = true,
        )
        val pending = RecordingCorePendingProviderActionRepository()
        val effects = FakeCorePlaybackEffects()
        val services = fakeCoreServices(
            provider = provider,
            playbackEffects = effects,
            providerActions = NaviampProviderActionController(pending),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connectionInventory = NaviampCoreConnectionInventory(currentSourceId = "source"),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = createTrackedCore(this, services, initialState = initial)
        core.updateLivePlayback { live ->
            live.copy(
                currentTrack = provider.track,
                queue = PlaybackQueue(listOf(provider.track), 0),
            )
        }

        assertTrue(provider.capabilities.supportsPlayReporting)
        assertEquals("source", core.state.value.shell.connectionSettings.currentSourceId)
        assertNotNull(effects.observer).onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        advanceUntilIdle()

        assertEquals(
            emptyList(),
            pending.enqueued,
        )
    }

    @Test
    fun constructsOneProductGraphAndRoutesActionsWithoutHostControllers() = runTest {
        val failures = mutableListOf<Throwable>()
        val core = createTrackedCore(
            scope = this,
            services = fakeCoreServices(),
            onAsyncFailure = { _, cause -> failures += cause },
        )

        core.actions.shell.navigationActions.onRouteSelected(SharedRoute.Library)
        core.actions.shell.searchActions.onQueryChanged("ambient")
        core.actions.settingsSync.onExport()
        advanceUntilIdle()

        assertEquals(SharedRoute.Library, core.state.value.shell.shellChrome.selectedRoute)
        assertEquals("ambient", core.state.value.shell.search.query)
        assertEquals("Settings exported to settings.json.", core.state.value.settingsSync.status)
        assertNotNull(core.state.value.shell.nowPlaying)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun nativePlaybackObservationsEnterThroughTheCoreFacade() = runTest {
        val core = createTrackedCore(this, fakeCoreServices())

        core.updateLivePlayback { state ->
            state.copy(playbackState = app.naviamp.domain.playback.PlaybackState.Playing)
        }
        core.onTrackChanged(coreTrack("observed"))

        assertEquals("observed", core.state.value.shell.nowPlaying?.id)
        assertEquals("Playing", core.state.value.shell.nowPlaying?.stateLabel)
    }

    @Test
    fun selectingProviderContentStartsPlaybackAndOpensNowPlayingInCore() = runTest {
        val provider = FakeCoreMediaProvider()
        val core = createTrackedCore(this, fakeCoreServices(provider))
        core.dispatch(NaviampCoreCommand.Search.ChangeQuery("core"))
        core.execute(NaviampCoreCommand.Search.Submit)
        val track = core.state.value.shell.search.results.tracks.single()

        core.execute(
            NaviampCoreCommand.Media.TrackAction(
                SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
            ),
        )

        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals(provider.track.id.value, core.state.value.shell.nowPlaying?.id)
    }

    @Test
    fun selectingLibrarySongStartsPlaybackFromTheLibraryCatalog() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val core = createTrackedCore(this, fakeCoreServices(provider, playbackEffects = effects))
        core.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
        val track = core.state.value.shell.library.songs.tracks.single()

        core.execute(
            NaviampCoreCommand.Library.TrackAction(
                SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
            ),
        )

        assertEquals(listOf(provider.track.id), effects.selections.single().tracks.map { it.id })
        assertEquals(provider.track.id.value, core.state.value.shell.nowPlaying?.id)
    }

    @Test
    fun selectingDownloadedContentUsesTheCoreQueuePlaybackTransaction() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val downloads = listOf(
            NaviampCoreDownloadedTrack("file-one", coreTrack("download-one"), 1_000, "FLAC"),
            NaviampCoreDownloadedTrack("file-two", coreTrack("download-two"), 2_000, "FLAC"),
        )
        val defaults = fakeCoreServices(provider, playbackEffects = effects)
        val services = defaults.copy(
            downloads = defaults.downloads.copy(
                storage = object : NaviampCoreDownloadStoragePort {
                    override suspend fun snapshot(sourceId: String) =
                        NaviampCoreDownloadStorageSnapshot(downloads = downloads)

                    override suspend fun pruneMissing(sourceId: String) = 0
                    override suspend fun remove(sourceId: String, track: Track) = Unit
                    override suspend fun deleteAll(sourceId: String) = 0
                },
            ),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "source",
                ),
                connectionInventory = NaviampCoreConnectionInventory(currentSourceId = "source"),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = createTrackedCore(this, services, initialState = initial)
        core.execute(NaviampCoreCommand.Downloads.Refresh)

        core.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(
                    download = core.state.value.shell.downloads.downloads[1],
                    action = DownloadedTrackAction.Select,
                ),
            ),
        )

        val queue = effects.selections.single()
        assertEquals(listOf("download-one", "download-two"), queue.tracks.map { it.id.value })
        assertEquals(1, queue.currentIndex)
        assertEquals("download-two", core.state.value.shell.nowPlaying?.id)
        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun playlistDownloadUsesTheSharedDownloadsControllerRatherThanTheLegacyPlaylistPlaceholder() = runTest {
        val provider = FakeCoreMediaProvider()
        var transferredTrackIds = emptyList<String>()
        val defaults = fakeCoreServices(provider)
        val services = defaults.copy(
            downloads = defaults.downloads.copy(
                transfer = NaviampCoreDownloadTransferPort { request, _, update ->
                    transferredTrackIds = request.tracks.map { it.id.value }
                    update(DownloadJobUpdate.Completed)
                    NaviampCoreDownloadTransferResult(refreshDownloads = false)
                },
            ),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "source",
                ),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = createTrackedCore(this, services, initialState = initial)

        core.execute(
            NaviampCoreCommand.Playlists.Detail(
                NaviampPlaylistDetailActionRequest(
                    playlist = provider.playlist.toSharedMediaItemUi(
                        coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) },
                    ),
                    command = NaviampPlaylistDetailCommand.Download(null),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(provider.track.id.value), transferredTrackIds)
        // The transfer completed, so the starting status must not remain.
        assertEquals(null, core.state.value.shell.playlists.status)
    }

    @Test
    fun playlistAddToQueueUsesTheCoreQueueCoordinator() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val current = coreTrack("current")
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, provider.playlist.id)
        val profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless)
        val defaults = fakeCoreServices(provider, profiles = singlePlaybackProfile(target, profile))
        val core = createTrackedCore(
            scope = this,
            services = defaults.copy(
                playback = defaults.playback.copy(effects = effects),
            ),
            initialState = NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = current,
                    queue = PlaybackQueue(listOf(current), currentIndex = 0),
                ),
            ).withSource("source"),
        )

        core.execute(
            NaviampCoreCommand.Playlists.Detail(
                NaviampPlaylistDetailActionRequest(
                    playlist = provider.playlist.toSharedMediaItemUi(
                        coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) },
                    ),
                    command = NaviampPlaylistDetailCommand.AddToQueue,
                ),
            ),
        )

        assertEquals(
            listOf(current.id.value, provider.track.id.value),
            effects.appliedQueues.single().tracks.map { it.id.value },
        )
        assertEquals(
            listOf(provider.track.title),
            assertNotNull(core.state.value.shell.nowPlaying).upNext.map { it.title },
        )
        assertEquals("Connected.", core.state.value.shell.playlistDetail.status)
        assertEquals(target, effects.appliedQueues.single().groups.single().target)
        assertEquals(profile, effects.appliedQueues.single().groups.single().profile)
    }

    @Test
    fun albumAddToQueueImmediatelyRepublishesNowPlayingQueue() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val current = coreTrack("current")
        val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, provider.album.id.value)
        val profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless)
        val defaults = fakeCoreServices(provider, profiles = singlePlaybackProfile(target, profile))
        val core = createTrackedCore(
            scope = this,
            services = defaults.copy(playback = defaults.playback.copy(effects = effects)),
            initialState = NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = current,
                    queue = PlaybackQueue(listOf(current), currentIndex = 0),
                ),
            ).withSource("source"),
        )
        val albumItem = provider.album.toSharedMediaItemUi(coverArtUrl = { null })
        core.execute(
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(
                    albumItem,
                    NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select),
                ),
            ),
        )

        core.execute(
            NaviampCoreCommand.Detail.Album(
                NaviampAlbumDetailActionRequest(albumItem, NaviampAlbumDetailCommand.AddToQueue),
            ),
        )

        assertEquals(
            listOf(provider.track.title),
            assertNotNull(core.state.value.shell.nowPlaying).upNext.map { it.title },
        )
        assertEquals(
            listOf(current.id.value, provider.track.id.value),
            effects.appliedQueues.single().tracks.map { it.id.value },
        )
        assertEquals(target, effects.appliedQueues.single().groups.single().target)
        assertEquals(profile, effects.appliedQueues.single().groups.single().profile)
    }

    @Test
    fun seedOnlyLibraryRadioDoesNotPersistASuccessfulSession() = runTest {
        var persisted = listOf(libraryRecentRadioStream())
        val defaults = fakeCoreServices(FakeCoreMediaProvider())
        val core = createTrackedCore(
            scope = this,
            services = defaults.copy(radio = defaults.radio.copy(
                generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                    load = { persisted }, save = { persisted = it },
                ),
            )),
        )
        core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
            SharedMediaItemUi("library", "Library Radio", "Radio"),
        ))
        assertEquals(listOf("library"), persisted.map { it.id })
        assertEquals(app.naviamp.domain.radio.RadioBuildOutcome.Empty, core.state.value.overlays.radioNotice?.outcome)
    }

    @Test
    fun homeMixTilePlaysAMultiTrackSimilarSongStationInsteadOfTheSeedAlbum() = runTest {
        val similar = (1..5).map { coreTrack("similar-$it").copy(albumId = app.naviamp.domain.AlbumId("other-$it")) }
        val fixture = homeMixFixture(similar)

        fixture.playMix()

        val seed = fixture.effects.selections.first().tracks.single()
        assertEquals(fixture.album.id, seed.albumId)
        val queue = fixture.effects.appliedQueues.last().tracks.map { it.id }
        assertEquals(seed.id, queue.first())
        assertEquals(similar.map { it.id }.toSet(), queue.drop(1).toSet())
        assertEquals(listOf(seed.id), fixture.similarRequests)
        assertEquals(emptyList(), fixture.albumRadioRequests)
        assertEquals(listOf("track:${seed.id.value}"), fixture.persisted.map { it.id })
    }

    @Test
    fun savedHomeMixRebuildsFromRecentRadioThroughSimilarSongsNeverAlbumRadio() = runTest {
        val similar = (1..3).map { coreTrack("similar-$it").copy(albumId = app.naviamp.domain.AlbumId("other-$it")) }
        val fixture = homeMixFixture(similar)
        fixture.playMix()
        val seed = fixture.effects.selections.first().tracks.single()
        // Without saved session tracks, replay must regenerate the station from its identity.
        fixture.persist(fixture.persisted.map { it.copy(sessionTracks = emptyList()) })

        fixture.core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
            SharedMediaItemUi("track:${seed.id.value}", "Mix", "Radio")))
        advanceUntilIdle()

        // The rebuild asked for the seed's similar songs again and never touched album radio.
        assertEquals(listOf(seed.id, seed.id), fixture.similarRequests)
        assertEquals(emptyList(), fixture.albumRadioRequests)
        val albumSiblings = (fixture.effects.selections + fixture.effects.appliedQueues).flatMap { it.tracks }
            .filter { it.albumId == fixture.album.id && it.id != seed.id }
        assertEquals(emptyList(), albumSiblings)
        assertEquals(listOf("track:${seed.id.value}"), fixture.persisted.map { it.id })
    }

    @Test
    fun homeMixWithoutSimilarSongsUsesTheRadioFailurePathAndNeverQueuesTheAlbum() = runTest {
        val fixture = homeMixFixture(similar = emptyList())

        fixture.playMix()

        val seed = fixture.effects.selections.first().tracks.single()
        val queued = (fixture.effects.selections + fixture.effects.appliedQueues).flatMap { it.tracks }.map { it.id }.toSet()
        assertEquals(setOf(seed.id), queued)
        assertEquals(app.naviamp.domain.radio.RadioBuildOutcome.Empty, fixture.core.state.value.overlays.radioNotice?.outcome)
        assertEquals(emptyList(), fixture.persisted)
        assertEquals(emptyList(), fixture.albumRadioRequests)
    }

    private class HomeMixFixture(
        val core: NaviampCore,
        val effects: FakeCorePlaybackEffects,
        val album: Album,
        val similarRequests: List<app.naviamp.domain.TrackId>,
        val albumRadioRequests: List<app.naviamp.domain.AlbumId>,
        private val persistedRadio: () -> List<app.naviamp.domain.settings.RecentRadioStream>,
        val persist: (List<app.naviamp.domain.settings.RecentRadioStream>) -> Unit,
        private val scope: kotlinx.coroutines.test.TestScope,
    ) {
        val persisted get() = persistedRadio()

        suspend fun playMix() {
            val item = album.toSharedMediaItemUi({ null })
            core.execute(NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select))))
            scope.advanceUntilIdle()
            core.execute(NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.PlayAlbumMix)))
            scope.advanceUntilIdle()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.homeMixFixture(similar: List<Track>): HomeMixFixture {
        val original = FakeCoreMediaProvider()
        val albumTracks = (1..3).map { original.track.copy(id = app.naviamp.domain.TrackId("album-$it")) }
        val similarRequests = mutableListOf<app.naviamp.domain.TrackId>()
        val albumRadioRequests = mutableListOf<app.naviamp.domain.AlbumId>()
        val provider = object : MediaProvider by original {
            override val capabilities = original.capabilities.copy(supportsTrackRadio = true, supportsAlbumRadio = true)
            override suspend fun album(albumId: app.naviamp.domain.AlbumId) =
                app.naviamp.domain.AlbumDetails(original.album, albumTracks)
            override suspend fun albumRadio(albumId: app.naviamp.domain.AlbumId, count: Int): List<Track> =
                emptyList<Track>().also { albumRadioRequests += albumId }
            override suspend fun instantMixTracks(trackId: app.naviamp.domain.TrackId, count: Int): List<Track> {
                similarRequests += trackId
                return similar
            }
        }
        val effects = FakeCorePlaybackEffects()
        var persisted = emptyList<app.naviamp.domain.settings.RecentRadioStream>()
        val defaults = fakeCoreServices(provider)
        val core = createTrackedCore(this, defaults.copy(
            playback = defaults.playback.copy(effects = effects),
            radio = defaults.radio.copy(generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                load = { persisted }, save = { persisted = it })),
        ))
        return HomeMixFixture(core, effects, original.album, similarRequests, albumRadioRequests, { persisted }, { persisted = it }, this)
    }

    @Test
    fun playSomethingNewStartsRadioFromTheFirstUnplayedTrackInTheSelectedLibraries() = runTest {
        val fixture = somethingNewFixture(
            batches = listOf(listOf(coreTrack("played").copy(playCount = 3), coreTrack("pending"),
                coreTrack("hidden").copy(musicFolderId = "2"), coreTrack("fresh").copy(musicFolderId = "1"))),
            pendingListenIds = listOf("pending"),
            selectedFolders = listOf("1"),
        )

        fixture.core.execute(NaviampCoreCommand.Home.SelectStation(
            SharedHomeStationUi(app.naviamp.domain.home.HomeStationSomethingNew, "Play something new", "")))
        advanceUntilIdle()

        assertEquals("fresh", fixture.effects.selections.first().tracks.single().id.value)
        assertEquals(listOf("fresh", "similar-1", "similar-2"), fixture.effects.appliedQueues.last().tracks.map { it.id.value })
        assertEquals(listOf("fresh"), fixture.similarRequests.map { it.value })
        assertEquals(null, fixture.core.state.value.overlays.somethingNewNotice)
    }

    @Test
    fun playSomethingNewInAFullyPlayedLibraryStopsAtTheBoundAndPlaysNothing() = runTest {
        val fixture = somethingNewFixture(batches = listOf(listOf(coreTrack("played").copy(playCount = 1))))

        fixture.core.execute(NaviampCoreCommand.Home.PlaySomethingNew)
        runCurrent()

        assertEquals(app.naviamp.domain.radio.SomethingNewMaxBatches, fixture.randomRequests())
        assertEquals(emptyList(), fixture.effects.selections)
        assertEquals(app.naviamp.ui.NaviampSomethingNewNotice.NothingUnplayed,
            fixture.core.state.value.overlays.somethingNewNotice)
        fixture.core.dispatch(NaviampCoreCommand.Home.DismissSomethingNewNotice)
        assertEquals(null, fixture.core.state.value.overlays.somethingNewNotice)
    }

    @Test
    fun playSomethingNewOfflineChoosesOnlyAnUnplayedDownloadAndSaysSo() = runTest {
        val fixture = somethingNewFixture(
            batches = listOf(listOf(coreTrack("streamed"))),
            downloads = listOf(coreTrack("downloaded-played").copy(playCount = 2), coreTrack("downloaded-new")),
            offline = true,
        )

        fixture.core.execute(NaviampCoreCommand.Home.PlaySomethingNew)
        runCurrent()

        assertEquals(0, fixture.randomRequests())
        assertEquals("downloaded-new", fixture.effects.selections.first().tracks.single().id.value)
        assertEquals(app.naviamp.ui.NaviampSomethingNewNotice.PlayingFromDownloads,
            fixture.core.state.value.overlays.somethingNewNotice)
        // Offline the pick plays alone: no server Radio request, so no server-failure notice.
        assertEquals(emptyList(), fixture.similarRequests)
        assertEquals(null, fixture.core.state.value.overlays.radioNotice)
        assertEquals(listOf("downloaded-new"), fixture.effects.selections.flatMap { it.tracks }.map { it.id.value })
    }

    @Test
    fun radioThatCannotReachTheServerWhileOfflineSaysOfflineInsteadOfBlamingTheServer() = runTest {
        val offline = somethingNewFixture(batches = listOf(emptyList()), offline = true)

        offline.core.execute(NaviampCoreCommand.Home.StartSomethingNewRadio(coreTrack("seed")))
        runCurrent()

        assertEquals("seed", offline.effects.selections.first().tracks.single().id.value)
        assertEquals(app.naviamp.domain.radio.RadioBuildOutcome.Offline,
            offline.core.state.value.overlays.radioNotice?.outcome)
    }

    @Test
    fun radioFailureWhileOnlineKeepsTheServerFailureNotice() = runTest {
        val online = somethingNewFixture(batches = listOf(emptyList()), serverUnreachable = true)

        online.core.execute(NaviampCoreCommand.Home.StartSomethingNewRadio(coreTrack("seed")))
        advanceUntilIdle()

        assertEquals(app.naviamp.domain.radio.RadioBuildOutcome.Failed,
            online.core.state.value.overlays.radioNotice?.outcome)
    }

    @Test
    fun startingSomethingElseAbandonsAnInFlightSomethingNewPick() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val fixture = somethingNewFixture(batches = listOf(listOf(coreTrack("fresh"))), randomGate = gate)

        fixture.core.dispatch(NaviampCoreCommand.Home.PlaySomethingNew)
        runCurrent()
        fixture.core.execute(NaviampCoreCommand.Media.TrackAction(SharedTrackRowActionRequest(
            SharedTrackRowUi("other", "Other", "Artist"), SharedTrackRowAction.Select)))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fixture.randomRequests())
        assertTrue(fixture.effects.selections.none { queue -> queue.tracks.any { it.id.value == "fresh" } })
        assertEquals(null, fixture.core.state.value.overlays.somethingNewNotice)
    }

    private class SomethingNewFixture(
        val core: NaviampCore,
        val effects: FakeCorePlaybackEffects,
        val randomRequests: () -> Int,
        val similarRequests: List<app.naviamp.domain.TrackId>,
    )

    private fun kotlinx.coroutines.test.TestScope.somethingNewFixture(
        batches: List<List<Track>>,
        pendingListenIds: List<String> = emptyList(),
        downloads: List<Track> = emptyList(),
        offline: Boolean = false,
        selectedFolders: List<String> = emptyList(),
        randomGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
        serverUnreachable: Boolean = offline,
    ): SomethingNewFixture {
        val original = FakeCoreMediaProvider(supportsPlayReporting = true)
        var randomCalls = 0
        val similarRequests = mutableListOf<app.naviamp.domain.TrackId>()
        val provider = object : MediaProvider by original {
            override val capabilities = original.capabilities.copy(supportsTrackRadio = true)
            override val selectedMusicFolderIds = selectedFolders
            override fun tracksInSelectedLibraries(tracks: List<Track>) =
                tracks.filter { it.musicFolderId == null || selectedFolders.isEmpty() || it.musicFolderId in selectedFolders }
            override suspend fun randomSongs(limit: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Track> {
                randomGate?.await()
                return batches[randomCalls++.coerceAtMost(batches.lastIndex)]
            }
            override suspend fun instantMixTracks(trackId: app.naviamp.domain.TrackId, count: Int): List<Track> {
                similarRequests += trackId
                if (serverUnreachable) throw IllegalStateException("Request failed")
                return listOf(coreTrack("similar-1"), coreTrack("similar-2"))
            }
        }
        val pending = object : PendingProviderActionRepository by EmptyCorePendingProviderActionRepository {
            override fun pendingProviderActions(sourceId: String, limit: Int) = pendingListenIds.mapIndexed { index, id ->
                PendingProviderAction(index.toLong(), sourceId, app.naviamp.domain.provider.PendingActionSubmitListen,
                    id, longValue = 1L, createdAtEpochMillis = 1L)
            }
        }
        val effects = FakeCorePlaybackEffects()
        val defaults = fakeCoreServices(provider, playbackEffects = effects,
            providerActions = NaviampProviderActionController(pending))
        val services = defaults.copy(downloads = defaults.downloads.copy(
            storage = object : NaviampCoreDownloadStoragePort by defaults.downloads.storage {
                override suspend fun availableDownloads(sourceId: String) =
                    downloads.map { NaviampCoreDownloadedTrack(it.id.value, it, 1_000) }
            },
        ))
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(sourceId = "source",
                    phase = if (offline) NaviampConnectionPhase.Offline else NaviampConnectionPhase.Connected),
                connectionInventory = NaviampCoreConnectionInventory(currentSourceId = "source"),
                product = state.product.copy(shell = state.product.shell.copy(
                    connectionSettings = state.product.shell.connectionSettings.copy(currentSourceId = "source"),
                )),
            )
        }
        val core = createTrackedCore(this, services, initialState = initial)
        return SomethingNewFixture(core, effects, { randomCalls }, similarRequests)
    }

    @Test
    fun generatedRadioRecentsReplayAndPersistThroughCore() = runTest {
        var now = 10L
        val candidate = coreTrack("radio-candidate")
        val provider = FakeCoreMediaProvider(additionalLibraryTracks = listOf(candidate))
        val effects = FakeCorePlaybackEffects()
        var persisted = listOf(libraryRecentRadioStream())
        val defaults = fakeCoreServices(provider)
        val core = createTrackedCore(
            scope = this,
            services = defaults.copy(
                clockEpochMillis = { now },
                playback = defaults.playback.copy(effects = effects),
                radio = defaults.radio.copy(
                    generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                        load = { persisted },
                        save = { persisted = it },
                    ),
                ),
            ),
        )

        core.execute(
            NaviampCoreCommand.Home.SelectRecentRadio(
                SharedMediaItemUi("library", "Library Radio", "Radio"),
            ),
        )

        val playedIds = effects.appliedQueues.last().tracks.map { it.id.value }
        assertEquals(setOf(provider.track.id.value, candidate.id.value), playedIds.toSet())
        assertEquals(listOf(playedIds.first()), effects.selections.single().tracks.map { it.id.value })
        assertEquals(1, persisted.size)
        val savedSession = persisted.first()
        assertEquals("library", savedSession.id)
        assertEquals(playedIds, savedSession.sessionTracks.map { it.id })
        assertEquals(
            listOf(savedSession.id),
            core.state.value.shell.home.content.recentRadioStreams.map { it.id },
        )
        val section = core.state.value.shell.home.content.collectionSections
            .single { it.id == app.naviamp.domain.settings.HomeSectionIds.RecentRadio }
        assertEquals(savedSession.id, section.items.first().mediaItem.id)
        assertEquals("2 tracks", section.items.first().mediaItem.subtitle)
        assertEquals(app.naviamp.ui.SharedHomeCollectionItemAction.SelectRecentRadio, section.items.first().action)

        core.execute(
            NaviampCoreCommand.Home.SelectRecentRadio(
                SharedMediaItemUi(savedSession.id, savedSession.label, "2 tracks"),
            ),
        )

        assertEquals(2, effects.selections.size)
        assertEquals(playedIds, effects.selections.last().tracks.map { it.id.value })
        assertEquals(1, persisted.size)
        repeat(5) {
            now++
            core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
                SharedMediaItemUi(savedSession.id, savedSession.label, "2 tracks"),
            ))
            assertEquals(1, persisted.size)
            assertEquals(savedSession.id, persisted.single().id)
            assertEquals(now, persisted.single().startedAtEpochMillis)
            assertEquals(listOf(savedSession.id), core.state.value.shell.home.content.recentRadioStreams.map { it.id })
        }
    }

    @Test
    fun sonicPlaybackUsesCoreTrackTransitionAfterInternetRadio() = runTest {
        val provider = FakeCoreMediaProvider(supportsSonicSimilarity = true)
        val effects = FakeCorePlaybackEffects()
        val services = fakeCoreServices(provider).let { defaults ->
            defaults.copy(playback = defaults.playback.copy(effects = effects))
        }
        val station = InternetRadioStation("radio", "Radio", "https://radio.example")
        val radioTrack = internetRadioTrack(station)
        val core = createTrackedCore(
            scope = this,
            services = services,
            initialState = NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = radioTrack,
                    currentStation = station,
                    queue = PlaybackQueue(listOf(radioTrack), 0),
                ),
            ),
        )

        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.ChangeStartQuery("start"),
            ),
        )
        core.execute(
            NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchStart),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.SelectStart(
                    core.state.value.shell.sonicPathBuilder.startSuggestions.single(),
                ),
            ),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.ChangeEndQuery("end"),
            ),
        )
        core.execute(
            NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchEnd),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.SelectEnd(
                    core.state.value.shell.sonicPathBuilder.endSuggestions.single(),
                ),
            ),
        )
        core.execute(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Build))
        core.execute(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Play))

        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals("start", core.state.value.shell.nowPlaying?.id)
        assertTrue(core.state.value.shell.nowPlaying?.isLive == false)
        assertEquals(listOf("start"), effects.selections.map { it.current?.id?.value })
    }

    @Test
    fun successfulConnectionPopulatesProviderBackedScreensWithoutHostRefreshCommands() = runTest {
        val provider = FakeCoreMediaProvider(supportsSonicSimilarity = true)
        var persistedRadio = listOf(libraryRecentRadioStream().copy(sourceId = "source-1"))
        val record = NaviampCoreSavedConnectionRecord(
            id = "source-1",
            displayName = "Home Music",
            serverUrl = "https://music.example",
            username = "demo",
        )
        val defaults = fakeCoreServices(provider)
        val services = defaults.copy(
            connection = object : NaviampCoreProviderSessionPort {
                override suspend fun connect(
                    request: NaviampCoreConnectionRequest,
                    plan: app.naviamp.app.NaviampConnectionAttemptPlan,
                ) = NaviampCoreConnectedSession(
                    sourceId = record.id,
                    displayName = record.displayName,
                    inventory = NaviampCoreConnectionInventory(listOf(record), record.id),
                )

                override suspend fun editableConnection(id: String) = error("Not used")
                override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
                override suspend fun smartPlaylistProvider(password: String?) = provider
                override suspend fun refreshActiveSession() = true
                override suspend fun persistActiveSession() = Unit
                override suspend fun clearActiveSession() = Unit
            },
            radio = defaults.radio.copy(
                generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                    load = { persistedRadio },
                    save = { persistedRadio = it },
                ),
            ),
        )
        val core = createTrackedCore(this, services)
        core.dispatch(
            NaviampCoreCommand.Connection.ChangeForm(
                ConnectionFormState(
                    serverUrl = record.serverUrl,
                    username = record.username,
                    password = "secret",
                ),
            ),
        )

        core.execute(NaviampCoreCommand.Connection.Connect)
        advanceUntilIdle()

        assertEquals(listOf(provider.playlist.name), core.state.value.shell.home.content.playlists.map { it.title })
        assertEquals(listOf(provider.artist.name), core.state.value.shell.library.artists.items.map { it.title })
        assertEquals(listOf(provider.playlist.name), core.state.value.shell.playlists.playlists.map { it.title })
        assertTrue(core.state.value.shell.playback.sonicSimilarityAvailable)
        assertTrue(core.state.value.shell.capabilities.sonicSimilarity)
        assertEquals(listOf("library"), persistedRadio.map { it.id })
        assertEquals(
            listOf("library"),
            core.state.value.shell.home.content.recentRadioStreams.map { it.id },
        )
    }

    @Test
    fun restoredHostNeutralStateRehydratesNavigationConnectionPlaybackAndProductState() = runTest {
        val restoredTrack = coreTrack("restored")
        val core = createTrackedCore(
            scope = this,
            services = fakeCoreServices(),
            initialState = NaviampCoreInitialState(
                product = NaviampCoreState(
                    shell = NaviampAppShellUiState(
                        search = NaviampSearchScreenUi(query = "restored query"),
                    ),
                ),
                navigation = NaviampNavigationState(
                    route = NaviampRoute.Search,
                    lastContentRoute = NaviampRoute.Search,
                ),
                playback = NaviampLivePlaybackState(currentTrack = restoredTrack),
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "restored-source",
                    status = "Restored.",
                ),
            ),
        )

        assertEquals(SharedRoute.Search, core.state.value.shell.shellChrome.selectedRoute)
        assertEquals("restored query", core.state.value.shell.search.query)
        assertEquals("restored-source", core.state.value.shell.connectionSettings.currentSourceId)
        assertTrue(core.state.value.shell.connectionSettings.connection.connected)
        assertEquals(restoredTrack.id.value, core.state.value.shell.nowPlaying?.id)
    }

    @Test
    fun composedCoreOwnsEveryPreviouslyMissingMediaActionFamily() = runTest {
        val core = createTrackedCore(this, fakeCoreServices())
        val item = SharedMediaItemUi("missing", "Missing", "")
        val track = SharedTrackRowUi("missing", "Missing", "Artist")
        val row = SharedTrackRowActionRequest(track, SharedTrackRowAction.Select)
        val commands = listOf(
            NaviampCoreCommand.Home.SelectStation(SharedHomeStationUi("library", "Library", "")),
            NaviampCoreCommand.Home.SonicTrackAction(
                SharedHomeDiscoveryTrackActionRequest("row", track, SharedTrackRowAction.Select),
            ),
            NaviampCoreCommand.Home.RecentTrackAction(row),
            NaviampCoreCommand.Media.TrackAction(row),
            NaviampCoreCommand.Library.TrackAction(row),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.ToggleFavorite)),
            ),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.ToggleFavorite)),
            ),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.StartRadio)),
            ),
            NaviampCoreCommand.Detail.Album(
                NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.Play(shuffle = false)),
            ),
            NaviampCoreCommand.Detail.Artist(
                NaviampArtistDetailActionRequest(item, NaviampArtistDetailCommand.StartRadio),
            ),
            NaviampCoreCommand.Detail.ArtistAlbum(
                NaviampArtistAlbumActionRequest(item, NaviampArtistAlbumCommand.AddToQueue),
            ),
            NaviampCoreCommand.Detail.AlbumTrack(row),
            NaviampCoreCommand.Detail.ArtistPopularTrack(row),
            NaviampCoreCommand.Detail.PlaylistTrack(row),
        )

        commands.forEach { command ->
            assertEquals(NaviampCoreCommandResult.Completed, core.execute(command), command.toString())
        }
    }
}

internal fun fakeCoreServices(
    provider: MediaProvider? = null,
    playbackEffects: NaviampCorePlaybackEffectPort = FakeCorePlaybackEffects(),
    profiles: PlaybackProfileRepository = app.naviamp.domain.playback.EmptyPlaybackProfileRepository,
    providerActions: NaviampProviderActionController = NaviampProviderActionController(
        EmptyCorePendingProviderActionRepository,
    ),
) = NaviampCoreServices(
    content = NaviampCoreContentServices(
        providerSource = NaviampCoreMediaProviderSource { provider },
        homeDate = NaviampCoreHomeDateSource { HomeDate(2026, 202) },
        homeSupplement = NaviampCoreHomeSupplementSource { NaviampCoreHomeSupplement() },
        playlistSupplement = NaviampCorePlaylistBrowseSupplementSource { NaviampCorePlaylistBrowseSupplement() },
        artistDiscovery = NaviampCoreArtistDiscoveryServices(),
        externalUri = NaviampCoreExternalUriPort {},
    ),
    connection = object : NaviampCoreProviderSessionPort {
        override suspend fun connect(
            request: NaviampCoreConnectionRequest,
            plan: app.naviamp.app.NaviampConnectionAttemptPlan,
        ): NaviampCoreConnectedSession = error("Not used")

        override suspend fun editableConnection(id: String): NaviampCoreEditableConnection = error("Not used")
        override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
        override suspend fun smartPlaylistProvider(password: String?) = provider
        override suspend fun refreshActiveSession() = false
        override suspend fun persistActiveSession() = Unit
        override suspend fun clearActiveSession() = Unit
    },
    settings = NaviampCoreSettingsServices(
        interfaceSettings = NaviampCoreInterfaceSettingsStore {},
        cacheSettings = NaviampCoreCacheSettingsPort { it.normalized() },
        maintenance = NaviampCoreMaintenancePort { NaviampCoreMaintenanceResult("complete") },
        sync = fakeCoreSettingsSyncServices(),
    ),
    downloads = NaviampCoreDownloadServices(
        storage = object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
            override suspend fun pruneMissing(sourceId: String) = 0
            override suspend fun remove(sourceId: String, track: Track) = Unit
            override suspend fun deleteAll(sourceId: String) = 0
        },
        transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        },
        keepDownloaded = object : NaviampCoreKeepDownloadedPort {
            override fun policies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun toggle(policy: KeepDownloadedCollectionPolicy) = NaviampKeepDownloadedToggleResult.Enable
            override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
                NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
        },
        network = NaviampCoreMobileNetworkPort { false },
    ),
    playlists = NaviampCorePlaylistServices(
        history = NaviampCorePlaylistHistoryPort { current, _ -> current },
    ),
    radio = NaviampCoreRadioServices(
        playback = NaviampCoreInternetRadioPlaybackPort {},
        recents = object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
        generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
            load = { emptyList() },
            save = {},
        ),
    ),
    mixes = NaviampCoreMixServices(
        artist = { error("Artist mix service is lazy") },
        album = { error("Album mix service is lazy") },
        genre = { error("Genre mix service is lazy") },
    ),
    playback = NaviampCorePlaybackServices(
        effects = playbackEffects,
        settings = NaviampCorePlaybackSettingsPort { settings, _ -> settings },
        sidecars = object : NaviampCoreNowPlayingSidecarPort {
            override fun snapshot() = NaviampCoreNowPlayingSidecars()
            override suspend fun loadForTrack(track: Track) = Unit
            override suspend fun loadLyrics(track: Track) = Unit
            override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
        },
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) = Unit
        },
        sessions = NaviampPlaybackSessionController(object : PlaybackSessionRepository {
            override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
            override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
        }),
        profiles = profiles,
    ),
    providerActions = providerActions,
    clockEpochMillis = { 1_000L },
    favoritedAtIso8601 = { "2026-07-21T00:00:00Z" },
)

private fun NaviampCoreInitialState.withSource(sourceId: String) = copy(
    connection = NaviampConnectionRuntimeState(
        phase = NaviampConnectionPhase.Connected,
        sourceId = sourceId,
    ),
    product = product.copy(
        shell = product.shell.copy(
            connectionSettings = product.shell.connectionSettings.copy(currentSourceId = sourceId),
        ),
    ),
)

private fun singlePlaybackProfile(
    capturedTarget: PlaybackProfileTarget,
    profile: PlaybackProfile,
) = object : PlaybackProfileRepository {
    override fun playbackProfile(sourceId: String, target: PlaybackProfileTarget) =
        profile.takeIf { target == capturedTarget }

    override fun playbackProfiles(sourceId: String) = listOf(PlaybackProfileAssignment(capturedTarget, profile))

    override fun savePlaybackProfile(
        sourceId: String,
        target: PlaybackProfileTarget,
        profile: PlaybackProfile?,
    ) = Unit
}

private object EmptyCorePendingProviderActionRepository : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

private class RecordingCorePendingProviderActionRepository : PendingProviderActionRepository {
    val enqueued = mutableListOf<String>()

    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) {
        enqueued += "$sourceId:$actionType:$entityId"
    }

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

private fun fakeCoreSettingsSyncServices(): NaviampCoreSettingsSyncServices {
    var runtime = app.naviamp.domain.settings.SettingsSyncRuntimeState()
    return NaviampCoreSettingsSyncServices(
        controller = app.naviamp.app.NaviampSettingsSyncController(
            deviceId = "test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 1L },
            snapshot = { app.naviamp.domain.settings.SettingsSyncLocalSnapshot() },
            applyDocument = {},
        ),
        port = object : NaviampCoreSettingsSyncPort {
            private var configuration = NaviampCoreSettingsSyncConfiguration(directoryPath = "/sync")
            override fun configuration() = configuration
            override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
                this.configuration = configuration
            }
            override suspend fun readDocument(directoryPath: String) = null
            override suspend fun readDocumentFile(filePath: String) = null
            override suspend fun writeDocument(
                directoryPath: String,
                document: app.naviamp.domain.settings.SettingsSyncDocument,
            ) = "settings.json"
            override suspend fun chooseDirectory(currentPath: String?, title: String) = "/sync"
            override suspend fun chooseDocument(currentPath: String?, title: String) = "/sync/naviamp-settings.json"
            override fun defaultDirectory() = "/home"
            override val available = true
        },
    )
}

internal class FakeCorePlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities()
    override val playbackSource = PlaybackSource.ProviderStream
    val selections = mutableListOf<PlaybackQueue>()
    val appliedQueues = mutableListOf<PlaybackQueue>()
    val navigationCommands = mutableListOf<PlaybackQueueNavigationCommand>()
    val volumes = mutableListOf<Int>()
    var startOrRestoreCount = 0
    var resumeCount = 0
    var pauseCount = 0
    var observer: NaviampCorePlaybackObserver? = null
    override fun attach(observer: NaviampCorePlaybackObserver) {
        this.observer = observer
    }
    override fun pause() { pauseCount += 1 }
    override fun resume() {
        resumeCount += 1
    }
    override fun startOrRestore(): Boolean {
        startOrRestoreCount += 1
        return false
    }
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) {
        volumes += percent
    }
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        appliedQueues += queue
    }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) {
        navigationCommands += command
    }
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        selections += queue.jumpTo(index)
    }
}

private fun coreTrack(id: String) = Track(
    id = app.naviamp.domain.TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
