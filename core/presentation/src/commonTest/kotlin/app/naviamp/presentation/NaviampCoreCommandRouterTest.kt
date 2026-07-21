package app.naviamp.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCommandRouterTest {
    @Test
    fun dispatchesImmediateCommandsExactlyOnce() = runTest {
        val controller = RecordingCommandController(
            immediate = NaviampCoreImmediateCommandResult.Handled(),
        )
        val router = NaviampCoreCommandRouter(this, listOf(controller))

        router.dispatch(NaviampCoreCommand.Search.Clear)

        assertEquals(listOf<NaviampCoreCommand>(NaviampCoreCommand.Search.Clear), controller.dispatched)
        assertEquals(emptyList<NaviampCoreCommand>(), controller.executed)
    }

    @Test
    fun executesDeferredCommandsInsideTheCoreScope() = runTest {
        val controller = RecordingCommandController(
            immediate = NaviampCoreImmediateCommandResult.Deferred,
            executeResult = NaviampCoreCommandResult.Completed,
        )
        val router = NaviampCoreCommandRouter(this, listOf(controller))

        router.dispatch(NaviampCoreCommand.Search.Submit)
        advanceUntilIdle()

        assertEquals(listOf<NaviampCoreCommand>(NaviampCoreCommand.Search.Submit), controller.executed)
    }

    @Test
    fun rejectsCommandsMissingACommonControllerInsteadOfSilentlyIgnoringThem() = runTest {
        val router = NaviampCoreCommandRouter(this, emptyList())

        assertFailsWith<IllegalStateException> {
            router.dispatch(NaviampCoreCommand.Home.Refresh)
        }
    }
}

private class RecordingCommandController(
    private val immediate: NaviampCoreImmediateCommandResult,
    private val executeResult: NaviampCoreCommandResult? = null,
) : NaviampCoreCommandController {
    val dispatched = mutableListOf<NaviampCoreCommand>()
    val executed = mutableListOf<NaviampCoreCommand>()

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        dispatched += command
        return immediate
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        executed += command
        return executeResult
    }
}
