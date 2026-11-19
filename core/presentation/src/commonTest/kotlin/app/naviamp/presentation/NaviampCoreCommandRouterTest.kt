package app.naviamp.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCommandRouterTest {
    @Test fun closeCancelsAdmittedWorkWithoutCancellingCallerOrReportingNormalCancellation() = runTest {
        var entered = 0; var exited = 0; var admissions = 0
        val failures = mutableListOf<Throwable>()
        val controller = object : NaviampCoreCommandController {
            override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
                admissions++; return NaviampCoreImmediateCommandResult.Deferred
            }
            override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
                entered++
                try { awaitCancellation() } finally { exited++ }
            }
        }
        val router = NaviampCoreCommandRouter(backgroundScope, listOf(controller)) { _, cause -> failures += cause }
        router.dispatch(NaviampCoreCommand.Home.Refresh)
        val direct = launch { router.execute(NaviampCoreCommand.Home.Refresh) }
        runCurrent(); assertEquals(2, entered)
        router.close(); runCurrent(); direct.join()
        assertEquals(2, exited); assertTrue(failures.isEmpty()); assertTrue(backgroundScope.isActive)
        router.dispatch(NaviampCoreCommand.Home.Refresh)
        assertFailsWith<CancellationException> { router.execute(NaviampCoreCommand.Home.Refresh) }
        assertEquals(2, admissions)
    }

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

        assertEquals(listOf<NaviampCoreCommand>(NaviampCoreCommand.Search.Submit), controller.dispatched)
        assertEquals(listOf<NaviampCoreCommand>(NaviampCoreCommand.Search.Submit), controller.executed)
    }

    @Test
    fun rejectsCommandsMissingACommonControllerInsteadOfSilentlyIgnoringThem() = runTest {
        val router = NaviampCoreCommandRouter(this, emptyList())

        assertFailsWith<IllegalStateException> {
            router.dispatch(NaviampCoreCommand.Home.Refresh)
        }
    }

    @Test
    fun rejectsCommandsClaimedByMoreThanOneCommonController() = runTest {
        val router = NaviampCoreCommandRouter(
            this,
            listOf(
                RecordingCommandController(NaviampCoreImmediateCommandResult.Handled()),
                RecordingCommandController(NaviampCoreImmediateCommandResult.Deferred),
            ),
        )

        assertFailsWith<IllegalStateException> {
            router.dispatch(NaviampCoreCommand.Home.Refresh)
        }
    }

    @Test
    fun rejectsDeferredOwnerThatCannotExecuteItsClaimedCommand() = runTest {
        val router = NaviampCoreCommandRouter(
            this,
            listOf(RecordingCommandController(NaviampCoreImmediateCommandResult.Deferred)),
        )

        assertFailsWith<IllegalStateException> {
            router.execute(NaviampCoreCommand.Home.Refresh)
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
