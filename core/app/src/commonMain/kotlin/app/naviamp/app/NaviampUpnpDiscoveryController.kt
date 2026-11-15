package app.naviamp.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NaviampUpnpDiscoveryPacket(val sourceHost: String, val localAddress: String, val datagram: String)
interface NaviampUpnpDiscoveryEffect {
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
) {
    private data class Key(val udn: String, val host: String, val local: String)
    private class Entry(val location: String, var expires: Long, var target: NaviampUpnpDiscoveredRenderer? = null, var job: Job? = null)
    private val entries = mutableMapOf<Key, Entry>()
    private val mutableState = MutableStateFlow(NaviampUpnpDiscoveryState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var running = false

    fun start() {
        if (running) return
        running = true
        val current = ++generation
        try {
            effect.start(naviampUpnpSearchDatagram()) { packet ->
                scope.launch { if (running && current == generation) receive(packet, current) }
            }
        } catch (failure: Exception) {
            stop()
            mutableState.value = NaviampUpnpDiscoveryState(failed = true)
            if (failure is CancellationException) throw failure
        }
    }
    fun refresh() {
        if (!running) return
        try { effect.refresh(naviampUpnpSearchDatagram()) }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; mutableState.value = state.value.copy(failed = true) }
    }
    fun stop() {
        running = false; ++generation
        entries.values.forEach { it.job?.cancel() }; entries.clear()
        try { effect.stop() } finally { mutableState.value = NaviampUpnpDiscoveryState() }
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
