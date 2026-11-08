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
import app.naviamp.ui.generated.resources.settings_language_arabic_title
import app.naviamp.ui.generated.resources.settings_language_chinese_simplified_title
import app.naviamp.ui.generated.resources.settings_language_dutch_title
import app.naviamp.ui.generated.resources.settings_language_french_title
import app.naviamp.ui.generated.resources.settings_language_german_title
import app.naviamp.ui.generated.resources.settings_language_hindi_title
import app.naviamp.ui.generated.resources.settings_language_indonesian_title
import app.naviamp.ui.generated.resources.settings_language_italian_title
import app.naviamp.ui.generated.resources.settings_language_japanese_title
import app.naviamp.ui.generated.resources.settings_language_korean_title
import app.naviamp.ui.generated.resources.settings_language_polish_title
import app.naviamp.ui.generated.resources.settings_language_portuguese_brazil_title
import app.naviamp.ui.generated.resources.settings_language_russian_title
import app.naviamp.ui.generated.resources.settings_language_turkish_title
import app.naviamp.ui.generated.resources.settings_language_ukrainian_title
import app.naviamp.ui.generated.resources.settings_language_use_subtitle
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
        else -> AdditionalLanguageTitles.getValue(language)
    })

@Composable
fun NaviampLanguagePack.languageSubtitle(language: InterfaceLanguage): String =
    stringResource(when (language) {
        InterfaceLanguage.System -> settingsLanguageSystemSubtitle
        InterfaceLanguage.English -> settingsLanguageEnglishSubtitle
        InterfaceLanguage.Spanish -> settingsLanguageSpanishSubtitle
        else -> Res.string.settings_language_use_subtitle
    })

@Composable
fun NaviampLanguagePack.settingsTitle(): String = stringResource(settingsTitle)

@Composable
fun NaviampLanguagePack.selectedLabel(): String = stringResource(selectedLabel)

// Native names remain recognizable when the current interface language is unfamiliar.
private val AdditionalLanguageTitles = mapOf(
    InterfaceLanguage.Arabic to Res.string.settings_language_arabic_title,
    InterfaceLanguage.ChineseSimplified to Res.string.settings_language_chinese_simplified_title,
    InterfaceLanguage.Dutch to Res.string.settings_language_dutch_title,
    InterfaceLanguage.French to Res.string.settings_language_french_title,
    InterfaceLanguage.German to Res.string.settings_language_german_title,
    InterfaceLanguage.Hindi to Res.string.settings_language_hindi_title,
    InterfaceLanguage.Indonesian to Res.string.settings_language_indonesian_title,
    InterfaceLanguage.Italian to Res.string.settings_language_italian_title,
    InterfaceLanguage.Japanese to Res.string.settings_language_japanese_title,
    InterfaceLanguage.Korean to Res.string.settings_language_korean_title,
    InterfaceLanguage.Polish to Res.string.settings_language_polish_title,
    InterfaceLanguage.PortugueseBrazil to Res.string.settings_language_portuguese_brazil_title,
    InterfaceLanguage.Russian to Res.string.settings_language_russian_title,
    InterfaceLanguage.Turkish to Res.string.settings_language_turkish_title,
    InterfaceLanguage.Ukrainian to Res.string.settings_language_ukrainian_title,
)

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
