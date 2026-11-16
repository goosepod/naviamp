package app.naviamp.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class NaviampUpnpDiscoveryPacket(val sourceHost: String, val localAddress: String, val datagram: String)
interface NaviampUpnpDiscoveryEffect {
    /** Blocking native operations; Core dispatches these on its injected effect context. */
    fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit)
    fun refresh(search: String)
    fun stop()
}
data class NaviampUpnpDiscoveredRenderer(val id: String, val renderer: NaviampUpnpRendererDescription,
    val location: String, val localAddress: String)
data class NaviampUpnpDiscoveryState(val targets: List<NaviampUpnpDiscoveredRenderer> = emptyList(), val failed: Boolean = false)

/** Common scan generation, per-interface expiry and bounded description acquisition. */
class NaviampUpnpDiscoveryController(
    private val scope: CoroutineScope,
    private val effect: NaviampUpnpDiscoveryEffect,
    private val http: NaviampUpnpHttpTransport,
    private val nowMillis: () -> Long,
    private val effectContext: CoroutineContext = EmptyCoroutineContext,
) {
    private data class Key(val udn: String, val host: String, val local: String)
    private class Entry(val location: String, var expires: Long, var target: NaviampUpnpDiscoveredRenderer? = null, var job: Job? = null)
    private val entries = mutableMapOf<Key, Entry>()
    private val mutableState = MutableStateFlow(NaviampUpnpDiscoveryState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var running = false
    private val operations = Mutex()

    fun start() {
        if (running) return
        running = true
        val current = ++generation
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.withLock {
                if (!running || current != generation) return@withLock
                try {
                    withContext(effectContext) {
                        effect.start(naviampUpnpSearchDatagram()) { packet ->
                            scope.launch { if (running && current == generation) receive(packet, current) }
                        }
                    }
                } catch (failure: Exception) {
                    withContext(NonCancellable + effectContext) { runCatching { effect.stop() } }
                    if (current == generation) {
                        running = false; ++generation
                        entries.values.forEach { it.job?.cancel() }; entries.clear()
                        mutableState.value = NaviampUpnpDiscoveryState(failed = true)
                    }
                    if (failure is CancellationException) throw failure
                }
            }
        }
    }
    fun refresh() {
        if (!running) return
        val current = generation
        scope.launch(start = CoroutineStart.UNDISPATCHED) { operations.withLock {
            if (!running || current != generation) return@withLock
            try { withContext(effectContext) { effect.refresh(naviampUpnpSearchDatagram()) } }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (current == generation) mutableState.value = state.value.copy(failed = true)
            }
        } }
    }
    fun stop() {
        running = false; ++generation
        entries.values.forEach { it.job?.cancel() }; entries.clear()
        mutableState.value = NaviampUpnpDiscoveryState()
        // A cancelled UI scope must still release sockets/locks, after any in-flight startup.
        scope.launch(NonCancellable, start = CoroutineStart.UNDISPATCHED) {
            operations.withLock { withContext(effectContext) { runCatching { effect.stop() } } }
        }
    }
    fun refreshExpiry() {
        val expired = entries.filterValues { it.expires <= nowMillis() }.keys
        expired.forEach { entries.remove(it)?.job?.cancel() }
        publish()
    }
    fun target(id: String) = state.value.targets.firstOrNull { it.id == id }

    private fun receive(packet: NaviampUpnpDiscoveryPacket, current: Long) {
        if (packet.localAddress.isBlank()) return
        val advertisement = try { parseNaviampUpnpAdvertisement(packet.datagram, packet.sourceHost) }
        catch (_: NaviampUpnpProtocolException) { return }
        val key = Key(advertisement.udn, advertisement.sourceHost, packet.localAddress)
        if (advertisement is NaviampUpnpAdvertisement.Departed) {
            entries.remove(key)?.job?.cancel(); publish(); return
        }
        advertisement as NaviampUpnpAdvertisement.Available
        val now = nowMillis()
        val ttl = advertisement.maxAgeSeconds * 1_000
        if (now > Long.MAX_VALUE - ttl) return
        val existing = entries[key]
        if (existing?.location == advertisement.location) { existing.expires = now + ttl; return }
        if (existing == null && entries.size >= 64) return
        existing?.job?.cancel()
        val entry = Entry(advertisement.location, now + ttl)
        entries[key] = entry
        publish()
        entry.job = scope.launch {
            try {
                val reply = withTimeout(5_000) { http.exchange(NaviampUpnpHttpRequest("GET", entry.location)) }
                upnpCheck(reply.status == 200)
                val renderer = parseNaviampUpnpRendererDescription(reply.body, entry.location, key.udn)
                if (running && current == generation && entries[key] === entry && entry.expires > nowMillis()) {
                    entry.target = NaviampUpnpDiscoveredRenderer("upnp|${key.udn}|${key.host}|${key.local}", renderer, entry.location, key.local)
                    publish()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (entries[key] === entry) { entries.remove(key); publish() } }
        }
    }
    private fun publish() {
        mutableState.value = NaviampUpnpDiscoveryState(entries.values.mapNotNull { it.target }.sortedBy { it.renderer.name })
    }
}
