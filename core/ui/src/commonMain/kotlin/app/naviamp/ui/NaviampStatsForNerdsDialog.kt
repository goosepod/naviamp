package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.common_close
import app.naviamp.ui.generated.resources.tv_stats_for_nerds
import org.jetbrains.compose.resources.stringResource

/** Shared diagnostics surface. Hosts contribute facts through Core state, never their own window. */
@Composable
fun NaviampStatsForNerdsDialog(
    diagnostics: NaviampDiagnosticsUi,
    onDismissRequest: () -> Unit,
) {
    NaviampPopupPresence()
    NaviampAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Stats for Nerds") },
        text = {
            NaviampStatsForNerdsContent(diagnostics)
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        },
    )
}

/** Read changing diagnostics only inside the independently mounted presentation surface. */
@Composable
fun NaviampStatsForNerdsWindowContent(
    diagnostics: State<NaviampDiagnosticsUi>,
    onClose: () -> Unit,
    darkTheme: Boolean,
) = NaviampStatsForNerdsWindowContent(diagnostics.value, onClose, darkTheme)

/** Shared product content for hosts that supply an independent native diagnostics window. */
@Composable
fun NaviampStatsForNerdsWindowContent(
    diagnostics: NaviampDiagnosticsUi,
    onClose: () -> Unit,
    darkTheme: Boolean,
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.tv_stats_for_nerds), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onClose) { Text(stringResource(Res.string.common_close)) }
                }
                NaviampStatsForNerdsContent(
                    diagnostics = diagnostics,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun NaviampStatsForNerdsContent(
    diagnostics: NaviampDiagnosticsUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (diagnostics.sections.isEmpty()) {
            Text("No diagnostics are available yet.")
        } else {
            diagnostics.sections.forEach { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        section.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    section.rows.forEach { (label, value) ->
                        Text(
                            "${localizedDiagnosticText(label)}: ${localizedDiagnosticText(value)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
