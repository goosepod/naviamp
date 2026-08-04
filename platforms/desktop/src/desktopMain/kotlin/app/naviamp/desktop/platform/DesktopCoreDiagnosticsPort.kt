package app.naviamp.desktop.platform

import app.naviamp.presentation.NaviampCoreCachedDiagnosticsPort

/** Supplies genuine JVM/OS facts and the host-backed storage snapshot to Core diagnostics. */
fun desktopCoreDiagnosticsPort(
    storageStats: () -> app.naviamp.domain.cache.StorageCacheStats,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
) = NaviampCoreCachedDiagnosticsPort(
        platformRows = {
            listOf(
                "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
                "Java" to System.getProperty("java.version").orEmpty(),
                "Working directory" to System.getProperty("user.dir").orEmpty(),
            )
        },
        storageStats = storageStats,
        nowEpochMillis = nowEpochMillis,
    )
