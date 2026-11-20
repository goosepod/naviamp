package app.naviamp.presentation

import kotlinx.coroutines.*

/** App-owned artwork lifetime shared by native Now Playing surfaces; media IDs alone are not source identities. */
class NaviampExternalPlaybackArtworkController(
    private val scope: CoroutineScope,
    private val snapshot: () -> NaviampExternalPlaybackSnapshot,
    private val load: suspend (String) -> ByteArray?,
    private val changed: (ByteArray?) -> Unit,
) {
    private data class Key(val source: Triple<String?, Long, Long>?, val local: Boolean, val mediaId: String, val url: String?)
    private fun key(value: NaviampExternalPlaybackSnapshot) = value.current?.let { Key(value.sourceScope(), value.localPlayback, it.mediaId, it.artworkUrl) }
    private var current: Key? = null
    private var serial = 0L
    private var job: Job? = null
    private var closed = false

    fun update(value: NaviampExternalPlaybackSnapshot) {
        if (closed) return
        val next = key(value)
        if (next == current) return
        if (next != key(snapshot())) return
        current = next
        val accepted = ++serial
        job?.cancel(); job = null
        changed(null)
        val url = next?.url?.takeIf(String::isNotBlank) ?: return
        if (closed || accepted != serial) return
        val loading = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val bytes = try { load(url) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                ensureActive()
                if (!closed && accepted == serial && next == key(snapshot()) && bytes != null && bytes.isNotEmpty()) changed(bytes)
            } finally {
                if (accepted == serial) job = null
            }
        }
        job = loading
        loading.start()
    }

    fun close() { closed = true; serial++; job?.cancel(); job = null; current = null }
}
