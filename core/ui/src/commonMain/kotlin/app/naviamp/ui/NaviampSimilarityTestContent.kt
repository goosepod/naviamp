package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.naviamp.domain.radio.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun NaviampSimilarityTestContent(
    colors: NaviampColors,
    test: NaviampSimilarityTestUi,
    onTest: (() -> Unit)?,
    radio: RadioBuildDiagnostics? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
        Text(stringResource(Res.string.similarity_test_help), color = colors.secondaryText)
        TextButton(onClick = { open = true; onTest?.invoke() }, enabled = onTest != null && test.state != SimilarityTestState.Running) {
            Text(stringResource(if (test.state == SimilarityTestState.Running) Res.string.tv_loading else Res.string.similarity_test))
        }
    }
    if (open) {
        NaviampPopupPresence()
        NaviampAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(Res.string.similarity_test)) },
            containerColor = colors.controlSurface,
            titleContentColor = colors.primaryText,
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimilarityReportContent(colors, test, radio)
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(Res.string.common_close)) }
            },
        )
    }
}

@Composable
private fun SimilarityReportContent(colors: NaviampColors, test: NaviampSimilarityTestUi, radio: RadioBuildDiagnostics?) {
    when (test.state) {
        SimilarityTestState.Running -> Text(stringResource(Res.string.tv_loading), color = colors.secondaryText)
        SimilarityTestState.NoSeed -> Text(stringResource(Res.string.similarity_no_seed), color = colors.secondaryText)
        SimilarityTestState.Failed -> Text(stringResource(Res.string.similarity_failed), color = colors.secondaryText)
        else -> Unit
    }
    test.report?.let { report ->
        Text(stringResource(Res.string.similarity_seed, report.seed.title), color = colors.primaryText)
        report.seed.artistName.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.secondaryText) }
        Text(stringResource(when (report.support) {
            SimilaritySupport.Advertised -> Res.string.similarity_support_ready
            SimilaritySupport.Missing -> Res.string.similarity_support_missing
            null -> Res.string.similarity_support_unknown
        }), color = colors.secondaryText)
        report.supportFailure?.let { SimilarityResultText(colors, it) }
        Text(stringResource(Res.string.settings_related_sonic_similarity), color = colors.primaryText)
        SimilarityResultText(colors, report.sonic)
        Text(stringResource(Res.string.similarity_regular), color = colors.primaryText)
        SimilarityResultText(colors, report.regular)
        if (report.support != SimilaritySupport.Advertised || report.sonic.kind != SimilarityResultKind.Matches) {
            Text(stringResource(if (report.sonic.kind == SimilarityResultKind.Empty)
                Res.string.similarity_empty_help else Res.string.similarity_setup), color = colors.secondaryText)
        }
    }
    radio?.let {
        Text(radioNoticeText(it), color = colors.secondaryText)
        it.fallback?.let { reason -> Text(stringResource(when (reason) {
            SonicRadioFallbackReason.Empty -> Res.string.similarity_empty
            SonicRadioFallbackReason.TimedOut -> Res.string.similarity_timeout
            SonicRadioFallbackReason.Failed -> Res.string.similarity_failed
        }), color = colors.secondaryText) }
    }
}

@Composable
private fun SimilarityResultText(colors: NaviampColors, result: SimilarityEndpointResult) {
    val text = when (result.kind) {
        SimilarityResultKind.Matches -> stringResource(Res.string.similarity_matches, result.count)
        SimilarityResultKind.Empty -> stringResource(Res.string.similarity_empty)
        SimilarityResultKind.Unsupported -> stringResource(Res.string.settings_related_requires_support)
        SimilarityResultKind.TimedOut -> stringResource(Res.string.similarity_timeout)
        SimilarityResultKind.Failed -> stringResource(Res.string.similarity_failed)
    }
    Text(text, color = colors.secondaryText)
    result.tracks.forEach { track ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(track.title, color = colors.primaryText)
            track.artist?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.secondaryText) }
        }
    }
    if (result.httpStatus != null || result.serverCode != null) {
        Text(stringResource(Res.string.similarity_protocol_codes,
            result.httpStatus?.toString() ?: "—", result.serverCode?.toString() ?: "—"), color = colors.secondaryText)
    }
}

@Composable
private fun radioNoticeText(result: RadioBuildDiagnostics): String = stringResource(when (result.outcome) {
    RadioBuildOutcome.Fallback -> Res.string.similarity_radio_fallback
    RadioBuildOutcome.Empty -> Res.string.similarity_radio_empty
    RadioBuildOutcome.Failed -> Res.string.similarity_radio_failed
})

@Composable
fun NaviampRadioNotice(colors: NaviampColors, result: RadioBuildDiagnostics, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(colors.background).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(radioNoticeText(result), color = colors.primaryText, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
    }
}
