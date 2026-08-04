package app.naviamp.app

import app.naviamp.domain.settings.CacheSettings

/** Owns normalization and persistence order for cache and download settings. */
class NaviampCacheSettingsController(
    private val setSettings: (CacheSettings) -> Unit,
    private val saveSettings: (CacheSettings) -> Unit,
    private val applyPlatformSettings: (CacheSettings) -> Unit = {},
) {
    fun apply(requested: CacheSettings): CacheSettings {
        val settings = requested.normalized()
        setSettings(settings)
        saveSettings(settings)
        applyPlatformSettings(settings)
        return settings
    }
}
