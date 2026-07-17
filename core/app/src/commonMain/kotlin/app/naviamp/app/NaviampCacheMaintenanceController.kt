package app.naviamp.app

import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository

/** Shared ordering for clearing cache data while allowing hosts to clear their native files/state. */
class NaviampCacheMaintenanceController<Stats>(
    private val repository: CacheMaintenanceRepository<Stats>,
    private val clearPlatformCaches: () -> Unit = {},
    private val clearDerivedState: () -> Unit = {},
    private val setStatus: (String) -> Unit,
) {
    fun clearCache(detailedStatus: Boolean = false) {
        repository.clearCacheData()
        clearPlatformCaches()
        clearDerivedState()
        setStatus(cacheDataClearedStatus(detailedStatus))
    }

    fun stats(): Stats = repository.stats()
}
