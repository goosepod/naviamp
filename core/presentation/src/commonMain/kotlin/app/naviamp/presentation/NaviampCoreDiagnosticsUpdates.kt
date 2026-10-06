package app.naviamp.presentation

import app.naviamp.ui.NaviampDiagnosticsUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Each visible diagnostics presentation collects its own updates and cancels them on dismissal. */
internal fun naviampCoreDiagnosticsUpdates(snapshot: () -> NaviampDiagnosticsUi): Flow<NaviampDiagnosticsUi> =
    flow {
        while (true) {
            emit(snapshot())
            delay(1_000)
        }
    }.distinctUntilChanged()
