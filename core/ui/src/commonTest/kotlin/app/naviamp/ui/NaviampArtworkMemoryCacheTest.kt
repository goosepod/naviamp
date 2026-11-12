package app.naviamp.ui

import kotlin.test.*

class NaviampArtworkMemoryCacheTest {
    @Test fun largerCoversEvictByBytesAndRecentlyUsedCoversSurvive() {
        val cache = NaviampArtworkMemoryCache<Int>(10, 10) { it.toLong() }
        cache.put("a", 4)
        cache.put("b", 4)
        assertEquals(4, cache["a"])
        cache.put("c", 5)
        assertNull(cache["b"])
        assertEquals(4, cache["a"])
        assertEquals(9L, cache.bytes)
        cache.put("a", 2)
        assertEquals(7L, cache.bytes)
        cache.put("oversize", 11)
        assertNull(cache["oversize"])
        assertEquals(5, cache["c"])
        cache.clear()
        assertEquals(0L, cache.bytes)
        assertNull(cache["a"])
    }

    @Test fun tinyEntriesStillRespectTheEntryLimit() {
        val cache = NaviampArtworkMemoryCache<Int>(100, 2) { it.toLong() }
        cache.put("a", 1)
        cache.put("b", 1)
        cache.put("c", 1)
        assertNull(cache["a"])
        assertEquals(2L, cache.bytes)
    }

    @Test fun decodingUsesPhysicalPixelsUpToTheHeroResolution() {
        assertEquals(128, naviampCoverArtDecodeSidePx(40f))
        assertEquals(1441, naviampCoverArtDecodeSidePx(1440.5f))
        assertEquals(2048, naviampCoverArtDecodeSidePx(3000f))
    }
}
