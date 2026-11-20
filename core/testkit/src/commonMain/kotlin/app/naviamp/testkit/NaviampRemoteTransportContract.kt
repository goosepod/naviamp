package app.naviamp.testkit

import app.naviamp.app.*
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.provider.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlin.math.abs

/** Commands enter through the callback registered by the real surface session, never the bridge directly. */
suspend fun <Artwork> verifyNaviampRemoteTransport(host: NaviampAudioRecoveryHost<Artwork>) = coroutineScope {
    val provider = RemoteTransportProvider(host.writeWave(naviampDiscoveryPreviewFixtureWave(90)))
    val defaults = naviampCoreTestServices(provider)
    val saved = mutableListOf<PlaybackSessionSettings>()
    val services = naviampCoreStreamingPlaybackServices(this, host.engine, defaults.content.providerSource,
        PlaybackSettings(volumePercent = 0), {}, object : PlaybackSessionRepository {
            override fun loadPlaybackSession(sourceId: String?) = null
            override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) { session?.let(saved::add) }
        }, {})
    val queue = PlaybackQueue(provider.queue, 0)
    val core = NaviampCore.create(this, defaults.copy(playback = services), NaviampCoreInitialState(
        playback = NaviampLivePlaybackState(currentTrack = queue.tracks.first(), queue = queue),
        connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "remote-transport")))
    val bridge = core.externalPlaybackBridge()
    lateinit var send: (NaviampExternalPlaybackSurfaceCommand) -> Boolean
    val publications = mutableListOf<NaviampExternalPlaybackSnapshot>()
    val failures = mutableListOf<Throwable>()
    val surface = object : NaviampExternalPlaybackSurface<Artwork> by host.surface {
        override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean,
                            audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) {
            send = command
            host.surface.attach(command, audioEvent)
        }
        override fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>,
                             artwork: Artwork?, labels: NaviampExternalPlaybackLabels) {
            publications += snapshot
            host.surface.publish(snapshot, actions, artwork, labels)
        }
    }
    val session = NaviampExternalPlaybackSurfaceSession(this, bridge, bridge.snapshots, bridge::toggleFavorite,
        { null }, surface, failures::add, loadLabels = { NaviampExternalPlaybackLabels("Fixture queue",
            mapOf(NaviampExternalPlaybackAction.Favorite to "Fixture favorite")) })
    suspend fun await(description: String, predicate: () -> Boolean) {
        try { withTimeout(10_000) { while (!predicate()) { check(failures.isEmpty()) { failures.toString() }; delay(20) } } }
        catch (timeout: TimeoutCancellationException) { throw AssertionError("Remote transport: $description; snapshot=${bridge.snapshot()}", timeout) }
    }
    suspend fun command(value: NaviampExternalPlaybackSurfaceCommand) { check(send(value)) { "Rejected $value: ${bridge.snapshot()}" } }
    suspend fun playing(index: Int = bridge.snapshot().currentQueueIndex) {
        await("playing queue item $index") { bridge.snapshot().state == NaviampExternalPlaybackState.Playing && bridge.snapshot().currentQueueIndex == index }
        val before = host.nativePositionSeconds() ?: 0.0
        await("advancing output clock") { (host.nativePositionSeconds() ?: 0.0) > before + 0.15 }
    }
    suspend fun paused() {
        await("paused") { bridge.snapshot().state == NaviampExternalPlaybackState.Paused }
        val before = checkNotNull(host.nativePositionSeconds())
        repeat(5) { delay(100); check(bridge.snapshot().state == NaviampExternalPlaybackState.Paused)
            check(abs(checkNotNull(host.nativePositionSeconds()) - before) < 0.08) { "Paused native output advanced" } }
    }
    suspend fun persisted() {
        await("persisted selected queue") { saved.lastOrNull()?.let { it.toTracks().map(Track::id) == provider.queue.map(Track::id) &&
            it.currentIndex == bridge.snapshot().currentQueueIndex } == true }
    }
    fun passed(name: String) { check(failures.isEmpty()); println("remote transport passed: $name") }
    try {
        // Resolve an actual engine lease rather than relying on the initial shell snapshot.
        core.execute(NaviampCoreCommand.NowPlaying.Selection(NowPlayingSelectionActionRequest(
            checkNotNull(core.state.value.shell.nowPlaying).upNext.first(), NowPlayingSelectionAction.SelectQueueItem)))
        playing(1)
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused(); persisted()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        command(NaviampExternalPlaybackSurfaceCommand.Toggle); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Toggle); playing()
        passed("play-pause-toggle")

        command(NaviampExternalPlaybackSurfaceCommand.Next); playing(2)
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused(); persisted()
        check(!send(NaviampExternalPlaybackSurfaceCommand.Next)) { "Queue-end Next was accepted" }
        command(NaviampExternalPlaybackSurfaceCommand.Previous); playing(1)
        command(NaviampExternalPlaybackSurfaceCommand.Previous); playing(0)
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused(); persisted()
        check(!send(NaviampExternalPlaybackSurfaceCommand.Previous)) { "Queue-start Previous was accepted" }
        passed("next-previous-boundaries")

        command(NaviampExternalPlaybackSurfaceCommand.Seek(12.5))
        await("paused seek reaches native output") { abs((host.nativePositionSeconds() ?: -1.0) - 12.5) < 0.12 }
        paused()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing(0)
        command(NaviampExternalPlaybackSurfaceCommand.Seek(30.25))
        await("playing seek reaches native output") { (host.nativePositionSeconds() ?: 0.0) in 30.20..32.0 }
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused()
        val position = checkNotNull(host.nativePositionSeconds())
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 91.0, Long.MAX_VALUE.toDouble()))
            check(!send(NaviampExternalPlaybackSurfaceCommand.Seek(invalid)))
        delay(300)
        check(abs(checkNotNull(host.nativePositionSeconds()) - position) < 0.08)
        await("published seek and pause") { publications.lastOrNull()?.let { it.state == NaviampExternalPlaybackState.Paused &&
            abs((it.positionMillis ?: 0L) - 30_250L) < 200 } == true }
        passed("seek-and-invalid-input")

        for (mode in listOf(NaviampRepeatMode.Track, NaviampRepeatMode.Queue, NaviampRepeatMode.Off)) {
            command(NaviampExternalPlaybackSurfaceCommand.Repeat(mode))
            await("repeat $mode") { bridge.snapshot().repeatMode == mode }
            command(NaviampExternalPlaybackSurfaceCommand.Repeat(mode)) // setting an existing mode is idempotent
            delay(100); check(bridge.snapshot().repeatMode == mode)
        }
        command(NaviampExternalPlaybackSurfaceCommand.Shuffle(true))
        await("shuffle on") { bridge.snapshot().shuffleActive }
        command(NaviampExternalPlaybackSurfaceCommand.Shuffle(true)); delay(100)
        check(bridge.snapshot().shuffleActive)
        command(NaviampExternalPlaybackSurfaceCommand.Shuffle(false))
        await("shuffle off restores queue") { !bridge.snapshot().shuffleActive }
        check(core.state.value.shell.nowPlaying?.queueSnapshot?.tracks?.map(Track::id) == provider.queue.map(Track::id))
        paused(); persisted(); passed("shuffle-repeat")

        check(!send(NaviampExternalPlaybackSurfaceCommand.Favorite)) { "Unsupported provider Favorite was accepted" }
        passed("unsupported-favorite")

        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        command(NaviampExternalPlaybackSurfaceCommand.Stop)
        await("stopped") { bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume); delay(300)
        check(bridge.snapshot().state == NaviampExternalPlaybackState.Idle)
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        passed("stop-restart")

        // Failed URL resolution must remain recoverable by the same registered remote callback.
        provider.failNext = true
        command(NaviampExternalPlaybackSurfaceCommand.Next)
        await("failed source") { provider.failures == 1 && bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
        val failedIndex = bridge.snapshot().currentQueueIndex
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing(failedIndex)
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused(); persisted()
        passed("failed-source-recovery")

        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Pause); delay(100)
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Toggle); playing()
        passed("explicit-intent-cancels-resume")

        val late = send
        session.close(); session.close()
        for (value in listOf(NaviampExternalPlaybackSurfaceCommand.Play, NaviampExternalPlaybackSurfaceCommand.Pause,
            NaviampExternalPlaybackSurfaceCommand.Toggle, NaviampExternalPlaybackSurfaceCommand.Stop,
            NaviampExternalPlaybackSurfaceCommand.Next, NaviampExternalPlaybackSurfaceCommand.Previous,
            NaviampExternalPlaybackSurfaceCommand.Seek(0.0), NaviampExternalPlaybackSurfaceCommand.Shuffle(true),
            NaviampExternalPlaybackSurfaceCommand.Repeat(NaviampRepeatMode.Queue), NaviampExternalPlaybackSurfaceCommand.Favorite)) check(!late(value))
        passed("closed-session")
    } finally { session.close(); core.close(); services.effects.stop(); host.engine.release() }
}

private class RemoteTransportProvider(private val url: String) : MediaProvider {
    override val id = ProviderId("remote-transport-contract")
    override val displayName = "Remote transport fixture"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val queue = (0..2).map { index -> ProviderDiscoverySample("remote-$index", "Remote $index").playbackTrack("Fixture")
        .copy(id = TrackId("remote-$index"), durationSeconds = 90) }
    var failNext = false
    var failures = 0
    override suspend fun streamUrl(request: StreamRequest): String {
        if (failNext) { failNext = false; failures++; error("Expected remote transport fixture resolution failure") }
        return url
    }
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = emptyList<Playlist>()
    override suspend fun genres(limit: Int) = emptyList<Genre>()
    override fun coverArtUrl(coverArtId: String) = coverArtId
}
