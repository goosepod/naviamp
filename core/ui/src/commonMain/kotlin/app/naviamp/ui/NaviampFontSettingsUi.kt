package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** Rendered in Nunito Sans so the font can always be changed back, whatever is selected. */
@Composable
internal fun FontFamilySettings(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
) = NaviampDefaultTypographyScope {
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
        NaviampFontPreview(colors, selection)
        val installedActive = selection.installedFamily != null
        InterfaceFontFamily.entries.forEach { family ->
            SelectableSettingsRow(
                colors = colors,
                title = family.title(),
                subtitle = family.subtitle(),
                selected = !installedActive && selection.family == family,
            ) {
                onInterfaceSettingsChanged(interfaceSettings.withBundledFontFamily(family))
            }
        }
        if (naviampInstalledFonts() != null) {
            InstalledFontFamilyPicker(colors, selection, interfaceSettings, onInterfaceSettingsChanged)
        }
    }
}

@Composable
private fun NaviampFontPreview(colors: NaviampColors, selection: InterfaceFontSelection) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = rememberNaviampTypography(selection)) {
        Column(
            Modifier.padding(horizontal = SettingsRowHorizontalPadding, vertical = 6.dp).fillMaxWidth()
                .background(colors.primaryText.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("font-preview"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(Res.string.settings_font_preview_title), color = colors.mutedText, fontSize = 11.sp)
            Text(
                stringResource(Res.string.settings_font_preview_sample),
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("font-preview-sample"),
            )
            Text("1:07 / 4:38  ·  0123456789", color = colors.secondaryText, fontSize = 13.sp)
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
        val families = rememberNaviampInstalledFontFamilies().orEmpty()
        val matches = remember(families, query) {
            val needle = query.trim()
            if (needle.isEmpty()) families else families.filter { it.contains(needle, ignoreCase = true) }
        }
        if (matches.isEmpty()) {
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
            )
        }
    }
}

@Composable
internal fun FontSizeSettings(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
) {
    FontSizeControl(
        colors = colors,
        title = stringResource(Res.string.settings_font_size_general_title),
        value = interfaceSettings.generalFontSize,
        tag = "general-font-size",
        onValueChanged = { onInterfaceSettingsChanged(interfaceSettings.copy(generalFontSize = it).normalized()) },
    ) {
        // The whole page already follows the general size, so the sample needs no extra scope.
        Text(stringResource(Res.string.settings_font_preview_sample), color = colors.secondaryText, fontSize = 14.sp)
    }
    FontSizeControl(
        colors = colors,
        title = stringResource(Res.string.settings_font_size_now_playing_title),
        value = interfaceSettings.nowPlayingFontSize,
        tag = "now-playing-font-size",
        onValueChanged = { onInterfaceSettingsChanged(interfaceSettings.copy(nowPlayingFontSize = it).normalized()) },
    ) {
        NaviampFontSizeScope(interfaceSettings.nowPlayingFontSize, interfaceSettings.generalFontSize) {
            Column {
                Text(
                    stringResource(Res.string.settings_font_preview_sample),
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("1:07 / 4:38", color = colors.secondaryText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FontSizeControl(
    colors: NaviampColors,
    title: String,
    value: InterfaceFontSize,
    tag: String,
    onValueChanged: (InterfaceFontSize) -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding, vertical = 8.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetentIntSettingsSlider(
            colors = colors,
            title = title,
            value = value.normalized().percent,
            detents = InterfaceFontSize.Steps.map { it.percent },
            valueLabel = { stringResource(Res.string.tv_percentage_value, it) },
            onValueChanged = { onValueChanged(InterfaceFontSize(it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).testTag("$tag-preview")) { preview() }
            TextButton(
                onClick = { onValueChanged(InterfaceFontSize.Standard) },
                enabled = value.normalized() != InterfaceFontSize.Standard,
                modifier = Modifier.testTag("$tag-reset"),
            ) {
                Text(stringResource(Res.string.settings_font_size_reset), fontSize = 13.sp)
            }
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

@Composable
internal fun InterfaceFontSize.label(): String = stringResource(Res.string.tv_percentage_value, normalized().percent)
