package app.naviamp.domain.app

const val StorageStatsRefreshIntervalMillis = 5_000L

fun shouldRefreshStorageStats(
    route: NaviampRoute,
    diagnosticsVisible: Boolean = false,
): Boolean =
    diagnosticsVisible ||
        route == NaviampRoute.Settings ||
        route == NaviampRoute.Downloads
