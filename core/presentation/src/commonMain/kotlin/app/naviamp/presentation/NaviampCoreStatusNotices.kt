package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackQueueAddOutcome
import app.naviamp.ui.NaviampStatusMessage
import app.naviamp.ui.NaviampStatusNotice

/** Publishes [message] as the current bottom status notice, replacing any earlier status. */
internal fun NaviampCoreStateStore.publishStatus(message: NaviampStatusMessage) {
    update { it.withStatus(message) }
}

internal fun NaviampCoreState.withStatus(message: NaviampStatusMessage): NaviampCoreState =
    copy(overlays = overlays.withStatus(message))

/**
 * Each publication gets a fresh id so a repeated message is shown and timed again. The bar keeps its
 * node (and any focus on Close), so a screen reader may not announce an identical repeat a second time.
 */
internal fun NaviampCoreOverlayState.withStatus(message: NaviampStatusMessage): NaviampCoreOverlayState {
    val id = statusSequence + 1
    return copy(status = NaviampStatusNotice(id, message), statusSequence = id)
}

internal fun PlaybackQueueAddOutcome.statusMessage(): NaviampStatusMessage = when (this) {
    PlaybackQueueAddOutcome.AddedToQueue -> NaviampStatusMessage.AddedToQueue
    PlaybackQueueAddOutcome.AddedToPlayNext -> NaviampStatusMessage.AddedToPlayNext
    PlaybackQueueAddOutcome.AlreadyQueued -> NaviampStatusMessage.AlreadyInQueue
    PlaybackQueueAddOutcome.NothingFound -> NaviampStatusMessage.NoTracksAvailable
}

internal fun NaviampCoreMaintenanceOperation.statusMessage(): NaviampStatusMessage = when (this) {
    NaviampCoreMaintenanceOperation.ClearCache -> NaviampStatusMessage.CacheCleared
    NaviampCoreMaintenanceOperation.ClearLibrary -> NaviampStatusMessage.LibraryIndexCleared
    NaviampCoreMaintenanceOperation.RefreshLibrary -> NaviampStatusMessage.LibraryRefreshed
    NaviampCoreMaintenanceOperation.ResetDatabase -> NaviampStatusMessage.DatabaseReset
}

/** Clears the status only while it still shows [message], so a newer status is never removed. */
internal fun NaviampCoreOverlayState.withoutStatus(message: NaviampStatusMessage): NaviampCoreOverlayState =
    if (status?.message == message) copy(status = null) else this
