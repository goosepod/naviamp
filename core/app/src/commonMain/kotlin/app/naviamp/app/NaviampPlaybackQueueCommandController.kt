package app.naviamp.app

import app.naviamp.domain.playback.PlaybackQueueMutationUpdate
import app.naviamp.domain.queue.RepeatMode

/** Applies shared user queue mutations to the playback engine owned by a platform host. */
fun interface NaviampPlaybackQueueMutationExecution {
    fun apply(update: PlaybackQueueMutationUpdate)
}

/**
 * Owns the common mutate-then-mirror sequence for bounded user queue commands.
 *
 * The queue coordinator remains the source of truth. A platform adapter mirrors changed queues
 * into its playback engine and handles prepared-next invalidation and native callbacks.
 */
class NaviampPlaybackQueueCommandController(
    private val queue: NaviampPlaybackQueueCoordinator,
    private val execution: NaviampPlaybackQueueMutationExecution,
) {
    fun moveToNext(index: Int): PlaybackQueueMutationUpdate =
        apply(queue.moveToNext(index))

    fun removeAt(index: Int): PlaybackQueueMutationUpdate =
        apply(queue.removeAt(index))

    fun clearUpcoming(): PlaybackQueueMutationUpdate =
        apply(queue.clearUpcoming())

    private fun apply(update: PlaybackQueueMutationUpdate): PlaybackQueueMutationUpdate =
        update.also { if (it.changed) execution.apply(it) }
}

fun interface NaviampPlaybackRepeatModeExecution {
    fun apply(mode: RepeatMode)
}

/** Cycles shared repeat policy before mirroring the selected mode into a platform queue adapter. */
class NaviampPlaybackRepeatCommandController(
    private val queue: NaviampPlaybackQueueCoordinator,
    private val execution: NaviampPlaybackRepeatModeExecution,
) {
    fun cycle(): RepeatMode = queue.cycleRepeatMode().also(execution::apply)
}
