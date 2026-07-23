package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared diagnostics surface. Hosts contribute facts through Core state, never their own window. */
@Composable
fun NaviampStatsForNerdsDialog(
    diagnostics: NaviampDiagnosticsUi,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
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
                            "$label: $value",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
