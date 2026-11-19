package app.naviamp.presentation

import app.naviamp.domain.ArtistId
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.mediaArtworkIsCircular
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Portable image identity, never inferred from a URL or a containing page. */
data class NaviampVehicleArtworkReference(
    val kind: SharedMediaItemKind,
    val url: String? = null,
    val coverArtId: String? = null,
    val artistId: String? = null,
    val cacheOnly: Boolean = false,
)

/** Opaque scene identity and shared shape/placeholder policy exported to native renderers. */
data class NaviampVehicleArtwork(val id: String, val circular: Boolean, val placeholder: String)

internal fun interface NaviampVehicleArtworkSource {
    suspend fun load(reference: NaviampVehicleArtworkReference): ByteArray?
}

internal class NaviampCoreVehicleArtworkSource(
    private val product: StateFlow<NaviampCoreState>,
    private val providers: NaviampCoreMediaProviderSource,
    private val services: NaviampCoreArtworkServices,
) : NaviampVehicleArtworkSource {
    override suspend fun load(reference: NaviampVehicleArtworkReference): ByteArray? {
        val source = product.value.vehicleSourceKey()
        if (product.value.shell.connectionSettings.currentSourceId.isNullOrBlank() ||
            product.value.shell.connectionSettings.connection.isConnecting ||
            product.value.shell.connectionSettings.connection.restoringConnection) return null
        val provider = providers.current() ?: return null
        fun current() = product.value.vehicleSourceKey() == source && providers.isCurrent(provider)
        val url = reference.url ?: reference.coverArtId?.let { provider.coverArtUrl(it) }
            ?: reference.artistId?.takeUnless { reference.cacheOnly }?.let {
                val info = provider.artist(ArtistId(it)).info
                info?.mediumImageUrl ?: info?.smallImageUrl ?: info?.largeImageUrl
            } ?: return null
        currentCoroutineContext().ensureActive()
        if (!current()) return null
        val bytes = if (reference.cacheOnly) services.cache.cachedImageBytes(provider.artworkCacheKey(url))
        else services.cache.imageBytesForProvider(provider, url) {
            check(current())
            provider.bytesForOwnedUrl(url) ?: services.http.getBytes(url) ?: error("Artwork unavailable")
        }
        currentCoroutineContext().ensureActive()
        return bytes?.takeIf { current() }
    }
}

/** Scene-owned request lifetime, bounded work/cache and stale-result rejection. No native scheduling. */
internal class NaviampVehicleArtworkController(
    private val scope: CoroutineScope,
    private val source: NaviampVehicleArtworkSource?,
    private val timeoutMillis: Long = 5_000,
    private val maximumImageBytes: Int = 2 * 1024 * 1024,
    private val maximumCacheBytes: Int = 8 * 1024 * 1024,
) {
    private class Entry(val reference: NaviampVehicleArtworkReference, val descriptor: NaviampVehicleArtwork) {
        var job: Job? = null
        var deadline: Job? = null
        val callbacks = mutableListOf<(ByteArray?) -> Unit>()
        var failed = false
    }
    private var serial = 0L
    private var generation: List<Any?>? = null
    private val entries = mutableMapOf<NaviampVehicleArtworkReference, Entry>()
    private val seen = mutableSetOf<NaviampVehicleArtworkReference>()
    private val cache = linkedMapOf<String, ByteArray>()
    private val permits = Semaphore(3)
    private var closed = false

    fun begin(sourceKey: List<Any?>) {
        if (generation != sourceKey) { clear(); generation = sourceKey }
        seen.clear()
    }
    fun describe(reference: NaviampVehicleArtworkReference): NaviampVehicleArtwork {
        seen += reference
        return entries.getOrPut(reference) {
            Entry(reference, NaviampVehicleArtwork("artwork:${++serial}", mediaArtworkIsCircular(reference.kind), when (reference.kind) {
                SharedMediaItemKind.Artist -> "Artist"
                SharedMediaItemKind.Album -> "Album"
                SharedMediaItemKind.Playlist -> "Playlist"
                SharedMediaItemKind.RadioStation -> "Radio"
                else -> "Track"
            }))
        }.descriptor
    }
    fun end(retainedIds: Set<String>? = null) {
        entries.keys.filter { key ->
            if (retainedIds == null) key !in seen else entries.getValue(key).descriptor.id !in retainedIds
        }.forEach { key ->
            entries.remove(key)?.let { entry -> cancel(entry); cache.remove(entry.descriptor.id) }
        }
    }
    fun request(id: String, completed: (ByteArray?) -> Unit) {
        val entry = entries.values.firstOrNull { it.descriptor.id == id }
        if (closed || entry == null || source == null || entry.failed) { completed(null); return }
        cache.remove(id)?.let { cache[id] = it; completed(it); return }
        entry.callbacks += completed
        if (entry.job != null) return
        entry.deadline = scope.launch {
            delay(timeoutMillis)
            entry.deadline = null
            entry.failed = true
            val callbacks = entry.callbacks.toList(); entry.callbacks.clear()
            callbacks.forEach { it(null) }
            entry.job?.cancel()
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var bytes: ByteArray? = null
            try {
                bytes = withTimeoutOrNull(timeoutMillis) { permits.withPermit { source.load(entry.reference) } }
                    ?.takeIf { it.isNotEmpty() && it.size <= maximumImageBytes }
                ensureActive()
                if (entries[entry.reference] !== entry || closed) return@launch
                if (bytes != null) {
                    cache[id] = bytes
                    while (cache.values.sumOf { it.size.toLong() } > maximumCacheBytes) cache.remove(cache.keys.first())
                } else entry.failed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { entry.failed = true }
            finally {
                entry.job = null
                entry.deadline?.cancel(); entry.deadline = null
                val callbacks = entry.callbacks.toList(); entry.callbacks.clear()
                callbacks.forEach { it(if (entries[entry.reference] === entry && !closed) bytes else null) }
            }
        }
        entry.job = job
        job.start()
    }
    private fun cancel(entry: Entry) {
        val callbacks = entry.callbacks.toList(); entry.callbacks.clear()
        callbacks.forEach { it(null) }
        entry.deadline?.cancel(); entry.deadline = null
        entry.job?.cancel(); entry.job = null
    }
    private fun clear() { entries.values.toList().forEach(::cancel); entries.clear(); cache.clear() }
    fun close() { closed = true; clear() }
}
