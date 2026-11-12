package app.naviamp.ui

/** LRU for decoded artwork. Callers serialize access with their existing loading mutex. */
internal class NaviampArtworkMemoryCache<T>(
    private val maximumBytes: Long,
    private val maximumEntries: Int,
    private val sizeOf: (T) -> Long,
) {
    private val entries = linkedMapOf<String, Pair<T, Long>>()
    var bytes: Long = 0
        private set

    operator fun get(key: String): T? = entries.remove(key)?.also { entries[key] = it }?.first

    fun put(key: String, value: T) {
        entries.remove(key)?.let { bytes -= it.second }
        val size = sizeOf(value).coerceAtLeast(0)
        if (size > maximumBytes) return
        entries[key] = value to size
        bytes += size
        while (bytes > maximumBytes || entries.size > maximumEntries) {
            bytes -= entries.remove(entries.keys.first())!!.second
        }
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }
}
