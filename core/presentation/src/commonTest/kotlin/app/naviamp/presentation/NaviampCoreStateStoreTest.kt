package app.naviamp.presentation

import app.naviamp.ui.NaviampSearchScreenUi
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCoreStateStoreTest {
    @Test
    fun ownsAndUpdatesTheCompleteSharedShellState() {
        val store = NaviampCoreStateStore()

        store.updateShell { shell ->
            shell.copy(search = NaviampSearchScreenUi(query = "shared core"))
        }

        assertEquals("shared core", store.state.value.shell.search.query)
    }

    @Test
    fun replacesTheWholeProductSnapshotForRestoration() {
        val store = NaviampCoreStateStore()
        val restored = NaviampCoreState(
            shell = store.state.value.shell.copy(
                search = NaviampSearchScreenUi(query = "restored"),
            ),
        )

        store.replace(restored)

        assertEquals(restored, store.state.value)
    }
}
