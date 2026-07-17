package app.naviamp.app

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import kotlin.test.Test
import kotlin.test.assertSame

class NaviampApplicationServicesTest {
    @Test
    fun preservesTheRequiredDependencyAwareServiceInstances() {
        val settingsSync = NaviampSettingsSyncController(
            deviceId = "test",
            state = { SettingsSyncRuntimeState() },
            saveState = {},
            nowEpochMillis = { 1L },
            snapshot = { SettingsSyncLocalSnapshot() },
            applyDocument = {},
        )
        val cacheSettings = NaviampCacheSettingsController(
            setSettings = {},
            saveSettings = {},
        )
        val cacheMaintenance = NaviampCacheMaintenanceController(
            repository = EmptyCacheMaintenanceRepository,
            setStatus = {},
        )

        val services = NaviampApplicationServices(
            settingsSync = settingsSync,
            cacheSettings = cacheSettings,
            cacheMaintenance = cacheMaintenance,
        )

        assertSame(settingsSync, services.settingsSync)
        assertSame(cacheSettings, services.cacheSettings)
        assertSame(cacheMaintenance, services.cacheMaintenance)
    }
}

private object EmptyCacheMaintenanceRepository : CacheMaintenanceRepository<Unit> {
    override fun clearProviderData() = Unit
    override fun clearCacheData() = Unit
    override fun clearDownloadData() = Unit
    override fun clearAll() = Unit
    override fun stats() = Unit
}
