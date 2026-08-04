package app.naviamp.android

import android.os.Build
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.presentation.NaviampCoreDiagnosticsPort
import app.naviamp.presentation.NaviampCoreDiagnosticsSnapshot

/** Supplies Android OS facts and the shared storage snapshot to Core diagnostics. */
class AndroidCoreDiagnosticsPort(
    private val storageStats: () -> StorageCacheStats,
) : NaviampCoreDiagnosticsPort {
    override fun snapshot() = NaviampCoreDiagnosticsSnapshot(
        platformRows = listOf(
            "OS" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Device" to listOf(Build.MANUFACTURER, Build.MODEL).filter(String::isNotBlank).joinToString(" "),
            "Architecture" to Build.SUPPORTED_ABIS.joinToString().ifBlank { "Unknown" },
        ),
        storage = runCatching(storageStats).getOrNull(),
    )
}
