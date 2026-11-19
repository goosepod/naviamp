package app.naviamp.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
    private val executing = mutableSetOf<Job>()
    private val dispatched = mutableSetOf<Job>()
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        executing.toList().forEach { it.cancel() }
        dispatched.toList().forEach { it.cancel() }
    }
    override fun dispatch(command: NaviampCoreCommand) {
        if (closed) return
        val owner = requireSingleOwner(command)
        when (owner.immediateResult) {
            is NaviampCoreImmediateCommandResult.Handled -> Unit
            NaviampCoreImmediateCommandResult.Deferred -> {
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                    runCatching { executeDeferred(owner.controller, command) }
                        .onFailure { cause ->
                            if (cause is CancellationException) throw cause
                            onAsyncFailure(command, cause)
                        }
                    } finally { currentCoroutineContext()[Job]?.let { dispatched -= it } }
                }
                dispatched += job
                job.start()
            }
            NaviampCoreImmediateCommandResult.Unhandled -> error("Unreachable command ownership state")
        }
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult = coroutineScope {
        if (closed) throw CancellationException("Core command router is closed")
        val job = currentCoroutineContext()[Job]!!
        executing += job
        try {
            val owner = requireSingleOwner(command)
            when (val immediate = owner.immediateResult) {
                is NaviampCoreImmediateCommandResult.Handled -> immediate.result
                NaviampCoreImmediateCommandResult.Deferred -> executeDeferred(owner.controller, command)
                NaviampCoreImmediateCommandResult.Unhandled -> error("Unreachable command ownership state")
            }
        } finally { executing -= job }
    }

    private fun requireSingleOwner(command: NaviampCoreCommand): CommandOwner {
        val owners = controllers.mapNotNull { controller ->
            val result = controller.dispatch(command)
            if (result == NaviampCoreImmediateCommandResult.Unhandled) null
            else CommandOwner(controller, result)
        }
        check(owners.size == 1) {
            when {
                owners.isEmpty() -> "No Naviamp Core controller handles $command"
                else -> "Multiple Naviamp Core controllers handle $command: ${owners.size}"
            }
        }
        return owners.single()
    }

    private suspend fun executeDeferred(
        controller: NaviampCoreCommandController,
        command: NaviampCoreCommand,
    ): NaviampCoreCommandResult = controller.execute(command)
        ?: error("The Naviamp Core controller claiming $command did not execute it")

    private data class CommandOwner(
        val controller: NaviampCoreCommandController,
        val immediateResult: NaviampCoreImmediateCommandResult,
    )
}
