package app.naviamp.desktop

import app.naviamp.app.NaviampApplicationServices
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampCacheMaintenanceController
import app.naviamp.app.NaviampCacheSettingsController
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun desktopApplicationServices(
    storage: DesktopStorageDependencies,
    downloadJobs: () -> List<DownloadJob>,
    setDownloadJobs: (List<DownloadJob>) -> Unit,
    settingsSyncState: () -> SettingsSyncRuntimeState,
    saveSettingsSyncState: (SettingsSyncRuntimeState) -> Unit,
    settingsSyncSnapshot: () -> SettingsSyncLocalSnapshot,
    applySettingsSyncDocument: (SettingsSyncDocument) -> Unit,
    setCacheSettings: (CacheSettings) -> Unit,
    saveCacheSettings: (CacheSettings) -> Unit,
    publishCacheStatus: (String, NaviampApplicationStatusLevel) -> Unit,
): NaviampApplicationServices<StorageCacheStats, DownloadedAudioFile, DownloadedTrack> {
    val jobs = NaviampDownloadJobController(
        jobs = downloadJobs,
        setJobs = setDownloadJobs,
    )
    val downloads = NaviampDownloadCoordinator(
        downloadRepository = storage,
        downloadReplacementRepository = storage,
        keepDownloadedRepository = storage,
        jobs = jobs,
        downloadedTrackId = { download: DownloadedTrack -> download.track.id.value },
        loadStats = { withContext(Dispatchers.IO) { storage.stats() } },
    )
    return NaviampApplicationServices(
        settingsSync = NaviampSettingsSyncController(
            deviceId = DesktopSettingsSyncDeviceId,
            state = settingsSyncState,
            saveState = saveSettingsSyncState,
            nowEpochMillis = DesktopSystemClock::nowEpochMillis,
            snapshot = settingsSyncSnapshot,
            applyDocument = applySettingsSyncDocument,
        ),
        cacheSettings = NaviampCacheSettingsController(
            setSettings = setCacheSettings,
            saveSettings = saveCacheSettings,
        ),
        cacheMaintenance = NaviampCacheMaintenanceController(
            repository = storage,
            setStatus = { status ->
                publishCacheStatus(status, NaviampApplicationStatusLevel.Information)
            },
        ),
        downloadJobs = jobs,
        downloads = downloads,
    )
}

private const val DesktopSettingsSyncDeviceId = "desktop"
