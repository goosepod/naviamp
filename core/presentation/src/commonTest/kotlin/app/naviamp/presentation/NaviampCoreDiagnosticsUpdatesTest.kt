package app.naviamp.presentation

import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreDiagnosticsUpdatesTest {
    @Test
    fun initialSnapshotAndChangedFactsArePublishedAtTheExistingCadenceAndDismissalStopsSampling() = runTest {
        var fact = "initial"
        var reads = 0
        val values = mutableListOf<NaviampDiagnosticsUi>()
        val updates = naviampCoreDiagnosticsUpdates {
            reads++
            NaviampDiagnosticsUi(listOf(NaviampDiagnosticsSectionUi("Fixture", listOf("Fact" to fact))))
        }
        val collection = launch { updates.collect { values += it } }
        runCurrent()
        assertEquals(1, reads)
        assertEquals(1, values.size)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(4, reads)
        assertEquals(1, values.size)
        fact = "changed"
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, values.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("changed", values.last().sections.single().rows.single().second)
        assertEquals(2, values.size)
        collection.cancel()
        runCurrent()
        val readsAtDismissal = reads
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(readsAtDismissal, reads)
        // Reopening begins with current facts rather than a retained closed-window snapshot.
        fact = "reopened"
        val reopened = launch { updates.collect { values += it } }
        runCurrent()
        assertEquals("reopened", values.last().sections.single().rows.single().second)
        reopened.cancel()
    }
}
