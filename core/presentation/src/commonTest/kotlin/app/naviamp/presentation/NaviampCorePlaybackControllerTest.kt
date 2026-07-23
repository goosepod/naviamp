package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackSessionController
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
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.PlaybackReportState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCorePlaybackControllerTest {
    @Test
    fun nativeProgressPersistsTheCanonicalCoreQueueAndPosition() = runTest {
        val fixture = playbackFixture(this)
        fixture.controller.attachNativePlayback()

        fixture.effects.observer?.onProgressChanged(PlaybackProgress(31.0, 180.0))

        val saved = fixture.sessionRepository.sessions.getValue("source")!!
        assertEquals(listOf("one", "two", "three", "four", "five"), saved.toTracks().map { it.id.value })
        assertEquals(1, saved.currentIndex)
        assertEquals(31.0, saved.positionSeconds)
    }

    @Test
    fun restoredSessionRehydratesCoreQueueCurrentTrackAndResumePosition() = runTest {
        val fixture = playbackFixture(this)
        val restoredTracks = listOf(playbackTrack("remembered-one"), playbackTrack("remembered-two"))
        fixture.sessionRepository.sessions["source"] = PlaybackSessionSettings.fromTracks(
            restoredTracks,
            currentIndex = 1,
            positionSeconds = 42.0,
        )

        fixture.controller.restoreSession("source")

        assertEquals("remembered-two", fixture.live.state.value.currentTrack?.id?.value)
        assertEquals(restoredTracks, fixture.live.state.value.queue.tracks)
        assertEquals(42.0, fixture.live.state.value.progress.positionSeconds)
        assertEquals(42.0, fixture.effects.restoredStartPositionSeconds)
        assertEquals("remembered-two", fixture.sidecars.loadedTracks.single())
        assertEquals(0, fixture.effects.starts)
    }

    @Test
    fun duplicateNativeFinishedCallbacksAdvanceTheSharedQueueOnlyOnce() = runTest {
        val fixture = playbackFixture(this)
        fixture.controller.attachNativePlayback()

        fixture.effects.observer?.onStateChanged(PlaybackState.Finished)
        fixture.effects.observer?.onStateChanged(PlaybackState.Finished)

        assertEquals("three", fixture.live.state.value.currentTrack?.id?.value)
        assertEquals(
            listOf<PlaybackQueueNavigationCommand>(PlaybackQueueNavigationCommand.Next),
            fixture.effects.navigation,
        )
    }

    @Test
    fun nativePlaybackObservationsReportNowPlayingAndProgressThroughCore() = runTest {
        val fixture = playbackFixture(this)
        fixture.controller.attachNativePlayback()

        fixture.effects.observer?.onStateChanged(PlaybackState.Playing)
        fixture.effects.observer?.onProgressChanged(PlaybackProgress(30.0, 180.0))
        advanceUntilIdle()

        assertEquals(listOf("two"), fixture.provider.nowPlayingReports)
        assertEquals(listOf("two"), fixture.sidecars.loadedTracks)
        assertEquals(
            listOf("two:playing:20.0"),
            fixture.provider.stateReports,
        )
    }

    @Test
    fun transportQueueVolumeRepeatAndShuffleAreResolvedByCore() = runTest {
        val fixture = playbackFixture(this)

        fixture.controller.execute(playbackCommand(NowPlayingPlaybackAction.Pause))
        fixture.controller.execute(playbackCommand(NowPlayingPlaybackAction.Previous))
        fixture.controller.execute(playbackCommand(NowPlayingPlaybackAction.Next))
        fixture.controller.execute(playbackCommand(NowPlayingPlaybackAction.ToggleShuffle))
        fixture.controller.execute(playbackCommand(NowPlayingPlaybackAction.CycleRepeatMode))
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.ChangeVolume, volumePercent = 42),
            ),
        )

        assertEquals(1, fixture.effects.pauses)
        assertEquals(
            listOf(PlaybackQueueNavigationCommand.RestartCurrent, PlaybackQueueNavigationCommand.Next),
            fixture.effects.navigation,
        )
        assertEquals(fixture.live.state.value.queue.current, fixture.live.state.value.currentTrack)
        assertTrue(fixture.live.state.value.shuffledUpNextSnapshot != null)
        assertEquals(RepeatMode.Queue, fixture.live.state.value.repeatMode)
        assertEquals(listOf(42), fixture.effects.volumes)
        assertEquals(42, fixture.savedSettings.single().volumePercent)
        assertEquals(42, fixture.store.state.value.shell.nowPlaying?.volumePercent)
    }

    @Test
    fun seekQueueMutationsAndSaveAreCoreTransactions() = runTest {
        val fixture = playbackFixture(this)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Seek, seekSeconds = 33.0),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Queue(
                NowPlayingQueueActionRequest(NowPlayingQueueAction.RemoveFromQueue, queueIndex = 4),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Queue(
                NowPlayingQueueActionRequest(NowPlayingQueueAction.SaveQueueAsPlaylist, playlistName = "Queue Save"),
            ),
        )

        assertEquals(listOf(33.0), fixture.effects.seeks)
        assertEquals(listOf("one", "two", "three", "four"), fixture.live.state.value.queue.tracks.map { it.id.value })
        assertEquals(listOf("Queue Save:one,two,three,four"), fixture.provider.created)
        assertEquals("Saved Queue Save.", fixture.store.state.value.shell.nowPlaying?.playlistActionStatus)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Queue(
                NowPlayingQueueActionRequest(NowPlayingQueueAction.EmptyQueue),
            ),
        )
        assertEquals(
            listOf(fixture.live.state.value.currentTrack?.id?.value),
            fixture.live.state.value.queue.tracks.map { it.id.value },
        )
        assertEquals("two", fixture.live.state.value.currentTrack?.id?.value)
        assertEquals(PlaybackState.Playing, fixture.live.state.value.playbackState)
        assertEquals(0, fixture.effects.stops)
        assertEquals(listOf("two"), fixture.effects.queues.last().tracks.map { it.id.value })
    }

    @Test
    fun sleepTimerStateAndPresentationAreOwnedByCore() = runTest {
        val fixture = playbackFixture(this)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.SleepTimer(
                NowPlayingSleepTimerActionRequest(
                    NowPlayingSleepTimerAction.Select,
                    app.naviamp.domain.playback.SleepTimerRequest.DurationMinutes(10),
                ),
            ),
        )
        assertTrue(fixture.store.state.value.shell.nowPlaying?.sleepTimer?.active == true)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.SleepTimer(
                NowPlayingSleepTimerActionRequest(NowPlayingSleepTimerAction.Cancel),
            ),
        )
        assertFalse(fixture.store.state.value.shell.nowPlaying?.sleepTimer?.active == true)

        fixture.controller.expireSleepTimer()
        assertEquals(1, fixture.effects.stops)
        assertEquals(PlaybackState.Stopped, fixture.live.state.value.playbackState)
        assertEquals("Sleep timer stopped playback.", fixture.store.state.value.overlays.status)
    }

    @Test
    fun databaseResetStopsPlaybackAndRemovesTheQueueAndPersistedSession() = runTest {
        val fixture = playbackFixture(this)
        fixture.sessionRepository.sessions["source"] = PlaybackSessionSettings.fromTracks(
            fixture.live.state.value.queue.tracks,
            currentIndex = 1,
        )

        fixture.controller.resetAfterDatabaseClear()

        assertEquals(1, fixture.effects.stops)
        assertEquals(PlaybackState.Stopped, fixture.live.state.value.playbackState)
        assertEquals(null, fixture.live.state.value.currentTrack)
        assertTrue(fixture.live.state.value.queue.tracks.isEmpty())
        assertEquals(null, fixture.sessionRepository.sessions["source"])
        assertTrue(fixture.store.state.value.shell.nowPlaying?.upNext.orEmpty().isEmpty())
    }
}

private data class PlaybackFixture(
    val store: NaviampCoreStateStore,
    val provider: PlaybackTestProvider,
    val live: NaviampLivePlaybackController,
    val effects: PlaybackTestEffects,
    val sidecars: PlaybackTestSidecars,
    val sessionRepository: RecordingPlaybackSessionRepository,
    val controller: NaviampCorePlaybackController,
    val savedSettings: List<PlaybackSettings>,
)

private fun playbackFixture(scope: kotlinx.coroutines.CoroutineScope): PlaybackFixture {
    val tracks = listOf(
        playbackTrack("one"),
        playbackTrack("two"),
        playbackTrack("three"),
        playbackTrack("four"),
        playbackTrack("five"),
    )
    val provider = PlaybackTestProvider()
    val store = NaviampCoreStateStore().also { stateStore ->
        stateStore.updateShell { shell ->
            shell.copy(connectionSettings = shell.connectionSettings.copy(currentSourceId = "source"))
        }
    }
    val live = NaviampLivePlaybackController(
        NaviampLivePlaybackState(
            currentTrack = tracks[1],
            queue = PlaybackQueue(tracks, currentIndex = 1),
            progress = PlaybackProgress(20.0, 180.0),
            playbackState = PlaybackState.Playing,
        ),
    )
    val queue = NaviampPlaybackQueueCoordinator(live)
    val effects = PlaybackTestEffects()
    val sidecars = PlaybackTestSidecars()
    val presenter = NaviampCoreNowPlayingPresenter(store, { provider }, live, queue, effects, sidecars)
    val saved = mutableListOf<PlaybackSettings>()
    val sessionRepository = RecordingPlaybackSessionRepository()
    val controller = NaviampCorePlaybackController(
        scope = scope,
        stateStore = store,
        providerSource = { provider },
        playback = live,
        queue = queue,
        effects = effects,
        settings = NaviampCorePlaybackSettingsPort { settings, _ ->
            saved += settings
            settings
        },
        sidecars = sidecars,
        sessions = NaviampPlaybackSessionController(sessionRepository),
        presenter = presenter,
        nowEpochMillis = { 10_000L },
    )
    presenter.publish()
    return PlaybackFixture(store, provider, live, effects, sidecars, sessionRepository, controller, saved)
}

private class PlaybackTestEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities(supportsVisualizer = true)
    override val playbackSource = PlaybackSource.ProviderStream
    var pauses = 0
    var resumes = 0
    var starts = 0
    var stops = 0
    val seeks = mutableListOf<Double>()
    val volumes = mutableListOf<Int>()
    val navigation = mutableListOf<PlaybackQueueNavigationCommand>()
    val queues = mutableListOf<PlaybackQueue>()
    var restoredStartPositionSeconds: Double? = null
    var observer: NaviampCorePlaybackObserver? = null

    override fun attach(observer: NaviampCorePlaybackObserver) {
        this.observer = observer
    }

    override fun pause() { pauses += 1 }
    override fun resume() { resumes += 1 }
    override fun startOrRestore(): Boolean { starts += 1; return true }
    override fun seek(positionSeconds: Double) { seeks += positionSeconds }
    override fun replayCurrent(positionSeconds: Double) { seeks += positionSeconds }
    override fun setVolume(percent: Int) { volumes += percent }
    override fun stop() { stops += 1 }
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) { queues += queue }
    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        queues += queue
        restoredStartPositionSeconds = startPositionSeconds
    }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) { navigation += command }
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
}

private class RecordingPlaybackSessionRepository : PlaybackSessionRepository {
    val sessions = mutableMapOf<String?, PlaybackSessionSettings?>()
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = sessions[sourceId]
    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        sessions[sourceId] = session
    }
}

private class PlaybackTestSidecars : NaviampCoreNowPlayingSidecarPort {
    val loadedTracks = mutableListOf<String>()
    override fun snapshot() = NaviampCoreNowPlayingSidecars()
    override suspend fun loadForTrack(track: Track) { loadedTracks += track.id.value }
    override suspend fun loadLyrics(track: Track) = Unit
    override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
}

private class PlaybackTestProvider : MediaProvider {
    override val id = ProviderId("playback")
    override val displayName = "Playback"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = true,
        supportsAlbumRadio = true,
        supportsTrackRadio = true,
        supportsPlayReporting = true,
    )
    val created = mutableListOf<String>()
    val nowPlayingReports = mutableListOf<String>()
    val stateReports = mutableListOf<String>()
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        created += "$name:${trackIds.joinToString(",") { it.value }}"
        return Playlist("created", name, trackIds.size)
    }
    override suspend fun reportNowPlaying(trackId: TrackId) {
        nowPlayingReports += trackId.value
    }
    override suspend fun reportPlaybackState(
        trackId: TrackId,
        state: PlaybackReportState,
        positionSeconds: Double?,
    ) {
        stateReports += "${trackId.value}:${state.providerValue}:$positionSeconds"
    }
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun playbackCommand(action: NowPlayingPlaybackAction) =
    NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(action))

private fun playbackTrack(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
