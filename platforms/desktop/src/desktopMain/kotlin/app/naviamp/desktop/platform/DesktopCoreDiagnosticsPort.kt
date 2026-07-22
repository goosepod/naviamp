package app.naviamp.desktop.platform

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.presentation.NaviampCoreDiagnosticsPort
import app.naviamp.presentation.NaviampCoreDiagnosticsSnapshot

/** Supplies genuine JVM/OS facts and the host-backed storage snapshot to Core diagnostics. */
class DesktopCoreDiagnosticsPort(
    private val storageStats: () -> StorageCacheStats,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : NaviampCoreDiagnosticsPort {
    private var cachedAt = Long.MIN_VALUE
    private var cachedStorage: StorageCacheStats? = null

    override fun snapshot(): NaviampCoreDiagnosticsSnapshot {
        val now = nowEpochMillis()
        if (cachedStorage == null || now - cachedAt >= StorageRefreshIntervalMillis) {
            cachedStorage = runCatching(storageStats).getOrNull()
            cachedAt = now
        }
        return NaviampCoreDiagnosticsSnapshot(
            platformRows = listOf(
                "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
                "Java" to System.getProperty("java.version").orEmpty(),
                "Working directory" to System.getProperty("user.dir").orEmpty(),
            ),
            storage = cachedStorage,
        )
    }
}

private const val StorageRefreshIntervalMillis = 2_000L
