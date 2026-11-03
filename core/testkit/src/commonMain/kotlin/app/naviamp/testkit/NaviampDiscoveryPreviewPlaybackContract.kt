package app.naviamp.testkit

import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.*
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/** Real Core commands, preview transport and queue/session isolation; hosts supply only audio effects. */
suspend fun verifyNaviampDiscoveryPreviewPlayback(
    engine: PlaybackEngine,
    mainUrl: String,
    shortPreviewUrl: String,
    longPreviewUrl: String,
    nativeMainPosition: () -> Double?,
) = coroutineScope {
    val provider = PreviewContractProvider(mainUrl, shortPreviewUrl, longPreviewUrl)
    val defaults = naviampCoreTestServices(provider).let { services -> services.copy(
        content = services.content.copy(homeSupplement = NaviampCoreHomeSupplementSource {
            NaviampCoreHomeSupplement(sourceId = "preview-source")
        }),
    ) }
    val sessions = mutableListOf<PlaybackSessionSettings>()
    val playbackServices = naviampCoreStreamingPlaybackServices(this, engine, defaults.content.providerSource,
        PlaybackSettings(volumePercent = 0), {}, object : PlaybackSessionRepository {
            override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
            override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
                session?.let { sessions += it }
            }
        }, {})
    val queue = PlaybackQueue(listOf(provider.owned("before"), provider.owned("current"), provider.owned("next")), 1)
    val core = NaviampCore.create(this, defaults.copy(playback = playbackServices),
        NaviampCoreInitialState(
            playback = NaviampLivePlaybackState(currentTrack = queue.tracks.first(), queue = queue.copy(currentIndex = 0)),
            connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "preview-source"),
            connectionInventory = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord(
                "preview-source", "Fixture", "https://fixture.invalid", "fixture")), "preview-source"),
        ))
    suspend fun <T> phase(name: String, block: suspend () -> T): T = try { block() }
    catch (timeout: TimeoutCancellationException) {
        throw AssertionError("Preview acceptance timed out during $name; main=${core.state.value.shell.nowPlaying?.stateLabel}; " +
            "progress=${core.playbackProgress.value}; sample=${core.state.value.shell.home.samplePlayback}", timeout)
    }
    suspend fun playback(action: NowPlayingPlaybackAction, seek: Double? = null) =
        core.execute(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(action, seekSeconds = seek)))
    suspend fun sample(id: String) {
        val discovery = checkNotNull(core.state.value.shell.home.providerDiscovery)
        core.execute(NaviampCoreCommand.Home.DiscoveryReleaseAction(discovery.sourceId, discovery.sourceGeneration,
            provider.release.id, ProviderDiscoveryAction.PlaySample(id)))
    }
    suspend fun waitSample(playing: Boolean) = phase("sample playing=$playing") {
        withTimeout(10_000) {
            core.state.first { it.shell.home.samplePlayback.playing == playing && it.shell.home.samplePlayback.canToggle }
            check(!core.state.value.shell.home.samplePlayback.failed) { "Native preview failed" }
        }
    }
    fun verifyQueue() {
        val playing = checkNotNull(core.state.value.shell.nowPlaying)
        check(playing.queueSnapshot == queue) { "A discovery preview changed the main queue: ${playing.queueSnapshot}" }
        check(playing.id == "current") { "A preview replaced the main track" }
        check(playing.isPaused) { "Preview completion/navigation must not resume main playback" }
        check(sessions.isNotEmpty()) { "The production playback path must persist its queue" }
        check(sessions.all { it.toTracks().map(Track::id) == queue.tracks.map(Track::id) && it.currentIndex == 1 }) {
            "Preview identity entered persisted main playback state"
        }
    }
    try {
        val selected = checkNotNull(core.state.value.shell.nowPlaying).upNext.first()
        core.execute(NaviampCoreCommand.NowPlaying.Selection(
            NowPlayingSelectionActionRequest(selected, NowPlayingSelectionAction.SelectQueueItem)))
        phase("main queue selection") {
            withTimeout(10_000) { core.playbackProgress.first { (it.positionSeconds ?: 0.0) > 0.2 } }
        }
        playback(NowPlayingPlaybackAction.Seek, 5.0)
        phase("native seek completion") {
            withTimeout(10_000) { core.playbackProgress.first { progress ->
                val native = nativeMainPosition()
                native != null && native >= 4.9 &&
                    abs((progress.positionSeconds ?: Double.NEGATIVE_INFINITY) - native) < 0.15 &&
                    core.state.value.shell.nowPlaying?.isPlaying == true
            } }
        }
        core.execute(NaviampCoreCommand.Home.BrowseDiscoveryArtist("preview-source", provider.artist))
        core.execute(NaviampCoreCommand.Home.SelectDiscoveryRelease(provider.release.id))
        sessions.clear()
        sample("101")
        waitSample(true)
        withTimeout(10_000) { core.state.first { it.shell.nowPlaying?.isPaused == true } }
        val nativePausedPosition = checkNotNull(nativeMainPosition())
        // Pause publishes state before the next progress callback. Match the real native clock,
        // rather than comparing the preview's end against a stale pre-pause callback.
        phase("paused native clock snapshot") {
            withTimeout(10_000) { core.playbackProgress.first {
                abs((it.positionSeconds ?: Double.NEGATIVE_INFINITY) - nativePausedPosition) < 0.15
            } }
        }
        val pausedPosition = checkNotNull(core.playbackProgress.value.positionSeconds)
        waitSample(false)
        verifyQueue()
        check(abs(checkNotNull(nativeMainPosition()) - nativePausedPosition) < 0.15) { "Native main position moved during preview" }
        check(abs(checkNotNull(core.playbackProgress.value.positionSeconds) - pausedPosition) < 0.2) {
            "Main playback position moved during the preview"
        }
        // Start a longer preview and exercise pause/resume through the same release action.
        sample("102"); waitSample(true)
        sample("102"); waitSample(false)
        sample("102"); waitSample(true)
        core.execute(NaviampCoreCommand.Home.DismissDiscoveryArtist)
        check(core.state.value.shell.home.samplePlayback.trackId == null)
        verifyQueue()
        // Back out of the artist, then explicitly resume the retained main track.
        core.execute(NaviampCoreCommand.Home.DismissDiscoveryArtist)
        verifyQueue()
        val retainedPosition = checkNotNull(core.playbackProgress.value.positionSeconds)
        playback(NowPlayingPlaybackAction.Resume)
        withTimeout(5_000) { core.playbackProgress.first { (it.positionSeconds ?: 0.0) > retainedPosition + 0.2 } }
        check(core.state.value.shell.nowPlaying?.queueSnapshot == queue)
        check(core.state.value.shell.nowPlaying?.id == "current")
        check(core.state.value.shell.home.samplePlayback.trackId == null)
    } finally {
        core.close()
        playbackServices.effects.stop()
    }
}

private class PreviewContractProvider(
    private val mainUrl: String,
    private val shortUrl: String,
    private val longUrl: String,
) : MediaProvider {
    override val id = ProviderId("preview-contract")
    override val displayName = "Preview contract"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val artist = ProviderDiscoveryArtist("11111111-1111-4111-8111-111111111111", "Fixture artist", detailsAvailable = true)
    val release = ProviderDiscoveryRelease("22222222-2222-4222-8222-222222222222", "Fixture release")
    override val discovery = object : ProviderDiscovery {
        override suspend fun load() = ProviderDiscoveryFeed(recommendations = listOf(artist))
        override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = ProviderDiscoveryArtistResult(
            ProviderDiscoveryStatus.Ready, ProviderDiscoveryArtistDetails(artist, releases = listOf(release)))
        override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseDetails(
            ProviderDiscoveryStatus.Ready, this@PreviewContractProvider.release)
        override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseTracks(
            ProviderDiscoveryStatus.Ready, listOf("101", "102").mapIndexed { index, id ->
                ProviderDiscoveryReleaseTrack(null, "Preview $id", 1, index + 1, null, DiscoveryDeezerPreviewId(id))
            })
    }
    fun owned(id: String) = ProviderDiscoverySample(id, "Owned $id").playbackTrack("Fixture artist")
        .copy(id = TrackId(id), durationSeconds = 40)
    override suspend fun streamUrl(request: StreamRequest) = when (request.trackId.value) {
        "discovery-preview:101" -> shortUrl
        "discovery-preview:102" -> longUrl
        else -> mainUrl
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

/** Non-silent PCM fixture; output remains muted in acceptance, so this does not establish audibility. */
fun naviampDiscoveryPreviewFixtureWave(seconds: Int): ByteArray {
    require(seconds in 1..180)
    val rate = 16_000
    val size = rate * 2 * seconds
    val bytes = ByteArray(44 + size)
    fun text(offset: Int, value: String) { value.encodeToByteArray().copyInto(bytes, offset) }
    fun number(offset: Int, value: Int, count: Int) {
        repeat(count) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
    text(0, "RIFF"); number(4, 36 + size, 4); text(8, "WAVEfmt ")
    number(16, 16, 4); number(20, 1, 2); number(22, 1, 2)
    number(24, rate, 4); number(28, rate * 2, 4); number(32, 2, 2); number(34, 16, 2)
    text(36, "data"); number(40, size, 4)
    repeat(rate * seconds) { frame ->
        number(44 + frame * 2, (kotlin.math.sin(frame * 2.0 * kotlin.math.PI * 440 / rate) * 3_000).toInt(), 2)
    }
    return bytes
}
