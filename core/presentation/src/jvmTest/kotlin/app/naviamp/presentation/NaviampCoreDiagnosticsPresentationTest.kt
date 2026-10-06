package app.naviamp.presentation

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampCoreDiagnosticsPresentationTest {
    @Test
    fun refreshUpdatesDiagnosticsWithoutRecomposingTheApplicationAndStopsOnDismissal() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var snapshotReads = 0
        var fact = "initial"
        val core = NaviampCore.create(scope, fakeCoreServices().copy(
            diagnostics = NaviampCoreDiagnosticsPort {
                snapshotReads++
                NaviampCoreDiagnosticsSnapshot(platformRows = listOf("Fixture" to fact))
            },
        ))
        val visible = mutableStateOf(true)
        var applicationCompositions = 0
        var diagnosticsCompositions = 0
        var displayedFact = ""
        try {
            setContent {
                SideEffect { applicationCompositions++ }
                if (visible.value) NaviampCoreDiagnosticsPresentation(core) { diagnostics, _ ->
                    SideEffect {
                        diagnosticsCompositions++
                        displayedFact = diagnostics.sections.flatMap { it.rows }.toMap()["Fixture"].orEmpty()
                    }
                }
            }
            waitForIdle()
            val initialApplicationCompositions = applicationCompositions
            val initialDiagnosticsCompositions = diagnosticsCompositions
            val initialReads = snapshotReads
            // Equal snapshots retain the existing diagnostics composition as well as its sibling.
            waitUntil(timeoutMillis = 5_000) { snapshotReads > initialReads }
            waitForIdle()
            assertEquals(initialDiagnosticsCompositions, diagnosticsCompositions)
            assertEquals(initialApplicationCompositions, applicationCompositions)

            runOnIdle { fact = "changed" }
            waitUntil(timeoutMillis = 5_000) { displayedFact == "changed" }
            assertTrue(diagnosticsCompositions > initialDiagnosticsCompositions)
            assertEquals(initialApplicationCompositions, applicationCompositions)

            runOnIdle { visible.value = false }
            waitForIdle()
            val readsWhenDismissed = snapshotReads
            Thread.sleep(1_100)
            waitForIdle()
            assertEquals(readsWhenDismissed, snapshotReads)
        } finally {
            core.close()
            scope.cancel()
        }
    }
}
