package app.naviamp.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** Portable Cast sender. The injected transport executes TLS/socket effects, never session policy. */
class NaviampCastChannelSessionEffect(
    private val scope: CoroutineScope,
    private val transport: NaviampCastTransportFactory,
    private val authentication: NaviampCastDeviceAuthentication,
    private val nowEpochMillis: () -> Long,
    private val requestTimeoutMillis: Long = 10_000,
    private val heartbeatIntervalMillis: Long = 5_000,
) : NaviampCastSessionEffect {
    init { require(requestTimeoutMillis > 0 && heartbeatIntervalMillis > 0) }

    private data class Pending(
        val namespace: String,
        val source: String,
        val result: CompletableDeferred<JsonObject?>,
    )
    private class Session(
        val id: Long,
        val target: NaviampCastTarget,
        val connection: NaviampCastTransportConnection,
    ) {
        val sender = "naviamp-$id"
        val pending = mutableMapOf<Int, Pending>()
        val writeMutex = Mutex()
        var requestId = 0
        var reader: Job? = null
        var heartbeat: Job? = null
        var progress: Job? = null
        var applicationTransport: String? = null
        var applicationSession: String? = null
        var mediaSession: Long? = null
        var mediaUrl: String? = null
        var volume: Int? = null
        var lastPong = 0L
        var intentionalClose = false
        val mediaStatus = MutableStateFlow<NaviampCastReceiverStatus?>(null)
    }

    private var listener: NaviampCastSessionListener? = null
    private var session: Session? = null
    private var revision = 0L
    /** Socket's selected route, used by the media binding effect. Not an interface-selection policy. */
    override val localAddress: String? get() = session?.connection?.localAddress

    override fun start(listener: NaviampCastSessionListener) { this.listener = listener }

    override fun stop() {
        ++revision
        listener = null
        session?.let(::close)
    }

    override suspend fun connect(selectionId: Long, target: NaviampCastDiscoveredTarget): Boolean {
        val callbacks = listener ?: return false
        val current = ++revision
        session?.let(::close)
        callbacks.onConnecting(selectionId)
        for (endpoint in target.endpoints) {
            var cleanupCandidate: Session? = null
            try {
                val connected = withTimeoutOrNull(requestTimeoutMillis) { transport.connect(endpoint) } ?: continue
                val candidate = Session(selectionId, target.target, connected)
                cleanupCandidate = candidate
                if (current != revision || listener !== callbacks) { close(candidate); return false }
                session = candidate
                val authenticated = withTimeoutOrNull(requestTimeoutMillis) {
                    val nonce = authentication.nonce()
                    send(candidate, NaviampCastChannelMessage("sender-0", Receiver, Auth,
                        binary = authentication.challenge(nonce)))
                    var verified = false
                    while (!verified) {
                        val frame = connected.receive() ?: break
                        val message = NaviampCastChannelCodec.decode(frame)
                        if (message.namespace == Auth && message.sourceId == Receiver &&
                            message.destinationId == "sender-0") {
                            verified = message.binary?.let {
                                authentication.verify(it, nonce, connected.peerCertificateDer)
                            } == true
                            break
                        }
                    }
                    verified
                } == true
                if (!authenticated) { close(candidate); continue }
                candidate.lastPong = nowEpochMillis()
                candidate.reader = scope.launch { read(candidate) }
                json(candidate, Receiver, Connection, buildJsonObject {
                    put("type", "CONNECT"); put("connType", 0); put("origin", JsonObject(emptyMap()))
                    put("userAgent", "Naviamp"); put("senderInfo", buildJsonObject { put("sdkType", 2) })
                })
                val launched = request(candidate, Receiver, ReceiverNamespace, buildJsonObject {
                    put("type", "LAUNCH"); put("appId", DefaultMediaReceiver)
                })
                if (launched?.string("type") != "RECEIVER_STATUS" || candidate.applicationTransport == null ||
                    session !== candidate || current != revision) { close(candidate); continue }
                json(candidate, checkNotNull(candidate.applicationTransport), Connection,
                    buildJsonObject {
                        put("type", "CONNECT"); put("connType", 0); put("origin", JsonObject(emptyMap()))
                        put("userAgent", "Naviamp"); put("senderInfo", buildJsonObject { put("sdkType", 2) })
                    })
                candidate.heartbeat = scope.launch {
                    while (isActive && session === candidate) {
                        delay(heartbeatIntervalMillis)
                        if (nowEpochMillis() - candidate.lastPong > heartbeatIntervalMillis * 3) {
                            failed(candidate); break
                        }
                        try {
                            json(candidate, Receiver, Heartbeat, buildJsonObject { put("type", "PING") })
                        } catch (_: Exception) { failed(candidate); break }
                    }
                }
                candidate.progress = scope.launch {
                    while (isActive && session === candidate) {
                        delay(1_000)
                        if (candidate.mediaSession == null) continue
                        try {
                            json(candidate, checkNotNull(candidate.applicationTransport), Media, buildJsonObject {
                                put("type", "GET_STATUS"); put("requestId", nextRequestId(candidate))
                            })
                        } catch (_: Exception) { failed(candidate); break }
                    }
                }
                if (current != revision || session !== candidate) { close(candidate); return false }
                callbacks.onConnected(selectionId, target.target.displayName)
                return true
            } catch (cancelled: CancellationException) {
                cleanupCandidate?.let(::close)
                throw cancelled
            } catch (_: Exception) {
                cleanupCandidate?.let(::close)
            }
            if (current != revision || listener !== callbacks) return false
        }
        return false
    }

    override fun disconnect() {
        ++revision
        val previous = session ?: return
        previous.intentionalClose = true
        // Keep the last receiver position published. Core owns returning to local at that position.
        session = null
        previous.heartbeat?.cancel()
        previous.progress?.cancel()
        scope.launch {
            try {
                previous.applicationSession?.let { appSession ->
                    request(previous, Receiver, ReceiverNamespace, buildJsonObject {
                        put("type", "STOP"); put("sessionId", appSession)
                    })
                }
            } finally { close(previous) }
        }.invokeOnCompletion { close(previous) }
    }

    override suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean {
        val current = session?.takeIf { it.id == selectionId } ?: return false
        val destination = current.applicationTransport ?: return false
        current.mediaStatus.value = null
        val reply = request(current, destination, Media, buildJsonObject {
            put("type", "LOAD"); put("autoplay", media.autoplay)
            put("currentTime", media.positionMillis.coerceAtLeast(0) / 1_000.0)
            put("media", buildJsonObject {
                put("contentId", media.mediaUrl); put("contentType", media.contentType); put("streamType", "BUFFERED")
                media.durationMillis?.let { put("duration", it / 1_000.0) }
                put("metadata", buildJsonObject {
                    put("metadataType", 3); put("title", media.title); put("artist", media.artist)
                    media.album?.let { put("albumName", it) }
                    media.artworkUrl?.let { url -> put("images", buildJsonArray { add(buildJsonObject { put("url", url) }) }) }
                })
            })
        })
        val accepted = reply?.string("type") == "MEDIA_STATUS" &&
            reply.array("status").any { entry ->
                val status = entry as? JsonObject
                status?.obj("media")?.string("contentId") == media.mediaUrl &&
                    status.long("mediaSessionId") != null && status.string("idleReason") != "ERROR"
            }
        if (!accepted || session !== current) return false
        // LOAD can echo an IDLE session before the receiver has fetched any bytes. Do not hand
        // playback authority away from local audio until the receiver reports usable media.
        val ready = withTimeoutOrNull(requestTimeoutMillis) {
            current.mediaStatus.first { status ->
                status?.mediaUrl == media.mediaUrl && status.playerState in setOf(
                    NaviampCastReceiverPlayerState.Paused, NaviampCastReceiverPlayerState.Playing,
                    NaviampCastReceiverPlayerState.Failed,
                )
            }
        }
        return ready != null && ready.playerState != NaviampCastReceiverPlayerState.Failed && session === current
    }

    override suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean {
        val current = session?.takeIf { it.id == selectionId } ?: return false
        if (command is NaviampCastReceiverCommand.Volume) {
            val reply = request(current, Receiver, ReceiverNamespace, buildJsonObject {
                put("type", "SET_VOLUME"); put("volume", buildJsonObject { put("level", command.percent.coerceIn(0, 100) / 100.0) })
            })
            return reply?.string("type") == "RECEIVER_STATUS" && session === current
        }
        val destination = current.applicationTransport ?: return false
        val mediaId = current.mediaSession ?: return false
        val reply = request(current, destination, Media, buildJsonObject {
            put("type", when (command) {
                NaviampCastReceiverCommand.Play -> "PLAY"
                NaviampCastReceiverCommand.Pause -> "PAUSE"
                NaviampCastReceiverCommand.Stop -> "STOP"
                is NaviampCastReceiverCommand.Seek -> "SEEK"
                is NaviampCastReceiverCommand.Volume -> error("Handled above")
            })
            put("mediaSessionId", mediaId)
            if (command is NaviampCastReceiverCommand.Seek) put("currentTime", command.positionMillis.coerceAtLeast(0) / 1_000.0)
        })
        return reply?.string("type") == "MEDIA_STATUS" && session === current
    }

    private suspend fun read(current: Session) {
        try {
            while (true) {
                val frame = current.connection.receive() ?: break
                val message = NaviampCastChannelCodec.decode(frame)
                if (message.destinationId !in setOf(current.sender, "sender-0", "*")) continue
                val text = message.text ?: continue
                val payload = Json.parseToJsonElement(text) as? JsonObject ?: continue
                if (message.namespace == Heartbeat && message.sourceId == Receiver) {
                    when (payload.string("type")) {
                        "PING" -> json(current, Receiver, Heartbeat, buildJsonObject { put("type", "PONG") })
                        "PONG" -> current.lastPong = nowEpochMillis()
                    }
                }
                if (message.namespace == Connection && payload.string("type") == "CLOSE" &&
                    message.sourceId in setOf(Receiver, current.applicationTransport)) break
                if (message.namespace == ReceiverNamespace && message.sourceId == Receiver &&
                    payload.string("type") == "RECEIVER_STATUS") receiverStatus(current, payload)
                if (message.namespace == Media && message.sourceId == current.applicationTransport &&
                    payload.string("type") == "MEDIA_STATUS") mediaStatus(current, payload)
                payload.int("requestId")?.let { id ->
                    current.pending[id]?.takeIf { it.namespace == message.namespace && it.source == message.sourceId }
                        ?.result?.complete(payload)
                }
            }
        } catch (_: CancellationException) {
            return
        } catch (_: Exception) {
            // No protocol content or scoped media tokens enter diagnostics.
        }
        if (!current.intentionalClose) failed(current)
    }

    private fun receiverStatus(current: Session, payload: JsonObject) {
        val status = payload.obj("status") ?: return
        status.obj("volume")?.double("level")?.let { current.volume = (it * 100).toInt().coerceIn(0, 100) }
        val application = status.array("applications").mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("appId") == DefaultMediaReceiver }
        if (application == null && current.applicationTransport != null && !current.intentionalClose) {
            failed(current); return
        }
        application?.let {
            val remote = it.string("transportId")?.takeIf(String::isNotBlank)
            if (current.applicationTransport != null && remote != current.applicationTransport) { failed(current); return }
            current.applicationTransport = remote
            current.applicationSession = it.string("sessionId")
        }
    }

    private fun mediaStatus(current: Session, payload: JsonObject) {
        if (session !== current) return
        val status = payload.array("status").firstOrNull() as? JsonObject ?: return
        val id = status.long("mediaSessionId") ?: return
        val media = status.obj("media")
        val url = media?.string("contentId") ?: current.mediaUrl?.takeIf { current.mediaSession == id }
        current.mediaSession = id
        current.mediaUrl = url
        val playerState = when (status.string("playerState")) {
            "PLAYING" -> NaviampCastReceiverPlayerState.Playing
            "PAUSED" -> NaviampCastReceiverPlayerState.Paused
            "BUFFERING" -> NaviampCastReceiverPlayerState.Buffering
            "IDLE" -> if (status.string("idleReason") == "ERROR") NaviampCastReceiverPlayerState.Failed else NaviampCastReceiverPlayerState.Idle
            else -> return
        }
        val published = NaviampCastReceiverStatus(
            playerState = playerState,
            positionMillis = ((status.double("currentTime") ?: 0.0).coerceAtLeast(0.0) * 1_000).toLong(),
            durationMillis = media?.double("duration")?.let { (it.coerceAtLeast(0.0) * 1_000).toLong() },
            volumePercent = current.volume,
            finished = status.string("idleReason") == "FINISHED",
            mediaUrl = url,
        )
        current.mediaStatus.value = published
        listener?.onMediaStatus(current.id, published)
    }

    private fun nextRequestId(current: Session): Int {
        check(current.requestId < Int.MAX_VALUE)
        return ++current.requestId
    }

    private suspend fun request(current: Session, destination: String, namespace: String, body: JsonObject): JsonObject? {
        val id = nextRequestId(current)
        val result = CompletableDeferred<JsonObject?>()
        current.pending[id] = Pending(namespace, destination, result)
        return try {
            withTimeoutOrNull(requestTimeoutMillis) {
                json(current, destination, namespace, JsonObject(body + ("requestId" to JsonPrimitive(id))))
                result.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!current.intentionalClose) failed(current)
            null
        } finally { current.pending.remove(id); result.cancel() }
    }

    private suspend fun json(current: Session, destination: String, namespace: String, body: JsonObject) =
        send(current, NaviampCastChannelMessage(current.sender, destination, namespace, text = body.toString()))

    private suspend fun send(current: Session, message: NaviampCastChannelMessage) {
        current.writeMutex.withLock { current.connection.send(NaviampCastChannelCodec.encode(message)) }
    }

    private fun failed(current: Session) {
        if (session !== current) return
        close(current)
        listener?.onDisconnected(current.id)
    }

    private fun close(current: Session) {
        if (session === current) session = null
        current.connection.close() // Close first to unblock a native read before cancelling its coroutine.
        current.reader?.cancel()
        current.heartbeat?.cancel()
        current.progress?.cancel()
        current.pending.values.forEach { it.result.complete(null) }
        current.pending.clear()
    }

    companion object {
        const val DefaultMediaReceiver = "CC1AD845"
        const val Receiver = "receiver-0"
        const val Auth = "urn:x-cast:com.google.cast.tp.deviceauth"
        const val Connection = "urn:x-cast:com.google.cast.tp.connection"
        const val Heartbeat = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val ReceiverNamespace = "urn:x-cast:com.google.cast.receiver"
        const val Media = "urn:x-cast:com.google.cast.media"
    }
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
private fun JsonObject.double(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
