package app.naviamp.app

/**
 * Shared application services whose construction depends on live host state and adapters.
 *
 * Unlike [NaviampApplicationControllers], this assembly is created after a host has initialized
 * settings, storage, filesystem paths, presentation callbacks, and download repositories. Every
 * member is required so a host cannot silently construct a partial service graph with nullable
 * dependencies.
 */
data class NaviampApplicationServices<CacheStats, DownloadedFile, DownloadedTrack>(
    val settingsSync: NaviampSettingsSyncController,
    val cacheSettings: NaviampCacheSettingsController,
    val cacheMaintenance: NaviampCacheMaintenanceController<CacheStats>,
    val downloadJobs: NaviampDownloadJobController,
    val downloads: NaviampDownloadCoordinator<DownloadedFile, DownloadedTrack, CacheStats>,
)
