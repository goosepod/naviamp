package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.domain.smartplaylist.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Describes the existing definition tree; it never evaluates or rewrites rules. */
@Composable
internal fun SmartPlaylistSummarySection(definition: SmartPlaylistDefinition, colors: NaviampColors) {
    Column(Modifier.testTag("smart-playlist-summary"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(Res.string.smart_playlist_summary), color = colors.primaryText)
        Text(matchSummary(definition.match, definition.rules.map { ruleSummary(it) }.joinToString("; ")),
            color = colors.secondaryText)
        if (definition.sort.isNotEmpty()) {
            Text(stringResource(Res.string.smart_playlist_summary_sort,
                definition.sort.mapIndexed { index, sort ->
                    val field = SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == sort.field }
                    val label = field?.let { smartPlaylistFieldLabel(it) } ?: sort.field
                    "${index + 1}. $label ${if (sort.field == SmartPlaylistFields.Random) "" else if (sort.descending) "↓" else "↑"}"
                }.joinToString(", ")), color = colors.secondaryText)
        }
        val limit = definition.limit
        val percent = definition.limitPercent
        Text(when {
            limit != null -> stringResource(Res.string.smart_playlist_summary_tracks, limit)
            percent != null -> stringResource(Res.string.smart_playlist_summary_percent, percent)
            else -> stringResource(Res.string.smart_playlist_summary_unlimited)
        }, color = colors.secondaryText)
        if (definition.ownerDependentFields().isNotEmpty()) {
            Text(stringResource(Res.string.smart_playlist_owner_notice), color = colors.secondaryText,
                modifier = Modifier.testTag("smart-playlist-owner-notice"))
        }
    }
}

@Composable
private fun matchSummary(match: SmartPlaylistMatch, rules: String): String = stringResource(
    if (match == SmartPlaylistMatch.All) Res.string.smart_playlist_summary_all else Res.string.smart_playlist_summary_any,
    rules,
)

@Composable
private fun ruleSummary(rule: SmartPlaylistRule): String = when (rule) {
    is SmartPlaylistGroup -> "(${matchSummary(rule.match, rule.rules.map { ruleSummary(it) }.joinToString("; "))})"
    is SmartPlaylistCondition -> {
        val field = SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == rule.field }
        stringResource(Res.string.smart_playlist_summary_condition,
            field?.let { smartPlaylistFieldLabel(it) } ?: rule.field,
            rule.operator.displayLabel(), rule.value.toJsonElement().toString())
    }
}
