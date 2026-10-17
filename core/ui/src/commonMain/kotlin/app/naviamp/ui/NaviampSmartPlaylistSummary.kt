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
internal fun SmartPlaylistSummarySection(
    definition: SmartPlaylistDefinition,
    colors: NaviampColors,
    references: List<SmartPlaylistReference> = emptyList(),
) {
    Column(Modifier.testTag("smart-playlist-summary"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(Res.string.smart_playlist_summary), color = colors.primaryText)
        Text(matchSummary(definition.match, definition.rules.map { ruleSummary(it, references) }.joinToString("; ")),
            color = colors.secondaryText)
        if (definition.sort.isNotEmpty()) {
            Text(stringResource(Res.string.smart_playlist_summary_sort,
                definition.sort.mapIndexed { index, sort ->
                    val field = SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == sort.field }
                    val label = field?.let { smartPlaylistFieldLabel(it) } ?: sort.field
                    val direction = if (sort.field == SmartPlaylistFields.Random) label else stringResource(
                        if (sort.descending) Res.string.smart_playlist_summary_descending else Res.string.smart_playlist_summary_ascending,
                        label,
                    )
                    "${index + 1}. $direction"
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
private fun ruleSummary(rule: SmartPlaylistRule, references: List<SmartPlaylistReference>): String = when (rule) {
    is SmartPlaylistGroup -> "(${matchSummary(rule.match, rule.rules.map { ruleSummary(it, references) }.joinToString("; "))})"
    is SmartPlaylistCondition -> {
        val field = SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == rule.field }
        val label = field?.let { smartPlaylistFieldLabel(it) } ?: rule.field
        val operator = rule.summaryOperator()
        val operation = summaryOperatorLabel(operator)
        if (operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent) {
            stringResource(Res.string.smart_playlist_summary_predicate, label, operation)
        } else {
            stringResource(Res.string.smart_playlist_summary_condition,
                label, operation, summaryValue(rule.value, rule.field, operator, references))
        }
    }
}

@Composable
private fun summaryValue(value: SmartPlaylistValue, field: String, operator: SmartPlaylistOperator,
    references: List<SmartPlaylistReference>): String = when (value) {
    is SmartPlaylistValue.Text -> when {
        operator == SmartPlaylistOperator.InPlaylist || operator == SmartPlaylistOperator.NotInPlaylist -> {
            val reference = smartPlaylistSummaryReference(value.value, references)
            if (reference == null) stringResource(Res.string.smart_playlist_summary_unavailable_playlist, value.value)
            else {
                val context = listOfNotNull(reference.owner,
                    if (reference.isSmart) stringResource(Res.string.playlists_smart) else null,
                    reference.distinguishingId).joinToString(" · ")
                if (context.isEmpty()) reference.name
                else stringResource(Res.string.smart_playlist_summary_reference_context, reference.name, context)
            }
        }
        field == SmartPlaylistFields.ExplicitStatus -> stringResource(when (value.value) {
            "c" -> Res.string.smart_playlist_summary_clean
            "e" -> Res.string.smart_playlist_summary_explicit
            else -> Res.string.smart_playlist_summary_unspecified
        })
        value.value.isEmpty() -> stringResource(Res.string.smart_playlist_summary_empty)
        else -> value.value
    }
    is SmartPlaylistValue.Number -> if (operator == SmartPlaylistOperator.InTheLast || operator == SmartPlaylistOperator.NotInTheLast) {
        stringResource(if (value.value == 1L) Res.string.smart_playlist_summary_day else Res.string.smart_playlist_summary_days, value.value)
    } else value.value.toString()
    is SmartPlaylistValue.Decimal -> value.value.toString()
    is SmartPlaylistValue.Flag -> stringResource(if (value.value) Res.string.smart_playlist_summary_yes else Res.string.smart_playlist_summary_no)
    is SmartPlaylistValue.Range -> stringResource(Res.string.smart_playlist_summary_range,
        summaryValue(value.start, field, operator, references), summaryValue(value.end, field, operator, references))
}

@Composable
private fun summaryOperatorLabel(operator: SmartPlaylistOperator): String = stringResource(when (operator) {
    SmartPlaylistOperator.Is -> Res.string.smart_playlist_summary_op_is
    SmartPlaylistOperator.IsNot -> Res.string.smart_playlist_summary_op_is_not
    SmartPlaylistOperator.GreaterThan -> Res.string.smart_playlist_summary_op_greater
    SmartPlaylistOperator.LessThan -> Res.string.smart_playlist_summary_op_less
    SmartPlaylistOperator.Contains -> Res.string.smart_playlist_summary_op_contains
    SmartPlaylistOperator.NotContains -> Res.string.smart_playlist_summary_op_not_contains
    SmartPlaylistOperator.StartsWith -> Res.string.smart_playlist_summary_op_starts
    SmartPlaylistOperator.EndsWith -> Res.string.smart_playlist_summary_op_ends
    SmartPlaylistOperator.InTheRange -> Res.string.smart_playlist_summary_op_range
    SmartPlaylistOperator.Before -> Res.string.smart_playlist_summary_op_before
    SmartPlaylistOperator.After -> Res.string.smart_playlist_summary_op_after
    SmartPlaylistOperator.InTheLast -> Res.string.smart_playlist_summary_op_last
    SmartPlaylistOperator.NotInTheLast -> Res.string.smart_playlist_summary_op_not_last
    SmartPlaylistOperator.InPlaylist -> Res.string.smart_playlist_summary_op_playlist
    SmartPlaylistOperator.NotInPlaylist -> Res.string.smart_playlist_summary_op_not_playlist
    SmartPlaylistOperator.IsMissing -> Res.string.smart_playlist_summary_op_missing
    SmartPlaylistOperator.IsPresent -> Res.string.smart_playlist_summary_op_present
})
