package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackStreamRelay
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PreviewPlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.isDiscoveryPreview
import app.naviamp.ui.DiscoverySamplePlaybackUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Separate effects and state: samples never enter the Library queue, session, sidecars or reports. */
interface NaviampCoreSamplePlaybackPort {
    val available: Boolean get() = false
    suspend fun play(provider: MediaProvider, track: Track, onState: (PlaybackState) -> Unit) {}
    fun pause() {}
    fun resume() {}
    fun stop() {}
    fun close() = stop()
}

object UnavailableNaviampSamplePlayback : NaviampCoreSamplePlaybackPort

/** Reuses provider-authenticated byte transport, with leases independent from main playback. */
internal class NaviampCoreEngineSamplePlayback(
    private val scope: CoroutineScope,
    engine: PlaybackEngine,
    private val providers: NaviampCoreMediaProviderSource,
    private val verifyCertificates: () -> Boolean,
    private val volumePercent: () -> Int,
) : NaviampCoreSamplePlaybackPort {
    private val engine = engine as? PreviewPlaybackEngine
    private var relay: NaviampPlaybackStreamRelay? = null
    private var generation = 0L
    override val available: Boolean get() = engine != null

    override suspend fun play(provider: MediaProvider, track: Track, onState: (PlaybackState) -> Unit) {
        require(track.id.isDiscoveryPreview())
        val epoch = ++generation
        val native = checkNotNull(engine)
        val request = StreamRequest(track.id, StreamQuality.Original)
        var transport: app.naviamp.domain.playback.PlaybackRandomAccessSource? = null
        val url = if (provider.requiresAuthenticatedPlaybackTransport) {
            val bytes = relay ?: NaviampPlaybackStreamRelay(scope, providers::isCurrent).also { relay = it }
            bytes.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request).also {
                transport = bytes.randomAccessSource(it)
            }
        } else provider.streamUrl(request)
        currentCoroutineContext().ensureActive()
        if (epoch != generation || !providers.isCurrent(provider)) return
        native.playPreview(scope, PlaybackRequest(url, mediaId = track.id.value, randomAccess = transport),
            verifyCertificates(), volumePercent()) { state ->
            if (epoch != generation) return@playPreview
            if (state == PlaybackState.Finished || state == PlaybackState.Stopped || state is PlaybackState.Error)
                relay?.clearCurrent()
            onState(state)
        }
    }

    override fun pause() { engine?.pausePreview() }
    override fun resume() { engine?.resumePreview() }
    override fun stop() { generation++; engine?.stopPreview(); relay?.clearCurrent() }
    override fun close() { stop(); relay?.close(); relay = null }
}

/** Core owns interruption policy; ending/dismissing a sample never resumes main audio implicitly. */
class NaviampCoreSamplePlaybackController(
    private val scope: CoroutineScope,
    private val port: NaviampCoreSamplePlaybackPort,
    private val providers: NaviampCoreMediaProviderSource,
    private val mainState: () -> PlaybackState,
    private val pauseMain: () -> Unit,
    private val publish: (DiscoverySamplePlaybackUi) -> Unit,
    private val prepareMain: () -> Unit = {},
) {
    private var generation = 0L
    private var job: Job? = null
    private var track: Track? = null
    private var state: PlaybackState = PlaybackState.Idle

    fun toggle(sample: Track) {
        require(sample.id.isDiscoveryPreview())
        val provider = providers.current() ?: return
        if (!port.available) {
            publish(DiscoverySamplePlaybackUi(sample.id.value, failed = true))
            return
        }
        if (track?.id == sample.id && state == PlaybackState.Playing) { port.pause(); return }
        prepareMain()
        if (mainState() == PlaybackState.Playing || mainState() == PlaybackState.Loading) pauseMain()
        if (track?.id == sample.id && state == PlaybackState.Paused) { port.resume(); return }
        stop()
        val epoch = generation
        track = sample
        update(PlaybackState.Loading)
        job = scope.launch {
            try {
                port.play(provider, sample) { next ->
                    if (epoch == generation && providers.isCurrent(provider)) update(next)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (epoch == generation && providers.isCurrent(provider)) {
                    port.stop()
                    update(PlaybackState.Error(error.message.orEmpty()))
                }
            }
        }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        port.stop()
        track = null
        update(PlaybackState.Idle)
    }

    fun close() { stop(); port.close() }

    private fun update(next: PlaybackState) {
        state = next
        publish(DiscoverySamplePlaybackUi(track?.id?.value, playing = next == PlaybackState.Playing,
            canToggle = next != PlaybackState.Loading, failed = next is PlaybackState.Error))
    }
}
