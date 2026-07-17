package app.naviamp.app

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
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
        val downloadJobs = NaviampDownloadJobController(jobs = { emptyList() }, setJobs = {})
        val downloads = NaviampDownloadCoordinator(
            downloadRepository = EmptyDownloadStore,
            downloadReplacementRepository = EmptyDownloadStore,
            keepDownloadedRepository = EmptyDownloadStore,
            jobs = downloadJobs,
            downloadedTrackId = { _: Unit -> "" },
            loadStats = { Unit },
        )

        val services = NaviampApplicationServices(
            settingsSync = settingsSync,
            cacheSettings = cacheSettings,
            cacheMaintenance = cacheMaintenance,
            downloadJobs = downloadJobs,
            downloads = downloads,
        )

        assertSame(settingsSync, services.settingsSync)
        assertSame(cacheSettings, services.cacheSettings)
        assertSame(cacheMaintenance, services.cacheMaintenance)
        assertSame(downloadJobs, services.downloadJobs)
        assertSame(downloads, services.downloads)
    }
}

private object EmptyDownloadStore :
    DownloadRepository<Unit, Unit>,
    DownloadReplacementRepository<Unit>,
    KeepDownloadedRepository {
    override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId, quality: StreamQuality) = null
    override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId) = null
    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ) = Unit

    override fun downloadedTracks(sourceId: String) = emptyList<Unit>()
    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = Unit
    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) = Unit

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ) = Unit

    override fun keepDownloadedPolicies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
    override fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = null

    override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) = Unit
    override fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = Unit

    override fun keepDownloadedTrackIds(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = emptySet<String>()

    override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) = Unit
    override fun managedKeepDownloadedTrackIds(sourceId: String) = emptySet<String>()
    override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
    override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
}

private object EmptyCacheMaintenanceRepository : CacheMaintenanceRepository<Unit> {
    override fun clearProviderData() = Unit
    override fun clearCacheData() = Unit
    override fun clearDownloadData() = Unit
    override fun clearAll() = Unit
    override fun stats() = Unit
}
