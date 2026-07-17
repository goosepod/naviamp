package app.naviamp.app

import app.naviamp.domain.cache.CacheMaintenanceRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCacheMaintenanceControllerTest {
    @Test
    fun clearsRepositoryPlatformFilesAndDerivedStateBeforePublishingStatus() {
        val events = mutableListOf<String>()
        val controller = NaviampCacheMaintenanceController(
            repository = RecordingCacheMaintenanceRepository(events),
            clearPlatformCaches = { events += "platform" },
            clearDerivedState = { events += "derived" },
            setStatus = { status -> events += status },
        )

        controller.clearCache()

        assertEquals(
            listOf("repository", "platform", "derived", "Cache cleared."),
            events,
        )
    }
}

private class RecordingCacheMaintenanceRepository(
    private val events: MutableList<String>,
) : CacheMaintenanceRepository<Unit> {
    override fun clearProviderData() = Unit
    override fun clearCacheData() { events += "repository" }
    override fun clearDownloadData() = Unit
    override fun clearAll() = Unit
    override fun stats() = Unit
}
