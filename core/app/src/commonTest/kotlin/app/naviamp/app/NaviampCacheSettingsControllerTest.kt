package app.naviamp.app

import app.naviamp.domain.settings.CacheSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCacheSettingsControllerTest {
    @Test
    fun normalizesThenPublishesPersistsAndAppliesOneSettingsSnapshot() {
        val events = mutableListOf<Pair<String, CacheSettings>>()
        val controller = NaviampCacheSettingsController(
            setSettings = { events += "set" to it },
            saveSettings = { events += "save" to it },
            applyPlatformSettings = { events += "apply" to it },
        )

        val result = controller.apply(CacheSettings(unmeteredAudioPrefetchDepth = -10, meteredAudioPrefetchDepth = -10))

        assertEquals(listOf("set", "save", "apply"), events.map { it.first })
        assertEquals(result, events[0].second)
        assertEquals(result, events[1].second)
        assertEquals(result, events[2].second)
        assertEquals(CacheSettings(unmeteredAudioPrefetchDepth = -10, meteredAudioPrefetchDepth = -10).normalized(), result)
    }
}
