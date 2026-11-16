package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.*
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.queue.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreUpnpPlaybackTest {
    @Test fun zeroReceiverDurationPreservesKnownTrackDurationAndExternalSeekReachesTheWire() = runTest {
        val f = Fixture(this)
        f.wire.duration = "00:00:00"
        var token = 0
        val core = NaviampCore.create(f.ownScope, fakeCoreServices(f.provider, f.local).copy(
            upnp = NaviampCoreUpnpServices(f.wire, f.server,
                NaviampCastSecureTokenSource { "duration-test-${(++token).toString().padStart(32, '0')}" }, pollIntervalMillis = null)))
        try {
            runCurrent()
            core.updateLivePlayback { it.copy(currentTrack = f.track, queue = PlaybackQueue(listOf(f.track), 0),
                playbackState = PlaybackState.Playing, progress = PlaybackProgress(0.0, 180.0)) }
            assertTrue(core.selectUpnpRenderer("http://192.0.2.2/device.xml", "uuid:fixture", "192.0.2.1"))
            advanceUntilIdle()
            val external = core.externalPlaybackBridge()
            assertEquals(180_000L, external.snapshot().durationMillis)
            assertTrue(external.snapshot().canSeek)
            external.pause(); advanceUntilIdle()
            external.seekTo(30_000); advanceUntilIdle()
            assertEquals("00:00:30", f.wire.seekTarget?.substringBefore('.'))
            assertEquals(1, f.wire.loads)
            assertEquals(0L, external.snapshot().positionMillis, "Keep observed position; do not invent successful seeking")
        } finally { core.shutdown(); f.ownScope.cancel() }
    }

    @Test fun returningToLocalWaitsForTheOwnedReceiverToStopBeforeStartingLocalAudio() = runTest {
        val f = Fixture(this)
        try {
            f.connect(); advanceUntilIdle()
            f.wire.stopGate = CompletableDeferred()
            f.controller.selectLocal(); runCurrent()
            assertEquals(0, f.local.starts)
            assertEquals(PlaybackState.Paused, f.live.state.value.playbackState)
            f.wire.stopGate!!.complete(Unit); advanceUntilIdle()
            assertEquals(1, f.local.starts)
            assertEquals(1, f.wire.stops)
        } finally { f.close() }
    }

    @Test fun metadataRefreshDoesNotReloadOrInvalidatePreparedPlaybackAndPromotionKeepsCanonicalMetadata() = runTest {
        val f = Fixture(this)
        try {
            f.connect(); advanceUntilIdle()
            val prepared = f.wire.next
            f.queue.updateTrack(f.track.copy(title = "Updated metadata"))
            advanceUntilIdle()
            assertEquals(prepared, f.wire.next)
            assertEquals(1, f.wire.loads)
            f.wire.promote(); f.session.refresh(); advanceUntilIdle()
            assertEquals(1, f.live.state.value.queue.currentIndex)
            assertEquals("Updated metadata", f.live.state.value.currentTrack?.title)
            assertEquals(1, f.wire.loads)
            assertTrue(f.outputs.hasRemotePlaybackAuthority())
        } finally { f.close() }
    }

    @Test fun disposalAfterScopeCancellationStillRevokesTheRelayAndStopsOwnedPlayback() = runTest {
        val f = Fixture(this)
        f.connect(); advanceUntilIdle()
        val url = f.wire.current
        f.ownScope.cancel()
        f.controller.close(); advanceUntilIdle()
        assertEquals(404, f.server.get(url).status)
        assertEquals(1, f.wire.stops)
        assertEquals(0, f.local.starts)
    }

    @Test fun lateDescriptionCannotDisplaceANewerSelectionAndReselectingCleansOnlyOldResources() = runTest {
        val f = Fixture(this)
        val descriptionGate = CompletableDeferred<Unit>()
        val http = NaviampUpnpHttpTransport { request ->
            if (request.url.contains("/slow/")) descriptionGate.await()
            f.wire.exchange(request)
        }
        var token = 0
        val core = NaviampCore.create(f.ownScope, fakeCoreServices(f.provider, f.local).copy(upnp = NaviampCoreUpnpServices(http,
            f.server, NaviampCastSecureTokenSource { "selection-test-${(++token).toString().padStart(32, '0')}" }, pollIntervalMillis = null)))
        try {
            runCurrent()
            core.updateLivePlayback { it.copy(currentTrack = f.track, queue = PlaybackQueue(listOf(f.track, f.track), 0),
                playbackState = PlaybackState.Playing, progress = PlaybackProgress(0.0, 180.0)) }
            val old = async { core.selectUpnpRenderer("http://192.0.2.2/slow/device.xml", "uuid:fixture", "192.0.2.1") }
            runCurrent()
            assertTrue(core.selectUpnpRenderer("http://192.0.2.2/device.xml", "uuid:fixture", "192.0.2.1"))
            advanceUntilIdle()
            val selected = (core.playbackOutputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId
            descriptionGate.complete(Unit)
            assertFalse(old.await())
            assertEquals(selected, (core.playbackOutputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId)
            assertTrue(core.selectUpnpRenderer("http://192.0.2.2/device.xml", "uuid:fixture", "192.0.2.1"))
            advanceUntilIdle()
            assertTrue(core.playbackOutputs.hasRemotePlaybackAuthority())
            assertEquals(2, f.wire.loads)
            assertEquals(200, f.server.get(f.wire.current).status)
            assertEquals(0, f.local.starts)
        } finally { core.shutdown(); f.ownScope.cancel() }
    }

    @Test fun coreFacadeAssemblesTheSameQueueRelayAndRoutesControlsWithoutHostProductCode() = runTest {
        val f = Fixture(this)
        var token = 0
        val core = NaviampCore.create(f.ownScope, fakeCoreServices(f.provider, f.local).copy(
            upnp = NaviampCoreUpnpServices(f.wire, f.server,
                NaviampCastSecureTokenSource { "core-upnp-${(++token).toString().padStart(32, '0')}" }, pollIntervalMillis = null)))
        try {
            runCurrent()
            assertEquals(0, f.wire.loads)
            core.updateLivePlayback { it.copy(currentTrack = f.track, queue = PlaybackQueue(listOf(f.track), 0),
                playbackState = PlaybackState.Playing, progress = PlaybackProgress(0.0, 180.0)) }
            assertTrue(core.selectUpnpRenderer("http://192.0.2.2/device.xml", "uuid:fixture", "192.0.2.1"))
            advanceUntilIdle()
            assertTrue(core.playbackOutputs.hasRemotePlaybackAuthority())
            assertEquals(1, f.wire.loads)
            assertEquals(200, f.server.get(f.wire.current).status)
            core.dispatch(NaviampCoreCommand.NowPlaying.Playback(app.naviamp.ui.NowPlayingPlaybackActionRequest(app.naviamp.ui.NowPlayingPlaybackAction.Pause)))
            advanceUntilIdle()
            assertEquals("PAUSED_PLAYBACK", f.wire.state)
            core.selectLocalPlayback(); advanceUntilIdle()
            assertEquals(NaviampPlaybackOutputSelection.Local, core.playbackOutputs.state.value)
            assertEquals(0, f.local.starts)
        } finally { core.shutdown(); f.ownScope.cancel() }
        assertFalse(core.selectUpnpRenderer("http://192.0.2.2/device.xml", "uuid:fixture", "192.0.2.1"))
    }

    @Test fun acknowledgedButIgnoredLoadCannotClaimAuthorityOrPlayAnotherControllersTrack() = runTest {
        val f = Fixture(this)
        try {
            f.wire.current = "http://192.0.2.2/other-controller"
            f.wire.ignoreLoad = true
            f.connect(); advanceUntilIdle()
            assertFalse(f.outputs.hasRemotePlaybackAuthority())
            assertEquals(0, f.wire.plays)
            assertEquals(0, f.wire.stops)
            assertEquals(NaviampRemoteOutputPhase.Unavailable, (f.outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    @Test fun observedControllerTakeoverLeavesCanonicalQueueAndOtherPlaybackAlone() = runTest {
        val f = Fixture(this)
        try {
            f.connect(); advanceUntilIdle()
            val url = f.wire.current
            f.wire.current = "http://192.0.2.2/other-controller"
            f.session.refresh(); advanceUntilIdle()
            assertEquals(0, f.live.state.value.queue.currentIndex)
            assertEquals(0, f.wire.stops)
            assertEquals(404, f.server.get(url).status)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    @Test fun repeatedOccurrencesAdvanceCanonicalQueueOnceWithoutReloadingAndKeepLosslessRelay() = runTest {
        val f = Fixture(this)
        try {
            f.connect(); advanceUntilIdle()
            assertTrue(f.outputs.hasRemotePlaybackAuthority())
            assertEquals(1, f.wire.loads)
            assertEquals(1, f.wire.plays)
            val first = f.wire.current
            val second = assertNotNull(f.wire.next)
            assertNotEquals(first, second)
            val reply = f.server.get(first)
            assertEquals("audio/flac", reply.headers["Content-Type"])
            assertContentEquals(f.bytes, reply.body.toByteArray())
            assertTrue(f.requests.all { it.quality == StreamQuality.Original })
            assertEquals(0, f.store.writes)
            f.wire.promote(); f.session.refresh(); advanceUntilIdle()
            assertEquals(1, f.live.state.value.queue.currentIndex)
            assertEquals(f.track.id, f.live.state.value.currentTrack?.id)
            assertEquals(1, f.wire.loads)
            assertEquals(1, f.wire.plays)
            val third = assertNotNull(f.wire.next)
            f.session.refresh(); f.session.refresh(); advanceUntilIdle()
            assertEquals(1, f.live.state.value.queue.currentIndex)
            assertEquals(third, f.wire.next)
            assertEquals(404, f.server.get(first).status)
            assertEquals(200, f.server.get(second).status)
            f.wire.promote(); f.session.refresh(); advanceUntilIdle()
            assertEquals(2, f.live.state.value.queue.currentIndex)
            assertNull(f.wire.next)
            assertEquals(1, f.wire.loads)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    @Test fun editsReplacePreparedSlotBeforeRevocationAndLongPauseDoesNotExpireActiveUrls() = runTest {
        val f = Fixture(this)
        try {
            f.connect(); advanceUntilIdle()
            val current = f.wire.current
            val superseded = assertNotNull(f.wire.next)
            val replacement = f.track.copy(id = TrackId("replacement"), title = "Replacement")
            f.queue.replaceQueue(PlaybackQueue(listOf(f.track, replacement), 0))
            advanceUntilIdle()
            assertNotEquals(superseded, f.wire.next)
            assertTrue(f.wire.operations.indexOf("clear-next") < f.wire.operations.lastIndexOf("next"))
            assertEquals(404, f.server.get(superseded).status)
            f.now = 10 * 60 * 60 * 1_000L
            assertEquals(200, f.server.get(current).status)
            assertEquals(200, f.server.get(assertNotNull(f.wire.next)).status)
            f.wire.promote(); f.session.refresh(); advanceUntilIdle()
            assertEquals(replacement.id, f.live.state.value.currentTrack?.id)
            assertEquals(1, f.wire.loads)
        } finally { f.close() }
    }

    @Test fun repeatTrackUsesNewUrlsAndStopAtDurationNeverAdvancesTheQueue() = runTest {
        val f = Fixture(this)
        try {
            f.live.updateRepeatMode(RepeatMode.Track)
            f.connect(); advanceUntilIdle()
            val old = assertNotNull(f.wire.next)
            f.wire.promote(); f.session.refresh(); advanceUntilIdle()
            assertEquals(0, f.live.state.value.queue.currentIndex)
            assertNotEquals(old, f.wire.next)
            assertEquals(1, f.wire.loads)
            f.controller.stop(); advanceUntilIdle()
            assertEquals(0, f.live.state.value.queue.currentIndex)
            assertNull(f.wire.next)
            assertEquals(PlaybackState.Stopped, f.live.state.value.playbackState)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    @Test fun failedNextKeepsCurrentPlayingAndSourceChangeRevokesWithoutStartingLocal() = runTest {
        val f = Fixture(this)
        try {
            f.wire.failNext = true
            f.connect(); advanceUntilIdle()
            assertNull(f.wire.next)
            assertEquals(PlaybackState.Playing, f.live.state.value.playbackState)
            assertTrue(f.outputs.hasRemotePlaybackAuthority())
            val url = f.wire.current
            f.providerCurrent = null
            f.session.refresh(); advanceUntilIdle()
            assertEquals(NaviampRemoteOutputPhase.Unavailable, (f.outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
            assertEquals(PlaybackState.Paused, f.live.state.value.playbackState)
            assertEquals(404, f.server.get(url).status)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    @Test fun unsupportedLosslessDoesNotUseProviderMp3TranscodingOrFallBackToLocal() = runTest {
        val f = Fixture(this)
        try {
            f.wire.sink = "http-get:*:audio/mpeg:*"
            f.connect(); advanceUntilIdle()
            assertEquals(0, f.wire.loads)
            assertEquals(0, f.store.writes)
            assertEquals(NaviampRemoteOutputPhase.Incompatible, (f.outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
            assertEquals(0, f.local.starts)
            f.controller.selectLocal(); advanceUntilIdle()
            assertEquals(NaviampPlaybackOutputSelection.Local, f.outputs.state.value)
        } finally { f.close() }
    }

    @Test fun delayedPreparationAfterAnEditCannotInstallStaleNextOrLeakItsLease() = runTest {
        val f = Fixture(this)
        try {
            f.wire.nextGate = CompletableDeferred()
            f.connect(); runCurrent()
            assertNotNull(f.wire.pendingNext)
            val stale = assertNotNull(f.wire.pendingNext)
            f.queue.replaceQueue(PlaybackQueue(listOf(f.track), 0))
            runCurrent()
            f.wire.nextGate?.complete(Unit)
            advanceUntilIdle()
            assertNull(f.wire.next)
            assertEquals(404, f.server.get(stale).status)
            assertEquals(0, f.live.state.value.queue.currentIndex)
            assertEquals(0, f.local.starts)
        } finally { f.close() }
    }

    private class Fixture(test: TestScope) {
        val ownScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val wire = Renderer()
        val outputs = NaviampPlaybackOutputSelectionController()
        val baseProvider = FakeCoreMediaProvider(supportsStreamingTranscode = true)
        val track = baseProvider.track.copy(audioInfo = AudioInfo("flac", null, "audio/x-flac", 16, 44_100))
        val bytes = byteArrayOf(102, 76, 97, 67, 1, 2, 3, 4) // Transport fixture; decoded audio is qualified separately.
        val requests = mutableListOf<StreamRequest>()
        val provider = object : MediaProvider by baseProvider {
            override val cacheNamespace = "account-a"
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                requests += request
                onResponse(ProviderMediaByteResponse(200, "audio/x-flac", bytes.size.toLong(), null))
                if (!headOnly) writeChunk(bytes, bytes.size)
                return true
            }
        }
        var providerCurrent: MediaProvider? = provider
        val source = NaviampCoreMediaProviderSource { providerCurrent }
        val live = NaviampLivePlaybackController(NaviampLivePlaybackState(currentTrack = track,
            queue = PlaybackQueue(listOf(track, track, track), 0), playbackState = PlaybackState.Playing,
            progress = PlaybackProgress(0.0, 180.0)))
        val queue = NaviampPlaybackQueueCoordinator(live)
        val session = NaviampCoreUpnpSession(ownScope, outputs, pollIntervalMillis = null)
        val server = Server()
        val store = FakeCastMediaStore()
        val local = Local()
        var now = 0L
        private var token = 0
        val controller = NaviampCoreRemotePlaybackController(ownScope, session, outputs,
            NaviampCastMediaEndpointController(server, NaviampCastMediaLeaseController(
                NaviampCastSecureTokenSource { "upnp-integration-test-${(++token).toString().padStart(32, '0')}" }, { now }),
                NaviampCoreCastMediaByteSource(source, store)), source, live, queue, local, {})
        suspend fun connect() {
            controller.start()
            val description = parseNaviampUpnpRendererDescription(wire.description(), "http://192.0.2.2/device.xml", "uuid:fixture")
            assertTrue(session.select(NaviampUpnpRendererClient(wire, description), "192.0.2.1"))
        }
        suspend fun close() { controller.shutdown(); ownScope.cancel() }
    }

    private class Local : NaviampCorePlaybackEffectPort {
        override val capabilities = NaviampCorePlaybackCapabilities()
        override val playbackSource = PlaybackSource.ProviderStream
        var starts = 0
        override fun pause() = Unit
        override fun resume() = Unit
        override fun startOrRestore(): Boolean { starts++; return true }
        override fun seek(positionSeconds: Double) = Unit
        override fun replayCurrent(positionSeconds: Double) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun stop() = Unit
        override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
        override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
        override fun applyRepeatMode(mode: RepeatMode) = Unit
        override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
    }
    private class Reply : NaviampCastHttpResponse {
        var status = 0; var headers = emptyMap<String, String>(); val body = mutableListOf<Byte>()
        override suspend fun begin(statusCode: Int, headers: Map<String, String>) { status = statusCode; this.headers = headers }
        override suspend fun write(bytes: ByteArray, count: Int) { body += bytes.take(count) }
    }
    private class Server : NaviampCastHttpServerEffect {
        private var handler: (suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit)? = null
        override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String { this.handler = handler; return "http://192.0.2.1:1234" }
        override suspend fun stop() { handler = null }
        suspend fun get(url: String): Reply = Reply().also { reply ->
            handler?.invoke(NaviampCastHttpRequest("GET", url.removePrefix("http://192.0.2.1:1234"), null), reply) ?: reply.begin(404, emptyMap())
        }
    }
    private class Renderer : NaviampUpnpHttpTransport {
        var current = ""; var next: String? = null; var state = "STOPPED"
        var loads = 0; var plays = 0; var position = 0L; var stops = 0
        var duration = "00:03:00"
        var seekTarget: String? = null
        var ignoreLoad = false
        var sink = "http-get:*:audio/flac:*"
        var failNext = false
        var nextGate: CompletableDeferred<Unit>? = null
        var stopGate: CompletableDeferred<Unit>? = null
        var pendingNext: String? = null
        val operations = mutableListOf<String>()
        fun description() = "<root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>Fixture</friendlyName><UDN>uuid:fixture</UDN><serviceList>" +
            NaviampUpnpServiceKind.entries.joinToString("") { "<service><serviceType>urn:schemas-upnp-org:service:$it:1</serviceType><controlURL>/control/$it</controlURL><eventSubURL>/event/$it</eventSubURL><SCPDURL>/scpd/$it</SCPDURL></service>" } + "</serviceList></device></root>"
        fun promote() { current = checkNotNull(next); next = null; state = "PLAYING"; position = 0 }
        override suspend fun exchange(request: NaviampUpnpHttpRequest): NaviampUpnpHttpResponse {
            if (request.method == "GET" && request.url.endsWith("device.xml")) return NaviampUpnpHttpResponse(200, emptyMap(), description())
            if (request.method == "GET") return NaviampUpnpHttpResponse(200, emptyMap(), "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><actionList>" +
                listOf("GetProtocolInfo", "SetAVTransportURI", "SetNextAVTransportURI", "Play", "Pause", "Stop", "Seek", "SetVolume", "GetPositionInfo", "GetTransportInfo")
                    .joinToString("") { "<action><name>$it</name></action>" } + "</actionList></scpd>")
            val action = request.headers.getValue("SOAPACTION").trim('"').substringAfter('#')
            val kind = request.url.substringAfterLast('/')
            fun field(name: String) = request.body.substringAfter("<$name>").substringBefore("</$name>")
            when (action) {
                "SetAVTransportURI" -> { if (!ignoreLoad) current = field("CurrentURI"); loads++; state = "STOPPED"; position = 0 }
                "SetNextAVTransportURI" -> {
                    val uri = field("NextURI")
                    if (uri.isNotEmpty()) {
                        if (failNext) return NaviampUpnpHttpResponse(500, emptyMap(), fault())
                        pendingNext = uri; nextGate?.await(); next = uri; operations += "next"
                    } else { next = null; operations += "clear-next" }
                }
                "Play" -> { state = "PLAYING"; plays++ }
                "Pause" -> state = "PAUSED_PLAYBACK"
                "Stop" -> { stopGate?.await(); state = "STOPPED"; position = 180_000; stops++ }
                "Seek" -> { seekTarget = field("Target"); position = 0 }
            }
            val fields = when (action) {
                "GetProtocolInfo" -> "<Source/><Sink>$sink</Sink>"
                "GetPositionInfo" -> "<TrackURI>$current</TrackURI><RelTime>00:00:${(position / 1_000 % 60).toString().padStart(2, '0')}</RelTime><TrackDuration>$duration</TrackDuration>".let {
                    if (position == 180_000L) it.replace("<RelTime>00:00:00</RelTime>", "<RelTime>00:03:00</RelTime>") else it
                }
                "GetTransportInfo" -> "<CurrentTransportState>$state</CurrentTransportState>"
                else -> ""
            }
            return NaviampUpnpHttpResponse(200, emptyMap(), "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><r:${action}Response xmlns:r=\"urn:schemas-upnp-org:service:$kind:1\">$fields</r:${action}Response></s:Body></s:Envelope>")
        }
        private fun fault() = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>701</errorCode><errorDescription>Cannot prepare</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
    }
}
