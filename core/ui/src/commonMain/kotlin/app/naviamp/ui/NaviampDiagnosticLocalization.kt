package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.diagnostics_external_scrobbling
import app.naviamp.ui.generated.resources.diagnostics_external_scrobbling_server_managed
import app.naviamp.ui.generated.resources.diagnostics_failure_legacy_presence
import app.naviamp.ui.generated.resources.diagnostics_failure_listen_submission
import app.naviamp.ui.generated.resources.diagnostics_failure_timeline
import app.naviamp.ui.generated.resources.diagnostics_last_listen_reporting_failure
import app.naviamp.ui.generated.resources.diagnostics_listen_reporting_mode
import app.naviamp.ui.generated.resources.diagnostics_mode_idle
import app.naviamp.ui.generated.resources.diagnostics_mode_legacy_fallback
import app.naviamp.ui.generated.resources.diagnostics_mode_legacy_presence
import app.naviamp.ui.generated.resources.diagnostics_mode_timeline_presence
import app.naviamp.ui.generated.resources.diagnostics_pending_listens
import org.jetbrains.compose.resources.stringResource

/** Localizes shared diagnostic vocabulary while leaving provider/native error details intact. */
@Composable
internal fun localizedDiagnosticText(text: String): String = when {
    text == "Listen reporting mode" -> stringResource(Res.string.diagnostics_listen_reporting_mode)
    text == "Idle" -> stringResource(Res.string.diagnostics_mode_idle)
    text == "Timeline presence" -> stringResource(Res.string.diagnostics_mode_timeline_presence)
    text == "Legacy presence" -> stringResource(Res.string.diagnostics_mode_legacy_presence)
    text == "Legacy fallback" -> stringResource(Res.string.diagnostics_mode_legacy_fallback)
    text == "Last listen reporting failure" ->
        stringResource(Res.string.diagnostics_last_listen_reporting_failure)
    text == "External scrobbling" -> stringResource(Res.string.diagnostics_external_scrobbling)
    text == "Server-managed; API acceptance does not confirm external delivery" ->
        stringResource(Res.string.diagnostics_external_scrobbling_server_managed)
    text == "Pending listens" -> stringResource(Res.string.diagnostics_pending_listens)
    text.startsWith("Legacy presence: ") ->
        stringResource(Res.string.diagnostics_failure_legacy_presence) + text.removePrefix("Legacy presence")
    text.startsWith("Listen submission: ") ->
        stringResource(Res.string.diagnostics_failure_listen_submission) + text.removePrefix("Listen submission")
    text.startsWith("Timeline ") && ": " in text ->
        stringResource(Res.string.diagnostics_failure_timeline) + text.removePrefix("Timeline")
    else -> text
}
