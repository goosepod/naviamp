package app.naviamp.desktop

class DesktopHotImageCache(
    private val maxBytes: Long,
) {
    private val images = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var totalBytes: Long = 0

    fun get(url: String): ByteArray? =
        synchronized(images) {
            images[url]
        }

    fun put(url: String, bytes: ByteArray) {
        synchronized(images) {
            images.remove(url)?.let { totalBytes -= it.size.toLong() }
            images[url] = bytes
            totalBytes += bytes.size.toLong()
            trim()
        }
    }

    fun clear() {
        synchronized(images) {
            images.clear()
            totalBytes = 0
        }
    }

    fun count(): Int =
        synchronized(images) {
            images.size
        }

    fun sizeBytes(): Long =
        synchronized(images) {
            totalBytes
        }

    private fun trim() {
        val iterator = images.entries.iterator()
        while (totalBytes > maxBytes && iterator.hasNext()) {
            val entry = iterator.next()
            totalBytes -= entry.value.size.toLong()
            iterator.remove()
        }
    }
}
