package app.naviamp.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampLiveSemanticsTest {
    @Test fun hiddenChangesDoNotPublishAndRestoreCatchesUpWithoutAUiFrame() = runTest {
        val visible = MutableStateFlow(true)
        val value = mutableIntStateOf(1)
        val published = mutableListOf<Int>()
        val observation = backgroundScope.launch {
            naviampObserveVisibleSemantics(visible, { value.intValue }, published::add)
        }
        runCurrent()
        assertEquals(listOf(1), published)
        visible.value = false; runCurrent()
        value.intValue = 2; Snapshot.sendApplyNotifications(); runCurrent()
        value.intValue = 3; Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(listOf(1), published)
        visible.value = true; runCurrent()
        assertEquals(listOf(1, 3), published)
        value.intValue = 4; Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(listOf(1, 3, 4), published)
        observation.cancel(); runCurrent()
        value.intValue = 5; Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(listOf(1, 3, 4), published)
    }
}
