package app.naviamp.presentation

import app.naviamp.domain.cache.StorageCacheStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreCachedDiagnosticsPortTest {
    @Test
    fun cachesStorageWorkButAlwaysRefreshesCheapPlatformFacts() {
        var now = 100L
        var storageCalls = 0
        var platformValue = "first"
        val port = NaviampCoreCachedDiagnosticsPort(
            platformRows = { listOf("OS" to platformValue) },
            storageStats = { StorageCacheStats(audioCount = (++storageCalls).toLong()) },
            nowEpochMillis = { now },
            refreshIntervalMillis = 2_000L,
        )

        assertEquals(1L, port.snapshot().storage?.audioCount)
        platformValue = "second"
        now += 1_999L
        assertEquals("second", port.snapshot().platformRows.single().second)
        assertEquals(1, storageCalls)
        now += 1L
        assertEquals(2L, port.snapshot().storage?.audioCount)
    }

    @Test
    fun failedStorageReadsRemainNullAndRetryAfterTheInterval() {
        var now = 0L
        var attempts = 0
        val port = NaviampCoreCachedDiagnosticsPort(
            platformRows = { emptyList() },
            storageStats = {
                attempts += 1
                if (attempts == 1) error("busy") else StorageCacheStats(audioCount = 7)
            },
            nowEpochMillis = { now },
            refreshIntervalMillis = 10L,
        )

        assertNull(port.snapshot().storage)
        now = 9L
        assertNull(port.snapshot().storage)
        now = 10L
        assertEquals(7L, port.snapshot().storage?.audioCount)
    }
}
