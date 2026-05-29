package app.naviamp.domain.app

const val StorageStatsRefreshIntervalMillis = 5_000L

fun shouldRefreshStorageStats(
    route: NaviampRoute,
    diagnosticsVisible: Boolean = false,
): Boolean =
    diagnosticsVisible ||
        route == NaviampRoute.Settings ||
        route == NaviampRoute.Downloads

fun cacheDataClearedStatus(detailed: Boolean = false): String =
    if (detailed) {
        "Image, provider response, audio, and waveform cache cleared."
    } else {
        "Cache cleared."
    }

fun libraryIndexClearedStatus(detailed: Boolean = false): String =
    if (detailed) {
        "Local artist, album, and track index cleared."
    } else {
        "Library index cleared."
    }

fun databaseResetStatus(savedServersRemoved: Boolean = false): String =
    if (savedServersRemoved) {
        "Database reset. Saved servers were removed."
    } else {
        "Database reset."
    }
