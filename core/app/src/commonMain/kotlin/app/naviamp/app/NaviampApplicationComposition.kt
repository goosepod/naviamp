package app.naviamp.app

/**
 * Complete shared application graph after both host-independent controllers and adapter-dependent
 * services have been assembled.
 *
 * Thin hosts construct platform adapters, then hand this single composition to lifecycle and UI
 * integration. Tests can construct the same graph entirely from fakes.
 */
data class NaviampApplicationComposition<CacheStats, DownloadedFile, DownloadedTrack>(
    val runtime: NaviampApplicationRuntime,
    val services: NaviampApplicationServices<CacheStats, DownloadedFile, DownloadedTrack>,
) {
    val controllers: NaviampApplicationControllers get() = runtime.controllers
    val capabilities: NaviampCapabilityPresentation get() = runtime.capabilityPresentation
}
