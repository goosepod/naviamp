package app.naviamp.android

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidCoreUriPickerStateTest {
    @Test
    fun pickerLaunchesWithCurrentUriAndReturnsActivityResult() = runTest {
        val state = AndroidCoreUriPickerState()
        var launchedWith: String? = null
        state.launch = { launchedWith = it }

        val result = async { state.choose("content://settings", "Choose settings") }
        runCurrent()
        assertEquals("content://settings", launchedWith)

        state.complete("content://selected")

        assertEquals("content://selected", result.await())
    }

    @Test
    fun pickerRejectsOverlappingActivityRequests() = runTest {
        val state = AndroidCoreUriPickerState()
        val first = async { state.choose(null, "First") }
        runCurrent()

        assertFailsWith<IllegalStateException> {
            state.choose(null, "Second")
        }

        state.complete(null)
        assertEquals(null, first.await())
    }
}
