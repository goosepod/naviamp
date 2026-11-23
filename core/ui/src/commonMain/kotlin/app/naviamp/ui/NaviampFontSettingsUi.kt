package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.settings.InterfaceFontFamily
import app.naviamp.domain.settings.InterfaceFontSelection
import app.naviamp.domain.settings.InterfaceFontSize
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.withBundledFontFamily
import app.naviamp.domain.settings.withInstalledFontFamily
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** One page for the typeface and both text sizes, with a shared preview of the result. */
@Composable
internal fun FontSettings(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
) = NaviampFontPageScope(interfaceSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val selection = rememberNaviampFontSelection(interfaceSettings)
        selection.unavailableInstalledFamily?.let { name ->
            Text(
                stringResource(Res.string.settings_font_unavailable, name),
                color = colors.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = SettingsRowHorizontalPadding, vertical = 6.dp)
                    .testTag("font-unavailable"),
            )
        }
        val installedActive = selection.installedFamily != null
        InterfaceFontFamily.entries.forEach { family ->
            FontFamilyRow(
                colors = colors,
                title = family.title(),
                subtitle = family.subtitle(),
                selected = !installedActive && selection.family == family,
            ) {
                onInterfaceSettingsChanged(interfaceSettings.withBundledFontFamily(family))
            }
        }
        FontSizePicker(
            colors = colors,
            title = stringResource(Res.string.settings_font_size_general_title),
            value = interfaceSettings.generalFontSize,
            tag = "general-font-size",
            onValueChanged = { onInterfaceSettingsChanged(interfaceSettings.copy(generalFontSize = it).normalized()) },
        )
        FontSizePicker(
            colors = colors,
            title = stringResource(Res.string.settings_font_size_now_playing_title),
            value = interfaceSettings.nowPlayingFontSize,
            tag = "now-playing-font-size",
            onValueChanged = { onInterfaceSettingsChanged(interfaceSettings.copy(nowPlayingFontSize = it).normalized()) },
        )
        // Below every control, so its height following the sizes never moves what is being chosen.
        NaviampFontPreview(
            colors,
            selection,
            interfaceSettings,
            Modifier.padding(horizontal = SettingsRowHorizontalPadding, vertical = 6.dp),
        )
        // Last, so its search and changing length never move the controls above.
        if (naviampInstalledFonts() != null) {
            InstalledFontFamilyPicker(colors, selection, interfaceSettings, onInterfaceSettingsChanged)
        }
    }
}

/**
 * The page shows its own controls in Nunito Sans at the standard size, keeping them readable and
 * in place whatever is chosen; only the preview reflects the choices.
 */
@Composable
internal fun NaviampFontPageScope(interfaceSettings: InterfaceSettings, content: @Composable () -> Unit) {
    NaviampDefaultTypographyScope {
        NaviampFontSizeScope(InterfaceFontSize.Standard, relativeTo = interfaceSettings.generalFontSize, content = content)
    }
}

/**
 * Sized for the tallest bundled font, so switching typefaces keeps its height and what is below in
 * place; a taller installed font is clipped rather than growing. Below the controls it follows the
 * chosen sizes. [reserveLargestSizes] is for a preview above controls, which must not grow either.
 */
@Composable
internal fun NaviampFontPreview(
    colors: NaviampColors,
    selection: InterfaceFontSelection,
    interfaceSettings: InterfaceSettings,
    modifier: Modifier = Modifier,
    reserveLargestSizes: Boolean = false,
) {
    val largest = InterfaceFontSize(InterfaceFontSize.MaxPercent)
    val reservedGeneral = if (reserveLargestSizes) largest else interfaceSettings.generalFontSize
    val reservedNowPlaying = if (reserveLargestSizes) largest else interfaceSettings.nowPlayingFontSize
    Layout(
        contents = listOf(
            {
                Box(Modifier.clearAndSetSemantics {}) {
                    InterfaceFontFamily.entries.forEach { family ->
                        MaterialTheme(typography = rememberNaviampTypography(InterfaceFontSelection(family))) {
                            NaviampFontPreviewCard(colors, reservedGeneral, reservedNowPlaying)
                        }
                    }
                }
            },
            {
                MaterialTheme(typography = rememberNaviampTypography(selection)) {
                    NaviampFontPreviewCard(
                        colors,
                        interfaceSettings.generalFontSize,
                        interfaceSettings.nowPlayingFontSize,
                        Modifier.testTag("font-preview"),
                    )
                }
            },
        ),
        modifier = modifier.fillMaxWidth().clipToBounds(),
    ) { (reserved, shown), constraints ->
        val reservation = reserved.map { it.measure(constraints) }
        val height = reservation.maxOf { it.height }
        val width = constraints.maxWidth
        val preview = shown.map {
            it.measure(Constraints(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height))
        }
        layout(width, height) { preview.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun NaviampFontPreviewCard(
    colors: NaviampColors,
    general: InterfaceFontSize,
    nowPlaying: InterfaceFontSize,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .background(colors.primaryText.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.settings_font_preview_title), color = colors.mutedText, fontSize = 11.sp, maxLines = 1)
        NaviampFontSizeScope(general) {
            Text(
                stringResource(Res.string.settings_font_preview_sample),
                color = colors.primaryText,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("font-preview-sample"),
            )
        }
        NaviampFontSizeScope(nowPlaying) {
            Column {
                Text(
                    stringResource(Res.string.settings_font_preview_sample),
                    color = colors.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("1:07 / 4:38", color = colors.secondaryText, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Reserves the "Selected" label on every row, so choosing a typeface never rewraps the subtitles. */
@Composable
private fun FontFamilyRow(colors: NaviampColors, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = SettingsRowHorizontalPadding).fillMaxWidth()
            .background(
                if (selected) colors.accent.copy(alpha = 0.16f) else colors.background.copy(alpha = 0f),
                RoundedCornerShape(6.dp),
            )
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.primaryText, fontSize = 14.sp)
            Text(subtitle, color = colors.mutedText, fontSize = 11.sp, lineHeight = 13.sp)
        }
        Text(
            stringResource(Res.string.settings_selected_label),
            color = colors.primaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 10.dp).alpha(if (selected) 1f else 0f)
                .then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
        )
    }
}

/**
 * A menu of the fixed steps rather than a slider or a number field: it needs no drag precision or
 * keyboard, works with touch, keys, D-pad and screen readers, and cannot produce invalid input.
 */
@Composable
private fun FontSizePicker(
    colors: NaviampColors,
    title: String,
    value: InterfaceFontSize,
    tag: String,
    onValueChanged: (InterfaceFontSize) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = value.normalized()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding, vertical = 8.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box {
            val label = current.optionLabel()
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryText.copy(alpha = 0.10f))
                    .semantics { stateDescription = label }
                    .clickable(role = Role.DropdownList, onClickLabel = title) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("$tag-value"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, color = colors.primaryText, fontSize = 14.sp, maxLines = 1, softWrap = false)
                Icon(NaviampIcons.ChevronDown, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(16.dp))
            }
            NaviampDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                InterfaceFontSize.Steps.forEach { size ->
                    NaviampDropdownMenuItem(label = size.optionLabel(), selected = size == current) {
                        expanded = false
                        onValueChanged(size)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledFontFamilyPicker(
    colors: NaviampColors,
    selection: InterfaceFontSelection,
    interfaceSettings: InterfaceSettings,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = SettingsRowHorizontalPadding).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(Res.string.settings_font_installed_title),
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(Res.string.settings_font_installed_subtitle), color = colors.mutedText, fontSize = 12.sp)
        var query by rememberSaveable { mutableStateOf("") }
        NaviampCompactSearchField(
            value = query,
            placeholder = stringResource(Res.string.settings_font_installed_search),
            colors = colors,
            onValueChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.testTag("installed-font-search"),
        )
        // Listed once per visit so fonts installed while Naviamp runs appear on the next visit.
        val families = rememberNaviampInstalledFontFamilies()
        val matches = remember(families, query) {
            val needle = query.trim()
            families.orEmpty().filter { needle.isEmpty() || it.contains(needle, ignoreCase = true) }
        }
        if (families != null && matches.isEmpty()) {
            Text(stringResource(Res.string.settings_font_installed_empty), color = colors.mutedText, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("installed-font-list")) {
            items(matches, key = { it }) { name ->
                InstalledFontRow(colors, name, selected = name == selection.installedFamily) {
                    onInterfaceSettingsChanged(interfaceSettings.withInstalledFontFamily(name))
                }
            }
        }
    }
}

@Composable
private fun InstalledFontRow(colors: NaviampColors, name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (selected) colors.accent.copy(alpha = 0.16f) else colors.background.copy(alpha = 0f),
                RoundedCornerShape(6.dp),
            )
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = colors.primaryText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                stringResource(Res.string.settings_selected_label),
                color = colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun InterfaceFontFamily.title(): String = when (this) {
    // Typeface names are proper names and stay untranslated.
    InterfaceFontFamily.NunitoSans -> "Nunito Sans"
    InterfaceFontFamily.Inter -> "Inter"
    InterfaceFontFamily.System -> stringResource(Res.string.settings_font_system)
}

@Composable
internal fun InterfaceFontFamily.subtitle(): String = when (this) {
    InterfaceFontFamily.NunitoSans -> stringResource(Res.string.settings_font_nunito_sans_subtitle)
    InterfaceFontFamily.Inter -> stringResource(Res.string.settings_font_inter_subtitle)
    InterfaceFontFamily.System -> stringResource(Res.string.settings_font_system_subtitle)
}

/** The font the Settings summary names: an installed family, or the bundled choice in effect. */
@Composable
internal fun InterfaceSettings.fontSummary(): String {
    val selection = rememberNaviampFontSelection(this)
    return selection.installedFamily ?: selection.family.title()
}

/** A size option; the standard size is marked so it doubles as the reset. */
@Composable
internal fun InterfaceFontSize.optionLabel(): String {
    val percent = normalized().percent
    return if (percent == InterfaceFontSize.Standard.percent) {
        stringResource(Res.string.settings_font_size_default_percent, percent)
    } else {
        stringResource(Res.string.tv_percentage_value, percent)
    }
}
