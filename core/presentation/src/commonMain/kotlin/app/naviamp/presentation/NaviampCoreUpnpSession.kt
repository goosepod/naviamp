package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared UPnP protocol translation. No duplicate queue, provider relay or output playback owner. */
internal class NaviampCoreUpnpSession(
    private val scope: CoroutineScope,
    private val outputs: NaviampPlaybackOutputSelectionController,
    private val pollIntervalMillis: Long? = 2_000,
) : NaviampCoreRemoteSession {
    override val kind = NaviampRemoteOutputKind.Upnp
    override val observedAuthority = true
    override val retainUntilRevoked = true
    private val mutableStatus = MutableStateFlow<NaviampCoreRemoteStatus?>(null)
    override val status = mutableStatus.asStateFlow()
    private val wire = Mutex()
    private var client: NaviampUpnpRendererClient? = null
    private var capabilities: NaviampUpnpRendererCapabilities? = null
    private var selection: Long? = null
    override val selectionIdentity get() = selection
    private var ownsMedia = false
    private var revision = 0L
    override val observationRevision get() = revision
    private var pollJob: Job? = null
    var localAddress: String? = null
        private set
    override val supportsNext get() = capabilities?.supportsNext == true
    override val supportsVolume get() = capabilities?.supportsVolume == true
    override fun start() = true
    override fun connectedSelection(): Long? = selection?.takeIf { id ->
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let {
            it.selectionId == id && it.target.kind == kind && it.phase == NaviampRemoteOutputPhase.Connected
        } == true
    }

    suspend fun select(client: NaviampUpnpRendererClient, localAddress: String): Boolean {
        require(localAddress.isNotBlank() && localAddress.length <= 256 && localAddress.none { it.isWhitespace() || it in "/\\?#" })
        this.localAddress = localAddress
        val id = outputs.select(NaviampRemoteOutputTarget(kind, client.renderer.udn, client.renderer.name))
        selection = id
        outputs.connecting(id)
        return try {
            val caps = client.probe()
            if (selection != id || (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.selectionId != id) return false
            this.client = client; capabilities = caps; mutableStatus.value = null; ownsMedia = false
            outputs.connected(id)
            pollJob?.cancel()
            pollIntervalMillis?.let { interval ->
                require(interval >= 1_000)
                pollJob = scope.launch {
                    while (connectedSelection() == id) { delay(interval); refresh() }
                }
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { outputs.unavailable(id); false }
    }

    private data class Plan(val original: NaviampUpnpMediaPlan.Original) : NaviampCoreRemoteMediaPlan {
        override val quality = StreamQuality.Original
        override val contentType get() = original.contentType
    }
    override fun plan(track: Track, provider: MediaProvider): NaviampCoreRemoteMediaPlan? {
        val info = track.audioInfo ?: return null
        val mime = info.contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        val format = when (mime) {
            "audio/flac", "audio/x-flac" -> NaviampUpnpAudioFormat.Flac
            "audio/mpeg", "audio/mp3" -> NaviampUpnpAudioFormat.Mp3
            else -> return null
        }
        val codec = info.codec?.lowercase()
        if (codec != null && when (format) {
                NaviampUpnpAudioFormat.Flac -> codec !in setOf("flac", "audio/flac", "audio/x-flac")
                NaviampUpnpAudioFormat.Mp3 -> codec !in setOf("mp3", "mpeg", "audio/mpeg", "audio/mp3")
            }) return null
        val plan = planNaviampUpnpMedia(NaviampUpnpSourceFormat(format, mime, info.bitDepth, info.samplingRateHz), capabilities?.sinkProtocols.orEmpty())
        return (plan as? NaviampUpnpMediaPlan.Original)?.let(::Plan)
    }
    private fun media(value: NaviampCoreRemoteMedia, plan: NaviampCoreRemoteMediaPlan) = NaviampUpnpRendererMedia(
        value.url, (plan as Plan).original, value.track.title, value.track.artistName, value.track.albumTitle,
        value.artworkUrl, value.track.durationSeconds?.toLong()?.times(1_000))

    override suspend fun load(selection: Long, media: NaviampCoreRemoteMedia, plan: NaviampCoreRemoteMediaPlan): Boolean = wire.withLock {
        if (connectedSelection() != selection) return@withLock false
        val receiver = client ?: return@withLock false
        receiver.setCurrent(media(media, plan))
        if (media.positionMillis > 0) receiver.seek(media.positionMillis)
        val observed = readStatus(receiver, selection)
        val confirmed = connectedSelection() == selection && observed?.mediaUrl == media.url
        if (confirmed) ownsMedia = true
        confirmed
    }
    override suspend fun prepareNext(selection: Long, media: NaviampCoreRemoteMedia?, plan: NaviampCoreRemoteMediaPlan?): Boolean = wire.withLock {
        if (connectedSelection() != selection || !supportsNext || !ownsMedia) return@withLock false
        client?.setNext(media?.let { media(it, checkNotNull(plan)) }) ?: return@withLock false
        connectedSelection() == selection
    }
    override suspend fun command(selection: Long, command: NaviampCoreRemoteCommand): Boolean = wire.withLock {
        if (connectedSelection() != selection || !ownsMedia) return@withLock false
        val receiver = client ?: return@withLock false
        when (command) {
            NaviampCoreRemoteCommand.Play -> receiver.play()
            NaviampCoreRemoteCommand.Pause -> receiver.pause()
            NaviampCoreRemoteCommand.Stop -> receiver.stop()
            is NaviampCoreRemoteCommand.Seek -> receiver.seek(command.millis)
            is NaviampCoreRemoteCommand.Volume -> receiver.setVolume(command.percent)
        }
        readStatus(receiver, selection)
        connectedSelection() == selection
    }
    suspend fun refresh() {
        val id = connectedSelection() ?: return
        try { wire.withLock { if (connectedSelection() == id) client?.let { readStatus(it, id) } } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { outputs.unavailable(id) }
    }
    /** Even valid events are reconciled against current status before changing the shared queue. */
    suspend fun acceptEvent(gate: NaviampUpnpEventGate, headers: Map<String, String>, body: String) {
        val id = connectedSelection() ?: return
        if (gate.accept(id, headers, body) != null) refresh()
    }
    private suspend fun readStatus(receiver: NaviampUpnpRendererClient, id: Long): NaviampCoreRemoteStatus? {
        val position = receiver.position()
        val state = receiver.transportState()
        if (connectedSelection() != id) return null
        val mapped = when (state) {
            NaviampUpnpTransportState.PLAYING -> PlaybackState.Playing
            NaviampUpnpTransportState.PAUSED_PLAYBACK -> PlaybackState.Paused
            NaviampUpnpTransportState.TRANSITIONING -> PlaybackState.Loading
            else -> PlaybackState.Stopped
        }
        return NaviampCoreRemoteStatus(id, ++revision, position.trackUri, mapped,
            // A renderer's unknown/zero duration must not erase the provider's known finite length.
            position.positionMillis ?: 0, position.durationMillis?.takeIf { it > 0 },
            finished = state == NaviampUpnpTransportState.STOPPED && position.durationMillis?.let { it > 0 && (position.positionMillis ?: 0) >= it } == true)
            .also { mutableStatus.value = it }
    }
    override fun selectLocal() {
        if ((outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let { it.selectionId == selection && it.target.kind == kind } == true) outputs.selectLocal()
    }
    override suspend fun disconnect(stopReceiver: Boolean) {
        pollJob?.cancel(); pollJob = null
        wire.withLock {
            val receiver = client
            try { if (stopReceiver && ownsMedia && supportsNext) receiver?.setNext(null) } finally {
                try { if (stopReceiver && ownsMedia) receiver?.stop() } finally { client = null; capabilities = null; mutableStatus.value = null; selection = null; ownsMedia = false }
            }
        }
    }
    override suspend fun shutdown() {
        val id = selection
        try { disconnect(true) } finally { if ((outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.selectionId == id) outputs.selectLocal() }
    }
    override fun stop() { pollJob?.cancel(); pollJob = null }
}
