package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.*
import app.naviamp.domain.bass.*
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.ui.DiscoverySamplePlaybackUi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreSamplePlaybackControllerTest {
    @Test fun pendingMainUrlCannotInterruptANewerPreviewAndResumesOnlyOnExplicitRequest() = runTest {
        for (initiallyPlaying in listOf(false, true)) {
            val wait = CompletableDeferred<String>()
            val provider = object : MediaProvider by SampleProvider() {
                override suspend fun streamUrl(request: StreamRequest) = if (request.trackId.value == "next") withContext(NonCancellable) { wait.await() }
                    else "https://music.test/${request.trackId.value}"
            }
            val providers = NaviampCoreMediaProviderSource { provider }
            val bass = PendingMainLoadBassBackend()
            val runtime = object : BassPlaybackEngineRuntime {
                override val workContext = kotlin.coroutines.EmptyCoroutineContext
                override fun localFilePath(url: String): String? = null
                override fun nowEpochMillis() = 1_000L
                override fun <T> withPreparedPlaybackLock(block: () -> T) = block()
            }
            val engine = CoreBassPlaybackEngine(Result.success(bass), runtime)
            val adapter = NaviampCorePlaybackEngineAdapter(backgroundScope, engine, providers,
                settings = { app.naviamp.domain.settings.PlaybackSettings() })
            var mainState: PlaybackState = PlaybackState.Idle
            adapter.attach(object : NaviampCorePlaybackObserver {
                override fun onStateChanged(state: PlaybackState) { mainState = state }
                override fun onProgressChanged(progress: PlaybackProgress) {}
                override fun onMetadataChanged(metadata: PlaybackStreamMetadata) {}
            })
            val screens = mutableListOf<DiscoverySamplePlaybackUi>()
            val controller = NaviampCoreSamplePlaybackController(backgroundScope,
                NaviampCoreEngineSamplePlayback(backgroundScope, engine, providers, { true }, { 50 }), providers,
                { mainState }, adapter::pause, screens::add, prepareMain = adapter::prepareForPreview)
            try {
                if (initiallyPlaying) {
                    adapter.playQueueSelection(PlaybackQueue(listOf(sample("old").copy(id = TrackId("old"))), 0), 0)
                    runCurrent()
                    assertEquals(PlaybackState.Playing, mainState)
                }
                adapter.playQueueSelection(PlaybackQueue(listOf(sample("next").copy(id = TrackId("next"))), 0), 0)
                runCurrent()
                controller.toggle(sample("42")); runCurrent()
                assertTrue(screens.last().playing)
                if (initiallyPlaying) assertEquals(BassActiveState.Paused, bass.states[1])
                wait.complete("https://music.test/next"); runCurrent()
                assertTrue(screens.last().playing, "An older pending main URL stopped the newer preview: ${screens.last()}")
                controller.stop()
                adapter.resume(); runCurrent()
                assertEquals(PlaybackState.Playing, mainState)
                assertEquals("https://music.test/next", bass.urls.last(), "Resume must start the deferred selection, not the older native stream")
            } finally { controller.close(); adapter.stop(); engine.release() }
        }
    }

    @Test fun canceledLateUrlResolutionCannotStartNativeAudioOrReplaceANewerSample() = runTest {
        val delayed = CompletableDeferred<String>()
        var active: MediaProvider = SampleProvider(resolveUrl = { withContext(NonCancellable) { delayed.await() } })
        val native = object : PlaybackEngine by UnavailableNaviampPlaybackEngine, PreviewPlaybackEngine {
            val requests = mutableListOf<PlaybackRequest>()
            override fun playPreview(scope: kotlinx.coroutines.CoroutineScope, request: PlaybackRequest,
                verifyNetworkCertificates: Boolean, volumePercent: Int, onStateChanged: (PlaybackState) -> Unit) {
                requests += request
                assertFalse(verifyNetworkCertificates)
                assertEquals(47, volumePercent)
                onStateChanged(PlaybackState.Playing)
            }
            override fun pausePreview() {}
            override fun resumePreview() {}
            override fun stopPreview() {}
        }
        val providers = NaviampCoreMediaProviderSource { active }
        val port = NaviampCoreEngineSamplePlayback(this, native, providers, { false }, { 47 })
        val screens = mutableListOf<DiscoverySamplePlaybackUi>()
        val controller = NaviampCoreSamplePlaybackController(this, port, providers,
            { PlaybackState.Paused }, {}, screens::add)
        controller.toggle(sample("old")); runCurrent()
        controller.stop()
        active = SampleProvider("new-account")
        controller.toggle(sample("new")); runCurrent()
        delayed.complete("https://old.test/stale.mp3"); runCurrent()
        assertEquals(listOf("discovery-preview:new"), native.requests.map { it.mediaId })
        assertEquals("discovery-preview:new", screens.last().trackId)
        assertTrue(screens.last().playing)
        controller.close()
    }

    @Test fun samplesPauseMainWithoutReplacingItsTrackQueuePositionOrNavigation() = runTest {
        val owned = sample("owned").copy(id = TrackId("owned"), durationSeconds = 180)
        val queued = sample("next").copy(id = TrackId("next"), durationSeconds = 180)
        val main = NaviampLivePlaybackController(NaviampLivePlaybackState(currentTrack = owned,
            queue = PlaybackQueue(listOf(owned, queued), 0), progress = PlaybackProgress(37.5, 180.0),
            playbackState = PlaybackState.Playing))
        val before = main.state.value
        val port = SamplePort()
        val screens = mutableListOf<DiscoverySamplePlaybackUi>()
        val provider = SampleProvider()
        val controller = NaviampCoreSamplePlaybackController(this, port, NaviampCoreMediaProviderSource { provider },
            { main.state.value.playbackState }, { main.updatePlaybackState(PlaybackState.Paused) }, screens::add)
        controller.toggle(sample("42")); runCurrent()
        assertEquals(PlaybackState.Paused, main.state.value.playbackState)
        assertEquals(before.queue, main.state.value.queue)
        assertEquals(before.currentTrack, main.state.value.currentTrack)
        assertEquals(37.5, main.progress.value.positionSeconds)
        assertEquals("discovery-preview:42", screens.last().trackId)
        assertTrue(screens.last().playing)
        controller.toggle(sample("42"))
        assertFalse(screens.last().playing)
        controller.toggle(sample("42"))
        assertTrue(screens.last().playing)
        assertEquals(listOf("discovery-preview:42"), port.played)
        port.observer!!(PlaybackState.Finished)
        assertFalse(screens.last().playing)
        assertEquals(PlaybackState.Paused, main.state.value.playbackState)
        controller.close()
        assertNull(screens.last().trackId)
    }

    @Test fun switchingSamplesAndClosingRejectOldCallbacksAndDoNotResumeMain() = runTest {
        val provider = SampleProvider()
        var active: MediaProvider = provider
        val port = SamplePort()
        val screens = mutableListOf<DiscoverySamplePlaybackUi>()
        var pauses = 0
        val controller = NaviampCoreSamplePlaybackController(this, port, NaviampCoreMediaProviderSource { active },
            { PlaybackState.Paused }, { pauses++ }, screens::add)
        controller.toggle(sample("1")); runCurrent()
        val old = port.observer!!
        controller.toggle(sample("2")); runCurrent()
        old(PlaybackState.Finished)
        assertEquals("discovery-preview:2", screens.last().trackId)
        assertTrue(screens.last().playing)
        active = SampleProvider("other-account")
        port.observer!!(PlaybackState.Error("old source"))
        assertFalse(screens.last().failed)
        controller.stop()
        old(PlaybackState.Playing)
        assertNull(screens.last().trackId)
        assertEquals(0, pauses)
    }

    @Test fun unsupportedPlaybackDoesNotInterruptMainAndErrorsPermitRetry() = runTest {
        val provider = SampleProvider()
        var pauses = 0
        val screens = mutableListOf<DiscoverySamplePlaybackUi>()
        val unsupported = NaviampCoreSamplePlaybackController(this, UnavailableNaviampSamplePlayback,
            NaviampCoreMediaProviderSource { provider }, { PlaybackState.Playing }, { pauses++ }, screens::add)
        unsupported.toggle(sample("1"))
        assertTrue(screens.last().failed)
        assertEquals(0, pauses)
        val port = SamplePort()
        val controller = NaviampCoreSamplePlaybackController(this, port, NaviampCoreMediaProviderSource { provider },
            { PlaybackState.Idle }, { pauses++ }, screens::add)
        controller.toggle(sample("1")); runCurrent()
        port.observer!!(PlaybackState.Error("unavailable"))
        assertTrue(screens.last().failed)
        controller.toggle(sample("1")); runCurrent()
        assertTrue(screens.last().playing)
        assertFalse(screens.last().failed)
        assertEquals(2, port.played.size)
        controller.close()
    }
}

private fun sample(id: String) = ProviderDiscoverySample(id, "Sample $id").playbackTrack("Artist")

private class SamplePort : NaviampCoreSamplePlaybackPort {
    override val available = true
    val played = mutableListOf<String>()
    var observer: ((PlaybackState) -> Unit)? = null
    override suspend fun play(provider: MediaProvider, track: Track, onState: (PlaybackState) -> Unit) {
        played += track.id.value; observer = onState; onState(PlaybackState.Playing)
    }
    override fun pause() { observer?.invoke(PlaybackState.Paused) }
    override fun resume() { observer?.invoke(PlaybackState.Playing) }
    override fun stop() { observer?.invoke(PlaybackState.Stopped); observer = null }
}

private class SampleProvider(override val cacheNamespace: String = "aurral-account",
    private val resolveUrl: suspend () -> String = { "https://music.test/sample.mp3" }) : MediaProvider {
    override val id = ProviderId("aurral")
    override val displayName = "Aurral"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = emptyList<Playlist>()
    override suspend fun genres(limit: Int) = emptyList<Genre>()
    override suspend fun streamUrl(request: StreamRequest) = resolveUrl()
    override fun coverArtUrl(coverArtId: String) = coverArtId
}

private class PendingMainLoadBassBackend : BassAudioBackend {
    private var next = 0
    val states = mutableMapOf<Int, Int>()
    val urls = mutableListOf<String>()
    val ends = mutableMapOf<Int, (BassStreamHandle) -> Unit>()
    val volumes = mutableMapOf<Int, Float>()
    val freed = mutableListOf<Int>()
    var deviceFrees = 0
    override fun init() = Result.success(Unit)
    override fun free(): Result<Unit> { deviceFrees++; return Result.success(Unit) }
    override fun setSampleRateConverterQuality(quality: Int) = Result.success(Unit)
    private fun create() = Result.success(BassStreamHandle(++next))
    override fun createFileStream(path: String) = create()
    override fun createUrlStream(url: String) = create().also { urls += url }
    override fun createFileDecodeStream(path: String) = create()
    override fun createUrlDecodeStream(url: String) = create().also { urls += url }
    override fun lengthBytes(stream: BassStreamHandle): Long? = null
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray) = Result.success(0)
    override fun activeState(stream: BassStreamHandle) = states[stream.value]
    override fun play(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Playing; return Result.success(Unit) }
    override fun pause(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Paused; return Result.success(Unit) }
    override fun stop(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Stopped; return Result.success(Unit) }
    override fun setEndSync(stream: BassStreamHandle, callback: (BassStreamHandle) -> Unit): Result<Int> {
        ends[stream.value] = callback; return Result.success(1)
    }
    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> { volumes[stream.value] = volume; return Result.success(Unit) }
    override fun freeStream(stream: BassStreamHandle): Result<Unit> { freed += stream.value; return Result.success(Unit) }
}
