package app.naviamp.testkit

import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.domain.provider.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlin.math.abs

/** Test stimuli, not product policy: native hosts post the corresponding OS notifications. */
enum class NaviampAudioRecoveryStimulus {
    Begin, EndMayResume, EndDenied, EndMissingOptions, Disconnect, NewRoute, OverrideRoute, UnknownRoute, Malformed, Activation, ApplicationDeactivation,
}

interface NaviampAudioRecoveryHost<Artwork> {
    val engine: ReleasablePlaybackEngine
    val surface: NaviampExternalPlaybackSurface<Artwork>
    suspend fun writeWave(bytes: ByteArray): String
    suspend fun notify(stimulus: NaviampAudioRecoveryStimulus)
    suspend fun deactivateAudioSession()
    fun nativePositionSeconds(): Double?
    val verifiesSystemInterruption: Boolean get() = false
    val usesActivationLifecycle: Boolean get() = false
    suspend fun requestSystemInterruption() { error("No system interruption fixture is mounted") }
}

/** Real Core transport, external-surface lifetime and native clock assertions shared by thin hosts. */
suspend fun <Artwork> verifyNaviampAudioRecovery(host: NaviampAudioRecoveryHost<Artwork>) = coroutineScope {
    val url = host.writeWave(naviampDiscoveryPreviewFixtureWave(90))
    val provider = AudioRecoveryProvider(url)
    val defaults = naviampCoreTestServices(provider)
    val saved = mutableListOf<PlaybackSessionSettings>()
    val services = naviampCoreStreamingPlaybackServices(this, host.engine, defaults.content.providerSource,
        PlaybackSettings(volumePercent = 0), {}, object : PlaybackSessionRepository {
            override fun loadPlaybackSession(sourceId: String?) = null
            override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) { session?.let(saved::add) }
        }, {})
    val queue = PlaybackQueue(listOf(provider.track.copy(id = TrackId("before")), provider.track,
        provider.track.copy(id = TrackId("after"))), 1)
    val core = NaviampCore.create(this, defaults.copy(playback = services), NaviampCoreInitialState(
        playback = NaviampLivePlaybackState(currentTrack = queue.tracks.first(), queue = queue.copy(currentIndex = 0)),
        connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "audio-recovery")))
    val bridge = core.externalPlaybackBridge()
    val failures = mutableListOf<Throwable>()
    val events = mutableListOf<NaviampExternalPlaybackAudioEvent>()
    val observedSurface = object : NaviampExternalPlaybackSurface<Artwork> by host.surface {
        override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean,
                            audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) = host.surface.attach(command) {
            events += it; audioEvent(it)
        }
    }
    val session = NaviampExternalPlaybackSurfaceSession(this, bridge, bridge.snapshots, {}, { null }, observedSurface,
        failures::add, loadLabels = { NaviampExternalPlaybackLabels("Test queue",
            mapOf(NaviampExternalPlaybackAction.Favorite to "Test favorite")) })
    suspend fun await(description: String, predicate: () -> Boolean) {
        try { withTimeout(10_000) { while (!predicate()) { check(failures.isEmpty()) { failures.toString() }; delay(20) } } }
        catch (timeout: TimeoutCancellationException) { throw AssertionError("Audio recovery: $description; snapshot=${bridge.snapshot()}; native=${host.nativePositionSeconds()}", timeout) }
    }
    suspend fun command(command: NaviampExternalPlaybackSurfaceCommand) {
        // Native command translation is covered by its adapter tests; these intents enter the real shared bridge.
        when (command) {
            NaviampExternalPlaybackSurfaceCommand.Play -> bridge.play()
            NaviampExternalPlaybackSurfaceCommand.Pause -> bridge.pause()
            NaviampExternalPlaybackSurfaceCommand.Stop -> bridge.stop()
            else -> error("Unexpected test command")
        }
    }
    suspend fun playing() {
        await("playing") { bridge.snapshot().state == NaviampExternalPlaybackState.Playing }
        val before = host.nativePositionSeconds() ?: 0.0
        await("native clock advances") { (host.nativePositionSeconds() ?: 0.0) > before + 0.15 }
    }
    suspend fun paused() {
        await("paused") { bridge.snapshot().state == NaviampExternalPlaybackState.Paused }
        val before = checkNotNull(host.nativePositionSeconds())
        repeat(6) {
            delay(100)
            check(bridge.snapshot().state == NaviampExternalPlaybackState.Paused)
            check(abs(checkNotNull(host.nativePositionSeconds()) - before) < 0.08) { "Paused native clock advanced" }
        }
    }
    fun verifyQueue() {
        check(core.state.value.shell.nowPlaying?.queueSnapshot == queue)
        check(saved.isNotEmpty()) { "The selected queue never reached shared session persistence" }
        check(saved.all { it.toTracks().map(Track::id) == queue.tracks.map(Track::id) && it.currentIndex == 1 }) {
            "Recovery changed persisted queue identity: ${saved.map { it.currentIndex to it.toTracks().map(Track::id) }}"
        }
        check(failures.isEmpty()) { failures.toString() }
    }
    fun passed(name: String) { verifyQueue(); println("audio recovery passed: $name") }
    try {
        // An initial UI snapshot is not an engine lease. Select through Core to resolve and own the queue.
        core.execute(NaviampCoreCommand.NowPlaying.Selection(NowPlayingSelectionActionRequest(
            checkNotNull(core.state.value.shell.nowPlaying).upNext.first(), NowPlayingSelectionAction.SelectQueueItem)))
        playing()
        // Setup legitimately persisted the preceding selection. Start recovery observations after
        // the selected lease is active, and require a fresh real pause to persist its current queue.
        saved.clear()
        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        if (host.verifiesSystemInterruption) {
            events.clear()
            host.requestSystemInterruption()
            paused()
            check(events.any { it is NaviampExternalPlaybackAudioEvent.SessionBecameInactive &&
                it.source == NaviampAudioSessionDeactivationSource.System }) { "No system-created deactivation context: $events" }
            val interruptedPosition = checkNotNull(host.nativePositionSeconds())
            playing()
            check(events.any { it is NaviampExternalPlaybackAudioEvent.ResumptionRecommended &&
                it.recommendation == NaviampAudioSessionResumptionRecommendation.Resume }) { "No system-created resumption recommendation: $events" }
            check(checkNotNull(host.nativePositionSeconds()) >= interruptedPosition - 0.1)
            passed("system-generated-lifecycle")
        }
        for (ignored in listOf(NaviampAudioRecoveryStimulus.NewRoute, NaviampAudioRecoveryStimulus.OverrideRoute,
            NaviampAudioRecoveryStimulus.UnknownRoute, NaviampAudioRecoveryStimulus.Malformed)) host.notify(ignored)
        playing(); passed("unrelated-notifications")

        host.notify(NaviampAudioRecoveryStimulus.Begin)
        host.notify(NaviampAudioRecoveryStimulus.Begin)
        paused()
        val retained = checkNotNull(host.nativePositionSeconds())
        if (!host.usesActivationLifecycle) host.deactivateAudioSession()
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume)
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume)
        playing()
        check(checkNotNull(host.nativePositionSeconds()) >= retained - 0.1) { "Interruption recovery restarted the track" }
        passed("allowed-interruption")

        for (ending in listOf(NaviampAudioRecoveryStimulus.EndDenied, NaviampAudioRecoveryStimulus.EndMissingOptions)) {
            host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
            host.notify(ending); paused()
            host.notify(NaviampAudioRecoveryStimulus.EndMayResume)
            if (host.usesActivationLifecycle && ending == NaviampAudioRecoveryStimulus.EndMissingOptions) playing()
            else { paused(); command(NaviampExternalPlaybackSurfaceCommand.Play); playing() }
        }
        passed("denied-interruption")

        command(NaviampExternalPlaybackSurfaceCommand.Pause); paused()
        host.notify(NaviampAudioRecoveryStimulus.Begin)
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        passed("already-paused")

        if (host.usesActivationLifecycle) {
            host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
            host.deactivateAudioSession()
            host.notify(NaviampAudioRecoveryStimulus.ApplicationDeactivation)
            host.notify(NaviampAudioRecoveryStimulus.Activation)
            host.notify(NaviampAudioRecoveryStimulus.EndMayResume); paused()
            command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
            passed("application-deactivation")
        }

        host.notify(NaviampAudioRecoveryStimulus.Disconnect); paused()
        host.notify(NaviampAudioRecoveryStimulus.NewRoute)
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        passed("route-disconnect")

        host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
        host.notify(NaviampAudioRecoveryStimulus.Disconnect)
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        passed("disconnect-during-interruption")

        host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
        command(NaviampExternalPlaybackSurfaceCommand.Stop)
        await("stopped") { bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume)
        delay(400)
        check(bridge.snapshot().state == NaviampExternalPlaybackState.Idle) { "A late end event restarted stopped playback" }
        passed("stopped-source")

        command(NaviampExternalPlaybackSurfaceCommand.Play); playing()
        host.notify(NaviampAudioRecoveryStimulus.Begin); paused()
        session.close(); session.close()
        host.notify(NaviampAudioRecoveryStimulus.EndMayResume)
        host.notify(NaviampAudioRecoveryStimulus.Disconnect); paused()
        passed("closed-session")
    } finally {
        session.close()
        core.close()
        services.effects.stop()
        host.engine.release()
    }
}

private class AudioRecoveryProvider(private val url: String) : MediaProvider {
    override val id = ProviderId("audio-recovery-contract")
    override val displayName = "Audio recovery fixture"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val track = ProviderDiscoverySample("audio-recovery", "Audio recovery fixture").playbackTrack("Fixture")
        .copy(id = TrackId("audio-recovery"), durationSeconds = 90)
    override suspend fun streamUrl(request: StreamRequest) = url
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
