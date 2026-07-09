package app.naviamp.ui

import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_category_about_subtitle
import app.naviamp.ui.generated.resources.settings_category_about_title
import app.naviamp.ui.generated.resources.settings_category_audio_cache_subtitle
import app.naviamp.ui.generated.resources.settings_category_audio_cache_title
import app.naviamp.ui.generated.resources.settings_category_debugging_subtitle
import app.naviamp.ui.generated.resources.settings_category_debugging_title
import app.naviamp.ui.generated.resources.settings_category_downloads_subtitle
import app.naviamp.ui.generated.resources.settings_category_downloads_title
import app.naviamp.ui.generated.resources.settings_category_experience_subtitle
import app.naviamp.ui.generated.resources.settings_category_experience_title
import app.naviamp.ui.generated.resources.settings_category_language_subtitle
import app.naviamp.ui.generated.resources.settings_category_language_title
import app.naviamp.ui.generated.resources.settings_category_playback_subtitle
import app.naviamp.ui.generated.resources.settings_category_playback_title
import app.naviamp.ui.generated.resources.settings_category_source_subtitle
import app.naviamp.ui.generated.resources.settings_category_source_title
import app.naviamp.ui.generated.resources.settings_language_english_subtitle
import app.naviamp.ui.generated.resources.settings_language_english_title
import app.naviamp.ui.generated.resources.settings_language_spanish_subtitle
import app.naviamp.ui.generated.resources.settings_language_spanish_title
import app.naviamp.ui.generated.resources.settings_language_system_subtitle
import app.naviamp.ui.generated.resources.settings_language_system_title
import app.naviamp.ui.generated.resources.settings_language_subtitle
import app.naviamp.ui.generated.resources.settings_language_title
import app.naviamp.ui.generated.resources.settings_selected_label
import app.naviamp.ui.generated.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable

data class NaviampLanguagePack(
    val settingsTitle: StringResource,
    val settingsLanguageTitle: StringResource,
    val settingsLanguageSubtitle: StringResource,
    val settingsLanguageSystemTitle: StringResource,
    val settingsLanguageSystemSubtitle: StringResource,
    val settingsLanguageEnglishTitle: StringResource,
    val settingsLanguageEnglishSubtitle: StringResource,
    val settingsLanguageSpanishTitle: StringResource,
    val settingsLanguageSpanishSubtitle: StringResource,
    val selectedLabel: StringResource,
    val categories: Map<NaviampSettingsCategory, NaviampSettingsCategoryText>,
)

data class NaviampSettingsCategoryText(
    val label: StringResource,
    val subtitle: StringResource,
)

fun naviampLanguagePack(language: InterfaceLanguage): NaviampLanguagePack =
    NaviampResourceLanguagePack

@Composable
fun NaviampLanguagePack.categoryLabel(category: NaviampSettingsCategory): String =
    categories[category]?.label?.let { stringResource(it) } ?: category.defaultLabel

@Composable
fun NaviampLanguagePack.categorySubtitle(category: NaviampSettingsCategory): String =
    categories[category]?.subtitle?.let { stringResource(it) } ?: category.defaultSubtitle

@Composable
fun NaviampLanguagePack.languageTitle(language: InterfaceLanguage): String =
    stringResource(when (language) {
        InterfaceLanguage.System -> settingsLanguageSystemTitle
        InterfaceLanguage.English -> settingsLanguageEnglishTitle
        InterfaceLanguage.Spanish -> settingsLanguageSpanishTitle
    })

@Composable
fun NaviampLanguagePack.languageSubtitle(language: InterfaceLanguage): String =
    stringResource(when (language) {
        InterfaceLanguage.System -> settingsLanguageSystemSubtitle
        InterfaceLanguage.English -> settingsLanguageEnglishSubtitle
        InterfaceLanguage.Spanish -> settingsLanguageSpanishSubtitle
    })

@Composable
fun NaviampLanguagePack.settingsTitle(): String = stringResource(settingsTitle)

@Composable
fun NaviampLanguagePack.selectedLabel(): String = stringResource(selectedLabel)

private val NaviampResourceLanguagePack = NaviampLanguagePack(
    settingsTitle = Res.string.settings_title,
    settingsLanguageTitle = Res.string.settings_language_title,
    settingsLanguageSubtitle = Res.string.settings_language_subtitle,
    settingsLanguageSystemTitle = Res.string.settings_language_system_title,
    settingsLanguageSystemSubtitle = Res.string.settings_language_system_subtitle,
    settingsLanguageEnglishTitle = Res.string.settings_language_english_title,
    settingsLanguageEnglishSubtitle = Res.string.settings_language_english_subtitle,
    settingsLanguageSpanishTitle = Res.string.settings_language_spanish_title,
    settingsLanguageSpanishSubtitle = Res.string.settings_language_spanish_subtitle,
    selectedLabel = Res.string.settings_selected_label,
    categories = mapOf(
        NaviampSettingsCategory.Source to NaviampSettingsCategoryText(
            Res.string.settings_category_source_title,
            Res.string.settings_category_source_subtitle,
        ),
        NaviampSettingsCategory.Language to NaviampSettingsCategoryText(
            Res.string.settings_category_language_title,
            Res.string.settings_category_language_subtitle,
        ),
        NaviampSettingsCategory.Experience to NaviampSettingsCategoryText(
            Res.string.settings_category_experience_title,
            Res.string.settings_category_experience_subtitle,
        ),
        NaviampSettingsCategory.Playback to NaviampSettingsCategoryText(
            Res.string.settings_category_playback_title,
            Res.string.settings_category_playback_subtitle,
        ),
        NaviampSettingsCategory.Downloads to NaviampSettingsCategoryText(
            Res.string.settings_category_downloads_title,
            Res.string.settings_category_downloads_subtitle,
        ),
        NaviampSettingsCategory.AudioCache to NaviampSettingsCategoryText(
            Res.string.settings_category_audio_cache_title,
            Res.string.settings_category_audio_cache_subtitle,
        ),
        NaviampSettingsCategory.Debugging to NaviampSettingsCategoryText(
            Res.string.settings_category_debugging_title,
            Res.string.settings_category_debugging_subtitle,
        ),
        NaviampSettingsCategory.About to NaviampSettingsCategoryText(
            Res.string.settings_category_about_title,
            Res.string.settings_category_about_subtitle,
        ),
    ),
)
