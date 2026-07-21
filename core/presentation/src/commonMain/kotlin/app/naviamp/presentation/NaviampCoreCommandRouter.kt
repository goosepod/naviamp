package app.naviamp.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface NaviampCoreImmediateCommandResult {
    data class Handled(
        val result: NaviampCoreCommandResult = NaviampCoreCommandResult.Completed,
    ) : NaviampCoreImmediateCommandResult

    data object Deferred : NaviampCoreImmediateCommandResult
    data object Unhandled : NaviampCoreImmediateCommandResult
}

/** A focused Core-owned feature controller participating in the complete command graph. */
interface NaviampCoreCommandController {
    fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult =
        NaviampCoreImmediateCommandResult.Unhandled

    suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? = null
}

/**
 * Exhaustive command router for the Core product graph.
 *
 * Unknown commands fail loudly. A visible action can therefore never degrade into the silent
 * no-op behavior that the Core-first migration is intended to remove.
 */
class NaviampCoreCommandRouter(
    private val scope: CoroutineScope,
    private val controllers: List<NaviampCoreCommandController>,
    private val onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Core command failed: $command", cause)
    },
) : NaviampCoreCommandHandler {
    override fun dispatch(command: NaviampCoreCommand) {
        controllers.forEach { controller ->
            when (controller.dispatch(command)) {
                is NaviampCoreImmediateCommandResult.Handled -> return
                NaviampCoreImmediateCommandResult.Deferred -> {
                    scope.launch {
                        runCatching { execute(command) }
                            .onFailure { cause -> onAsyncFailure(command, cause) }
                    }
                    return
                }
                NaviampCoreImmediateCommandResult.Unhandled -> Unit
            }
        }
        error("No Naviamp Core controller handles $command")
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
        controllers.forEach { controller ->
            when (val immediate = controller.dispatch(command)) {
                is NaviampCoreImmediateCommandResult.Handled -> return immediate.result
                NaviampCoreImmediateCommandResult.Deferred,
                NaviampCoreImmediateCommandResult.Unhandled,
                -> controller.execute(command)?.let { return it }
            }
        }
        error("No Naviamp Core controller executes $command")
    }
}
