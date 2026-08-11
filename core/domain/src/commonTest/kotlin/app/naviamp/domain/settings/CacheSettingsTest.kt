package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class CacheSettingsTest {
    @Test
    fun defaultsUseTheSmallestActiveCacheAndDownloadChoices() {
        val settings = CacheSettings()

        assertEquals(2, settings.audioPrefetchDepth)
        assertEquals(256L * 1024L * 1024L, settings.maxAudioCacheBytes)
        assertEquals(512L * 1024L * 1024L, settings.maxDownloadBytes)
    }

    @Test
    fun normalizationUsesTheDefaultBudgetsAsStorageFloors() {
        val settings = CacheSettings(
            maxAudioCacheBytes = 1L,
            maxDownloadBytes = 1L,
        ).normalized()

        assertEquals(DefaultAudioCacheBytes, settings.maxAudioCacheBytes)
        assertEquals(DefaultDownloadStorageBytes, settings.maxDownloadBytes)
    }
}
