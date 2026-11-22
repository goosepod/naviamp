package app.naviamp.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Native file effect only: replace one private, process-owned artwork file and return its local URI. */
fun interface NaviampExternalArtworkFileWriter {
    suspend fun write(name: String, bytes: ByteArray): String?
}

/**
 * External playback whose current artwork is published as a private local file instead of the
 * provider URL. Core owns when artwork is fetched, file naming, stale-result rejection and the
 * fallback to no artwork; the host only writes bytes.
 *
 * Two alternating file names bound disk use while still giving consumers that cache by URI a new
 * URI for every track change.
 */
class NaviampLocalArtworkExternalPlayback internal constructor(
    scope: CoroutineScope,
    private val control: NaviampExternalPlaybackControl,
    source: Flow<NaviampExternalPlaybackSnapshot>,
    private val load: suspend (String) -> ByteArray?,
    private val files: NaviampExternalArtworkFileWriter,
) : NaviampExternalPlaybackControl by control, AutoCloseable {
    private data class Key(val source: Triple<String?, Long, Long>?, val local: Boolean, val mediaId: String, val url: String)
    private data class Resolved(val key: Key, val uri: String)

    private val resolved = MutableStateFlow<Resolved?>(null)
    private var nextSlot = 0

    val snapshots: Flow<NaviampExternalPlaybackSnapshot> = combine(source, resolved) { snapshot, artwork ->
        snapshot.withLocalArtwork(artwork)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val job: Job = scope.launch {
        source.map(::key).distinctUntilChanged().collectLatest { key ->
            if (resolved.value?.key != key) resolved.value = null
            val url = key?.url?.takeIf(::isRemoteArtworkUrl) ?: return@collectLatest
            val bytes = try { load(url) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (bytes == null || bytes.isEmpty() || key != key(control.snapshot())) return@collectLatest
            val name = "now-playing-$nextSlot"
            val uri = try { files.write(name, bytes) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (uri.isNullOrBlank() || !isLocalArtworkUri(uri) || key != key(control.snapshot())) return@collectLatest
            nextSlot = (nextSlot + 1) % ArtworkFileSlots
            resolved.value = Resolved(key, uri)
        }
    }

    override fun snapshot(): NaviampExternalPlaybackSnapshot = control.snapshot().withLocalArtwork(resolved.value)

    override fun close() {
        job.cancel()
        resolved.value = null
    }

    private fun key(snapshot: NaviampExternalPlaybackSnapshot): Key? = snapshot.current?.let { current ->
        current.artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
            Key(snapshot.sourceScope(), snapshot.localPlayback, current.mediaId, url)
        }
    }

    private fun NaviampExternalPlaybackSnapshot.withLocalArtwork(artwork: Resolved?): NaviampExternalPlaybackSnapshot {
        val current = current ?: return this
        val uri = artwork?.takeIf { it.key == key(this) }?.uri
        return if (current.localArtworkUri == uri) this else copy(current = current.copy(localArtworkUri = uri))
    }
}

private const val ArtworkFileSlots = 2
