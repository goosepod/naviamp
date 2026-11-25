package app.naviamp.presentation

import app.naviamp.domain.radio.RadioBuildOutcome
import app.naviamp.ui.NaviampNoticeBaseTimeoutMillis
import app.naviamp.ui.NaviampSomethingNewNotice
import app.naviamp.ui.NaviampTimedNotice
import app.naviamp.ui.NaviampTimedNoticeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Longest window an accessibility-adjusted presentation may request, as for the queue Undo notice. */
const val NaviampNoticeMaxTimeoutMillis = 300_000L

/**
 * Gives transient bottom notices a lifetime: the base window, widened to the presentation's
 * accessibility-recommended timeout, paused while focused. Offline-only notices also end as soon
 * as the connection is no longer offline. Close still dismisses through the notice's own command.
 */
class NaviampCoreNoticeLifetimeController(
    private val scope: CoroutineScope,
    private val stateStore: NaviampCoreStateStore,
) : NaviampCoreCommandController {
    private val timers = mutableMapOf<NaviampTimedNotice, Job>()
    /** The notice value each lifetime belongs to; it survives a focus pause. */
    private val owners = mutableMapOf<NaviampTimedNotice, Any>()

    private data class Observed(val somethingNew: Any?, val radio: Any?, val offline: Boolean)

    fun start() {
        scope.launch {
            stateStore.state.map { state ->
                Observed(timedValue(state, NaviampTimedNotice.SomethingNew), timedValue(state, NaviampTimedNotice.Radio),
                    state.shell.connectionSettings.connection.offline)
            }.distinctUntilChanged().collect { observed ->
                if (!observed.offline) clearOfflineNotices()
                NaviampTimedNotice.entries.forEach { notice ->
                    val value = if (notice == NaviampTimedNotice.SomethingNew) observed.somethingNew else observed.radio
                    if (value == null) cancel(notice) else if (owners[notice] != value) schedule(notice, NaviampNoticeBaseTimeoutMillis)
                }
            }
        }
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val request = (command as? NaviampCoreCommand.Home.NoticeLifetime)?.request
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        if (timedValue(stateStore.state.value, request.notice) != null) {
            when (request.action) {
                NaviampTimedNoticeAction.Presented ->
                    schedule(request.notice, request.timeoutMillis ?: NaviampNoticeBaseTimeoutMillis)
                NaviampTimedNoticeAction.FocusGained -> timers.remove(request.notice)?.cancel()
                NaviampTimedNoticeAction.FocusLost -> schedule(request.notice, NaviampNoticeBaseTimeoutMillis)
            }
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    private fun schedule(notice: NaviampTimedNotice, timeoutMillis: Long) {
        val value = timedValue(stateStore.state.value, notice) ?: return
        timers.remove(notice)?.cancel()
        owners[notice] = value
        val window = timeoutMillis.coerceIn(NaviampNoticeBaseTimeoutMillis, NaviampNoticeMaxTimeoutMillis)
        timers[notice] = scope.launch {
            delay(window)
            if (timedValue(stateStore.state.value, notice) == value) clear(notice)
        }
    }

    private fun cancel(notice: NaviampTimedNotice) {
        timers.remove(notice)?.cancel()
        owners.remove(notice)
    }

    private fun clearOfflineNotices() {
        val overlays = stateStore.state.value.overlays
        if (overlays.somethingNewNotice in OfflineSomethingNewNotices) clear(NaviampTimedNotice.SomethingNew)
        if (overlays.radioNotice?.outcome == RadioBuildOutcome.Offline) clear(NaviampTimedNotice.Radio)
    }

    private fun clear(notice: NaviampTimedNotice) {
        stateStore.update { state ->
            state.copy(overlays = when (notice) {
                NaviampTimedNotice.SomethingNew -> state.overlays.copy(somethingNewNotice = null)
                NaviampTimedNotice.Radio -> state.overlays.copy(radioNotice = null)
            })
        }
    }

    /** Only the offline Radio outcome is timed; other Radio diagnostics keep their existing behavior. */
    private fun timedValue(state: NaviampCoreState, notice: NaviampTimedNotice): Any? = when (notice) {
        NaviampTimedNotice.SomethingNew -> state.overlays.somethingNewNotice
        NaviampTimedNotice.Radio -> state.overlays.radioNotice?.takeIf { it.outcome == RadioBuildOutcome.Offline }
    }

    private companion object {
        val OfflineSomethingNewNotices = setOf(
            NaviampSomethingNewNotice.NothingUnplayedOffline,
            NaviampSomethingNewNotice.NothingDownloaded,
            NaviampSomethingNewNotice.PlayingFromDownloads,
        )
    }
}
