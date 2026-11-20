package dev.sajidali.compose.tvos

import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import kotlin.test.*

class SelectedTvosDiagnosticsTest {
    private fun gap(coordinate: String) = EmptyDiscoveryRecord(coordinate, "fork:$coordinate", emptyList(), false)

    @Test fun originalPlatformAndLosingCandidatesDoNotFailStrictTvosBuilds() {
        val ios = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.9.6"
        val losing = "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0"
        val snapshot = DiagnosticsSnapshot(emptyList(), listOf(gap(ios), gap(losing)))
        val selected = setOf("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.1")
        val scoped = TvosDiagnosticsBookkeeping.forSelectedTvosModules(snapshot, selected)
        assertTrue(scoped.emptyDiscoveries.isEmpty())
        DiagnosticsSummary.report(scoped, true, true, false, Logging.getLogger(javaClass))
    }

    @Test fun genuinelySelectedMissingVariantStillFailsStrictMode() {
        val coordinate = "org.jetbrains.compose.ui:ui:99.0"
        val snapshot = DiagnosticsSnapshot(listOf(InjectionRecord("org.jetbrains.compose.ui:ui:98.0", "fork:98.0", 1)),
            listOf(gap(coordinate)))
        val scoped = TvosDiagnosticsBookkeeping.forSelectedTvosModules(snapshot, setOf(coordinate))
        assertFailsWith<GradleException> {
            DiagnosticsSummary.report(scoped, true, true, false, Logging.getLogger(javaClass), selectedGraphOnly = true)
        }
    }

    @Test fun forkCoordinatesAreNormalizedAndSelectionDoesNotLeakAcrossBuilds() {
        val coordinate = "org.jetbrains.compose.ui:ui:1.12.0"
        TvosDiagnosticsBookkeeping.resetDiagnostics()
        try {
            TvosDiagnosticsBookkeeping.recordEmptyDiscovery(gap(coordinate))
            TvosDiagnosticsBookkeeping.recordSelectedTvosModule("dev.sajidali.compose.ui:ui:1.12.0")
            assertEquals(listOf(coordinate), TvosDiagnosticsBookkeeping.selectedTvosSnapshot().emptyDiscoveries.map { it.sourceModule })
            TvosDiagnosticsBookkeeping.resetDiagnostics()
            assertTrue(TvosDiagnosticsBookkeeping.selectedTvosSnapshot().emptyDiscoveries.isEmpty())
        } finally { TvosDiagnosticsBookkeeping.resetDiagnostics() }
    }
}
